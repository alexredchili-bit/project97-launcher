use crate::bootstrap;
use crate::config;
use crate::error::{Error, Result};
use crate::install;
use crate::jvmpath;
use crate::launch;
use crate::payload::AgentInfo;
use crate::session_log;
use crate::state::{self, ActivePatch, State};
use crate::workshop_override;
use serde_json::Value;
use std::fs;
use std::path::Path;

const PROP_STAMP: &str = "-Dplz.stampFile=";
const PROP_BUILD: &str = "-Dplz.build=";
const PROP_WORKSHOP_STATE: &str = "-Dplz.workshopStateFile=";
const PROP_WORKSHOP_SESSION: &str = "-Dplz.workshopSession=";
const AGENT_FLAG: &str = "-javaagent:";
const NATIVE_FLAG: &str = "-agentpath:";
const AGENT_JAR_NAME: &str = "plz-agent.jar";
const NATIVE_NAME: &str = "plz-native.dll";

fn is_our_agent_arg(arg: &str) -> bool {
    (arg.starts_with(AGENT_FLAG) && arg.ends_with(AGENT_JAR_NAME))
        || (arg.starts_with(NATIVE_FLAG) && arg.ends_with(NATIVE_NAME))
}

fn payload_path_in(base: &Path, rel: &str) -> std::path::PathBuf {
    rel.split('/').fold(base.to_path_buf(), |dir, part| dir.join(part))
}

fn payload_path(build: u64, rel: &str) -> std::path::PathBuf {
    payload_path_in(&config::patch_dir(build), rel)
}

/// The JVM loads agents in command-line order, and the native helper has to be in memory
/// before the Java agent asks for instrument.dll.
fn agent_args(build: u64, agent: Option<&AgentInfo>) -> Result<Vec<String>> {
    let Some(agent) = agent else {
        return Ok(Vec::new());
    };
    let mut args = Vec::new();
    if cfg!(windows) {
        if let Some(native) = agent.native.as_deref() {
            let path = payload_path(build, native);
            if !path.is_file() {
                return Err(Error::Other(format!("the patch is missing {native}; sync again")));
            }
            args.push(format!("{NATIVE_FLAG}{}", jvmpath::for_jvm(&path)?));
        }
    }
    let jar = payload_path(build, &agent.jar);
    if !jar.is_file() {
        return Err(Error::Other(format!("the patch is missing {}; sync again", agent.jar)));
    }
    args.push(format!("{AGENT_FLAG}{}", jvmpath::for_jvm(&jar)?));
    Ok(args)
}

/// Steam's shipped default across the client, dedicated server and Linux copies alike.
pub const DEFAULT_HEAP_MB: u32 = 3072;

/// True when the install a patch was applied to is not there to restore into. Both halves
/// matter: a missing file inside a folder that exists is a file to put back, while a missing
/// folder is an install that moved, was uninstalled, or lives on a drive that is not mounted.
fn install_is_unreachable(json_path: &Path) -> bool {
    !json_path.is_file() && !json_path.parent().is_some_and(Path::is_dir)
}

#[cfg_attr(not(target_os = "macos"), allow(dead_code))]
struct BankedPlist {
    backup_path: std::path::PathBuf,
    original_sha256: String,
    patched: Vec<u8>,
}

#[cfg_attr(not(target_os = "macos"), allow(dead_code))]
fn bank_plist_with_agent(plist_path: &Path, backup_dir: &Path, jar: &Path) -> Result<BankedPlist> {
    let shipped = fs::read(plist_path)?;
    // A plist still carrying an agent line (a crashed session, or the manual guide) is not an original.
    let original = crate::macplist::without_agent(&shipped)?;
    let original_sha256 = hex::encode(<sha2::Sha256 as sha2::Digest>::digest(&original));
    let backup_path = backup_dir.join("Info.plist");
    fs::create_dir_all(backup_dir)?;
    fs::write(&backup_path, &original)?;
    if install::sha256_file(&backup_path)? != original_sha256 {
        return Err(Error::Other("backup verification failed".into()));
    }
    let patched = crate::macplist::with_agent(&original, &jar.to_string_lossy())?;
    Ok(BankedPlist { backup_path, original_sha256, patched })
}

fn file_label(path: &Path) -> String {
    path.file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_else(|| path.display().to_string())
}

pub fn repair(st: &mut State) -> Result<bool> {
    if launch::is_game_running()
        && (st.active_patch.is_some() || config::workshop_override_active_path().is_file())
    {
        return Err(Error::GameAlreadyRunning);
    }

    if let Err(e) = bootstrap::disable_in_default_mods() {
        session_log::log("restore", &format!("could not disable the launcher mod: {e}"));
    }

    let Some(active) = st.active_patch.clone() else {
        return workshop_override::clear_active();
    };

    if !active.installed_files.is_empty() {
        if let Some(dir) = st.install_dir.clone() {
            remove_installed(&dir, &active.installed_files)?;
        }
    }

    if let (Some(json_path), Some(backup_path), Some(original)) = (
        active.json_path.as_deref(),
        active.backup_path.as_deref(),
        active.original_sha256.as_deref(),
    ) {
        if install_is_unreachable(json_path) {
            // The install this patch was applied to is gone -- uninstalled, moved, or on a
            // drive that is not mounted. There is nothing left to put back, and refusing here
            // every time would keep the record forever: the memory setting, the install folder
            // and every launcher update are gated on it, with nothing in the UI the player
            // could use to clear it. apply() strips any leftovers it finds, so a drive that
            // comes back later still ends up with a clean file.
        } else if backup_path.is_file() {
            fs::copy(backup_path, json_path)?;
            let actual = install::sha256_file(json_path)?;
            if actual != original {
                return Err(Error::Other(format!(
                    "restored {} does not match the recorded original \
                     (expected {}, got {}). Verify the game files in Steam.",
                    file_label(json_path),
                    &original[..12],
                    &actual[..12]
                )));
            }
        } else if !json_path.is_file() {
            return Err(Error::Other(format!(
                "{} and its backup are both missing. Verify the game files in Steam.",
                file_label(json_path)
            )));
        }
    }

    workshop_override::clear_active()?;
    st.active_patch = None;
    st.save()?;
    Ok(true)
}

#[cfg(target_os = "macos")]
fn install_payload(install_dir: &Path, build: u64) -> Result<Vec<String>> {
    let source = config::patch_dir(build);
    let target = install::game_dir(install_dir);
    if !target.is_dir() {
        return Err(Error::Other(format!(
            "{} is missing, so this does not look like a Project Zomboid install.",
            target.display()
        )));
    }

    let mut written = Vec::new();
    for rel in collect_relative(&source, &source)? {
        let from = source.join(&rel);
        let to = target.join(&rel);
        if to.exists() {
            remove_installed(install_dir, &written)?;
            return Err(Error::Other(format!(
                "{} already exists in the game install. Verify the game files in Steam, then \
                 try again.",
                to.display()
            )));
        }
        if let Some(parent) = to.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::copy(&from, &to)?;
        written.push(rel);
    }
    Ok(written)
}

#[cfg(target_os = "macos")]
fn collect_relative(dir: &Path, base: &Path) -> Result<Vec<String>> {
    let mut out = Vec::new();
    for entry in fs::read_dir(dir)? {
        let path = entry?.path();
        if path.is_dir() {
            out.extend(collect_relative(&path, base)?);
        } else if let Ok(rel) = path.strip_prefix(base) {
            out.push(rel.to_string_lossy().replace('\\', "/"));
        }
    }
    out.sort();
    Ok(out)
}

#[cfg(target_os = "macos")]
fn remove_installed(install_dir: &Path, files: &[String]) -> Result<()> {
    let target = install::game_dir(install_dir);
    let mut dirs: Vec<std::path::PathBuf> = Vec::new();
    for rel in files {
        let path = target.join(rel);
        match fs::remove_file(&path) {
            Ok(()) => {}
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
            Err(e) => return Err(Error::Io(format!("could not remove {}: {e}", path.display()))),
        }
        let mut parent = path.parent().map(Path::to_path_buf);
        while let Some(dir) = parent {
            if dir == target || !dir.starts_with(&target) {
                break;
            }
            parent = dir.parent().map(Path::to_path_buf);
            dirs.push(dir);
        }
    }
    dirs.sort();
    dirs.dedup();
    for dir in dirs.into_iter().rev() {
        let _ = fs::remove_dir(&dir);
    }
    Ok(())
}

#[cfg(not(target_os = "macos"))]
fn remove_installed(_install_dir: &Path, _files: &[String]) -> Result<()> {
    Ok(())
}

fn strip_our_entries(json: &mut Value, patch_marker: &str) {
    if let Some(cp) = json.get_mut("classpath").and_then(Value::as_array_mut) {
        cp.retain(|v| {
            v.as_str()
                .map(|s| !s.contains(patch_marker))
                .unwrap_or(true)
        });
    }
    if let Some(vm) = json.get_mut("vmArgs").and_then(Value::as_array_mut) {
        vm.retain(|v| {
            v.as_str()
                .map(|s| {
                    !s.starts_with(PROP_STAMP)
                        && !s.starts_with(PROP_BUILD)
                        && !s.starts_with(PROP_WORKSHOP_STATE)
                        && !s.starts_with(PROP_WORKSHOP_SESSION)
                        && !is_our_agent_arg(s)
                })
                .unwrap_or(true)
        });
    }
}

pub fn apply(st: &mut State, install_dir: &Path, build: u64, agent: Option<&AgentInfo>) -> Result<()> {
    repair(st)?;

    #[cfg(target_os = "macos")]
    {
        let installed = install_payload(install_dir, build)?;
        let mut active = ActivePatch {
            json_path: None,
            backup_path: None,
            original_sha256: None,
            installed_files: installed,
            build,
            patched_at_millis: state::now_millis(),
        };
        if let Some(agent) = agent {
            let jar = payload_path_in(&install::game_dir(install_dir), &agent.jar);
            if !jar.is_file() {
                remove_installed(install_dir, &active.installed_files)?;
                return Err(Error::Other(format!("the patch is missing {}; sync again", agent.jar)));
            }
            let plist_path = install::info_plist_path(install_dir);
            let banked = match bank_plist_with_agent(&plist_path, &config::backup_dir(), &jar) {
                Ok(banked) => banked,
                Err(e) => {
                    remove_installed(install_dir, &active.installed_files)?;
                    return Err(e);
                }
            };
            active.json_path = Some(plist_path.clone());
            active.backup_path = Some(banked.backup_path);
            active.original_sha256 = Some(banked.original_sha256);
            st.active_patch = Some(active);
            st.save()?;
            fs::write(&plist_path, banked.patched)?;
        } else {
            st.active_patch = Some(active);
            st.save()?;
        }
        let _ = fs::remove_file(config::stamp_path());
        return Ok(());
    }

    #[cfg(not(target_os = "macos"))]
    {
    // Before anything is banked or activated: a path the JVM cannot read back is the end of
    // this launch, and unwinding a half-applied patch is worse than never starting one.
    let patch_str = jvmpath::for_jvm(&config::patch_dir(build))?;
    let stamp = jvmpath::for_jvm(&config::stamp_path())?;
    let agent_vm_args = agent_args(build, agent)?;
    if !config::app_dir().to_string_lossy().is_ascii() {
        session_log::log(
            "patch",
            &format!("the launcher's folder has a non-ASCII name; handing the game {patch_str}"),
        );
    }

    let json_path = install::json_path(install_dir);
    let original_bytes = fs::read(&json_path)?;
    if original_bytes.starts_with(&[0xEF, 0xBB, 0xBF]) {
        return Err(Error::Other(
            "ProjectZomboid64.json already has a UTF-8 BOM, which stops the game launching. \
             Verify the game files in Steam."
                .into(),
        ));
    }
    let mut json: Value = serde_json::from_slice(&original_bytes)?;

    // A json that still carries our entries is not an original. That happens whenever a
    // session's restore could not run, and banking it as-is would make the leftovers
    // permanent -- every restore after it would put them back. Bank the cleaned form.
    let untouched = json.clone();
    strip_our_entries(&mut json, config::PATCH_MARKER);
    strip_our_entries(&mut json, config::LEGACY_APP_DIR_NAME);
    let original_bytes = if json == untouched {
        original_bytes
    } else {
        (serde_json::to_string_pretty(&json)? + "\n").into_bytes()
    };
    let original_sha = hex::encode(<sha2::Sha256 as sha2::Digest>::digest(&original_bytes));

    let backup_path = config::backup_dir().join("ProjectZomboid64.json");
    fs::create_dir_all(config::backup_dir())?;
    fs::write(&backup_path, &original_bytes)?;
    let backup_sha = install::sha256_file(&backup_path)?;
    if backup_sha != original_sha {
        return Err(Error::Other("backup verification failed".into()));
    }

    st.active_patch = Some(ActivePatch {
        json_path: Some(json_path.clone()),
        backup_path: Some(backup_path),
        original_sha256: Some(original_sha),
        installed_files: Vec::new(),
        build,
        patched_at_millis: state::now_millis(),
    });
    st.save()?;

    let workshop_receipt = match workshop_override::activate_pending() {
        Ok(receipt) => receipt,
        Err(error) => {
            st.active_patch = None;
            st.save()?;
            return Err(error);
        }
    };

    let cp = json
        .get_mut("classpath")
        .and_then(Value::as_array_mut)
        .ok_or_else(|| Error::MalformedJson("no classpath array".into()))?;
    cp.insert(0, Value::String(patch_str));

    let vm = json
        .get_mut("vmArgs")
        .and_then(Value::as_array_mut)
        .ok_or_else(|| Error::MalformedJson("no vmArgs array".into()))?;
    for (i, arg) in agent_vm_args.into_iter().enumerate() {
        vm.insert(i, Value::String(arg));
    }
    vm.push(Value::String(format!("{PROP_STAMP}{stamp}")));
    vm.push(Value::String(format!("{PROP_BUILD}{build}")));
    if let Some(receipt) = workshop_receipt {
        let state_path = jvmpath::for_jvm(&receipt.path)?;
        let session = receipt
            .session
            .ok_or_else(|| Error::Other("active Workshop receipt has no session".into()))?;
        vm.push(Value::String(format!("{PROP_WORKSHOP_STATE}{state_path}")));
        vm.push(Value::String(format!("{PROP_WORKSHOP_SESSION}{session}")));
    }

    let text = serde_json::to_string_pretty(&json)? + "\n";
    state::write_no_bom(&json_path, &text)?;

    let _ = fs::remove_file(config::stamp_path());
    Ok(())
    }
}

fn parse_xmx_mb(arg: &str) -> Option<u32> {
    let value = arg.strip_prefix("-Xmx")?;
    let (digits, unit) = value.split_at(value.len().checked_sub(1)?);
    let n: u64 = digits.parse().ok()?;
    match unit {
        "g" | "G" => u32::try_from(n * 1024).ok(),
        "m" | "M" => u32::try_from(n).ok(),
        _ => None,
    }
}

pub fn read_heap_mb(install_dir: &Path) -> Option<u32> {
    let bytes = fs::read(install::json_path_opt(install_dir)?).ok()?;
    let json: Value = serde_json::from_slice(&bytes).ok()?;
    json.get("vmArgs")?
        .as_array()?
        .iter()
        .find_map(|v| v.as_str().and_then(parse_xmx_mb))
}

pub fn set_heap_mb(install_dir: &Path, mb: u32) -> Result<()> {
    let json_path = install::json_path_opt(install_dir).ok_or_else(|| {
        Error::Other("Project Zomboid's memory is not configurable on this platform.".into())
    })?;
    let bytes = fs::read(&json_path)?;
    if bytes.starts_with(&[0xEF, 0xBB, 0xBF]) {
        return Err(Error::Other(
            "ProjectZomboid64.json already has a UTF-8 BOM, which stops the game launching. \
             Verify the game files in Steam."
                .into(),
        ));
    }
    let mut json: Value = serde_json::from_slice(&bytes)?;
    let vm = json
        .get_mut("vmArgs")
        .and_then(Value::as_array_mut)
        .ok_or_else(|| Error::MalformedJson("no vmArgs array".into()))?;

    let entry = Value::String(format!("-Xmx{mb}m"));
    match vm
        .iter_mut()
        .find(|v| v.as_str().is_some_and(|s| s.starts_with("-Xmx")))
    {
        Some(slot) => *slot = entry,
        None => vm.push(entry),
    }

    let text = serde_json::to_string_pretty(&json)? + "\n";
    state::write_no_bom(&json_path, &text)
}

#[derive(Debug, Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Stamp {
    pub build: String,
    pub first_class: String,
    pub origin: String,
    pub classpath: String,
}

pub fn read_stamp() -> Option<Stamp> {
    let bytes = fs::read(config::stamp_path()).ok()?;
    let v: Value = serde_json::from_slice(&bytes).ok()?;
    Some(Stamp {
        build: v.get("build")?.as_str()?.to_string(),
        first_class: v.get("firstClass")?.as_str().unwrap_or("").to_string(),
        origin: v.get("origin")?.as_str().unwrap_or("").to_string(),
        classpath: v.get("classpath")?.as_str().unwrap_or("").to_string(),
    })
}

#[cfg(test)]
mod repair_tests {
    use super::*;

    const MAC_PLIST: &str = include_str!("../tests/fixtures/mac-Info.plist");

    #[test]
    fn a_mac_session_restores_the_exact_plist_steam_shipped() {
        for (tag, shipped) in [
            ("clean", MAC_PLIST.to_string()),
            (
                "manual",
                MAC_PLIST.replace(
                    "<key>JVMOptions</key>\n\t<array>\n",
                    "<key>JVMOptions</key>\n\t<array>\n\t\t<string>-javaagent:/x/plz-agent.jar</string>\n",
                ),
            ),
        ] {
            let root = std::env::temp_dir().join(format!("plz-mac-{}-{}", std::process::id(), tag));
            let _ = fs::remove_dir_all(&root);
            fs::create_dir_all(&root).unwrap();
            let plist = root.join("Info.plist");
            fs::write(&plist, &shipped).unwrap();
            let jar = root.join("Contents").join("Java").join("agent").join("plz-agent.jar");

            let banked = bank_plist_with_agent(&plist, &root.join("backup"), &jar).unwrap();
            fs::write(&plist, &banked.patched).unwrap();
            let live = String::from_utf8(fs::read(&plist).unwrap()).unwrap();
            assert_eq!(live.matches("plz-agent.jar").count(), 1, "{tag}");
            assert!(live.contains(&format!("-javaagent:{}", jar.to_string_lossy())), "{tag}");

            fs::copy(&banked.backup_path, &plist).unwrap();
            assert_eq!(install::sha256_file(&plist).unwrap(), banked.original_sha256, "{tag}");
            assert!(!fs::read_to_string(&plist).unwrap().contains("plz-agent.jar"), "{tag}");
            if tag == "clean" {
                assert_eq!(fs::read_to_string(&plist).unwrap(), MAC_PLIST);
            }
            let _ = fs::remove_dir_all(&root);
        }
    }

    #[test]
    fn an_install_that_still_has_its_folder_is_reachable() {
        let root = std::env::temp_dir().join(format!("plz-reach-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        fs::create_dir_all(&root).unwrap();

        let json = root.join("ProjectZomboid64.json");
        assert!(install_is_unreachable(&root.join("gone").join("ProjectZomboid64.json")));
        // The folder is there, so the file is one to put back rather than an install that left.
        assert!(!install_is_unreachable(&json));

        fs::write(&json, "{}").unwrap();
        assert!(!install_is_unreachable(&json));

        let _ = fs::remove_dir_all(&root);
    }

    #[test]
    fn stripping_leaves_a_json_that_apply_can_bank_as_an_original() {
        let mut json: Value = serde_json::from_str(
            r#"{
                "classpath": ["C:/x/Project97Launcher/patch/36", "C:/x/ProjectLifeZoidLauncher/patch/36", "zombie.jar"],
                "vmArgs": ["-Xmx8192m", "-Dplz.build=36", "-Dplz.stampFile=C:/x/stamp.json"]
            }"#,
        )
        .unwrap();
        let untouched = json.clone();
        strip_our_entries(&mut json, config::PATCH_MARKER);
        strip_our_entries(&mut json, config::LEGACY_APP_DIR_NAME);

        assert_ne!(json, untouched, "a patched json must not be banked as-is");
        assert_eq!(json["classpath"].as_array().unwrap().len(), 1);
        assert_eq!(json["vmArgs"].as_array().unwrap(), &vec![Value::from("-Xmx8192m")]);

        let clean = json.clone();
        strip_our_entries(&mut json, config::PATCH_MARKER);
        strip_our_entries(&mut json, config::LEGACY_APP_DIR_NAME);
        assert_eq!(json, clean, "a clean json is banked byte for byte");
    }

    #[test]
    fn stripping_drops_our_agent_args_from_either_channel_but_no_one_elses() {
        let mut json: Value = serde_json::from_str(
            r#"{
                "classpath": ["C:/x/Project97Launcher-Prerelease/patch/9001", "zombie.jar"],
                "vmArgs": [
                    "-agentpath:C:/x/PROJEC~1/patch/9001/agent/plz-native.dll",
                    "-javaagent:C:/x/Project97Launcher/patch/9001/agent/plz-agent.jar",
                    "-agentlib:zbNative=policy=allow-all",
                    "-javaagent:C:/mods/storm.jar",
                    "-Xmx8192m"
                ]
            }"#,
        )
        .unwrap();
        strip_our_entries(&mut json, config::PATCH_MARKER);
        strip_our_entries(&mut json, config::LEGACY_APP_DIR_NAME);

        assert_eq!(json["classpath"].as_array().unwrap(), &vec![Value::from("zombie.jar")]);
        assert_eq!(
            json["vmArgs"].as_array().unwrap(),
            &vec![
                Value::from("-agentlib:zbNative=policy=allow-all"),
                Value::from("-javaagent:C:/mods/storm.jar"),
                Value::from("-Xmx8192m"),
            ]
        );
    }
}

#[cfg(test)]
mod heap_tests {
    use super::*;

    #[test]
    fn parses_gigabyte_and_megabyte_suffixes() {
        assert_eq!(parse_xmx_mb("-Xmx3072m"), Some(3072));
        assert_eq!(parse_xmx_mb("-Xmx8g"), Some(8192));
        assert_eq!(parse_xmx_mb("-Xmx8G"), Some(8192));
        assert_eq!(parse_xmx_mb("-Xmx8192M"), Some(8192));
    }

    #[test]
    fn rejects_anything_that_is_not_an_xmx_flag() {
        assert_eq!(parse_xmx_mb("-Xms2048m"), None);
        assert_eq!(parse_xmx_mb("-Dzomboid.steam=1"), None);
        assert_eq!(parse_xmx_mb("-Xmx"), None);
        assert_eq!(parse_xmx_mb("-Xmxabc"), None);
    }

    fn fake_install(tag: &str, vm_args_json: &str) -> std::path::PathBuf {
        let root = std::env::temp_dir().join(format!("plz-heap-{}-{}", std::process::id(), tag));
        let _ = fs::remove_dir_all(&root);
        let json_path = install::json_path(&root);
        fs::create_dir_all(json_path.parent().unwrap()).unwrap();
        fs::write(&json_path, format!(r#"{{"vmArgs": {vm_args_json}}}"#)).unwrap();
        root
    }

    // macOS ships no ProjectZomboid64.json at all, so read/write_heap_mb refuse there by
    // design (see json_path_opt) -- these round-trip tests only apply to the platforms
    // that have a file to manage.
    #[cfg(not(target_os = "macos"))]
    #[test]
    fn set_heap_mb_replaces_the_existing_entry_in_place() {
        let root = fake_install(
            "replace",
            r#"["-Djava.awt.headless=true", "-Xmx3072m", "-Dzomboid.steam=1"]"#,
        );
        assert_eq!(read_heap_mb(&root), Some(3072));

        set_heap_mb(&root, 8192).unwrap();
        assert_eq!(read_heap_mb(&root), Some(8192));

        let text = fs::read_to_string(install::json_path(&root)).unwrap();
        assert_eq!(
            text.matches("-Xmx").count(),
            1,
            "must not accumulate old entries: {text}"
        );

        let _ = fs::remove_dir_all(&root);
    }

    #[cfg(not(target_os = "macos"))]
    #[test]
    fn set_heap_mb_adds_the_entry_when_none_exists() {
        let root = fake_install("missing", r#"["-Djava.awt.headless=true"]"#);

        set_heap_mb(&root, 6144).unwrap();
        assert_eq!(read_heap_mb(&root), Some(6144));

        let _ = fs::remove_dir_all(&root);
    }

    #[cfg(target_os = "macos")]
    #[test]
    fn macos_has_no_manageable_heap_setting() {
        let root = fake_install("macos-unsupported", r#"["-Xmx3072m"]"#);

        assert_eq!(read_heap_mb(&root), None);
        assert!(set_heap_mb(&root, 8192).is_err());

        let _ = fs::remove_dir_all(&root);
    }
}
