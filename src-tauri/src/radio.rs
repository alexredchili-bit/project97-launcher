use crate::config;
use crate::error::{Error, Result};
use crate::films;
use crate::press;
use crate::session_log;
use base64::Engine;
use ed25519_dalek::{Signature, VerifyingKey};
use serde::Deserialize;
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, HashSet};
use std::fs;
use std::future::Future;
use std::io::Write;
use std::path::Path;
use std::sync::atomic::AtomicBool;
use std::sync::Arc;
use std::time::Duration;

/// Made by tools/radio_keys.py on the bot host; empty turns the mirror off.
pub const RADIO_PUBLIC_KEY_HEX: &str = "4dcd14b36503d038386edccaec5fae05c9e6ff73f5409eea874f0a285d094ee3";

/// Signed over this prefix plus the payload, so a radio signature can never pass as any other document's.
pub const SIGN_CONTEXT: &[u8] = b"plz-radio-index-v1\n";

const INDEX_VERSION: u32 = 1;
const MAX_INDEX_BYTES: u64 = 4 * 1024 * 1024;
const MAX_TRACKS: usize = 50_000;
const MAX_TRACK_BYTES: u64 = 16 * 1024 * 1024;
const MAX_LIBRARY_BYTES: u64 = 8 * 1024 * 1024 * 1024;
pub const REFRESH: Duration = Duration::from_secs(120);

#[derive(Deserialize)]
struct Signed {
    payload: String,
    sig: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Index {
    pub v: u32,
    pub seq: u64,
    #[serde(default)]
    pub generated: u64,
    pub tracks: Vec<IndexTrack>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct IndexTrack {
    pub sha: String,
    pub bytes: u64,
    #[serde(default = "copyrighted")]
    pub rights: String,
}

fn copyrighted() -> String {
    "copyrighted".into()
}

#[derive(Debug, Default, Clone, PartialEq)]
pub struct Report {
    pub seq: u64,
    pub downloaded: usize,
    pub removed: usize,
    pub skipped: usize,
    pub failed: Vec<String>,
}

fn is_sha(s: &str) -> bool {
    s.len() == 64 && s.bytes().all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
}

pub fn file_name(track: &IndexTrack) -> String {
    format!("plzradio_{}.ogg", track.sha)
}

/// Is a half-written .part, for a file this module owns; None for anything else in the folder.
fn own_file(name: &str) -> Option<bool> {
    let (name, partial) = match name.strip_suffix(".part") {
        Some(name) => (name, true),
        None => (name, false),
    };
    let sha = name.strip_prefix("plzradio_")?.strip_suffix(".ogg")?;
    is_sha(sha).then_some(partial)
}

fn refused(what: impl Into<String>) -> Error {
    Error::Other(format!("radio index refused: {}", what.into()))
}

pub fn verify_index(document: &[u8], key_hex: &str) -> Result<Index> {
    let key_raw: [u8; 32] = hex::decode(key_hex)
        .map_err(|_| Error::BadSignature)?
        .try_into()
        .map_err(|_| Error::BadSignature)?;
    let key = VerifyingKey::from_bytes(&key_raw).map_err(|_| Error::BadSignature)?;
    let signed: Signed = serde_json::from_slice(document).map_err(|_| Error::BadSignature)?;
    let b64 = base64::engine::general_purpose::STANDARD;
    let payload = b64.decode(signed.payload.as_bytes()).map_err(|_| Error::BadSignature)?;
    let sig: [u8; 64] = b64
        .decode(signed.sig.as_bytes())
        .map_err(|_| Error::BadSignature)?
        .try_into()
        .map_err(|_| Error::BadSignature)?;
    let mut message = SIGN_CONTEXT.to_vec();
    message.extend_from_slice(&payload);
    key.verify_strict(&message, &Signature::from_bytes(&sig))
        .map_err(|_| Error::BadSignature)?;
    let index: Index = serde_json::from_slice(&payload)?;
    check_index(&index)?;
    Ok(index)
}

fn check_index(index: &Index) -> Result<()> {
    if index.v != INDEX_VERSION {
        return Err(refused(format!("version {} is not understood", index.v)));
    }
    if index.tracks.len() > MAX_TRACKS {
        return Err(refused("too many tracks"));
    }
    let mut seen = HashSet::new();
    let mut total = 0u64;
    for track in &index.tracks {
        if !is_sha(&track.sha) || !seen.insert(track.sha.as_str()) {
            return Err(refused(format!("bad or repeated track {:?}", track.sha)));
        }
        if track.bytes == 0 || track.bytes > MAX_TRACK_BYTES {
            return Err(refused(format!("track {} has an impossible size", track.sha)));
        }
        total += track.bytes;
    }
    if total > MAX_LIBRARY_BYTES {
        return Err(refused("the library is larger than any real one"));
    }
    Ok(())
}

/// Hash, exact size, and an Ogg Vorbis identification header where the bot always writes one.
pub fn check_track(bytes: &[u8], track: &IndexTrack) -> std::result::Result<(), String> {
    if bytes.len() as u64 != track.bytes {
        return Err(format!("{} bytes, index says {}", bytes.len(), track.bytes));
    }
    if hex::encode(Sha256::digest(bytes)) != track.sha {
        return Err("hash mismatch".into());
    }
    if bytes.len() < 64 || &bytes[0..4] != b"OggS" {
        return Err("not an Ogg file".into());
    }
    let body = 27 + bytes[26] as usize;
    if bytes.len() < body + 7 || bytes[body] != 1 || &bytes[body + 1..body + 7] != b"vorbis" {
        return Err("not the Ogg Vorbis the bot writes".into());
    }
    Ok(())
}

fn land(dir: &Path, track: &IndexTrack, bytes: &[u8]) -> Result<()> {
    let part = dir.join(format!("{}.part", file_name(track)));
    {
        let mut f = fs::File::create(&part)?;
        f.write_all(bytes)?;
        f.sync_all()?;
    }
    fs::rename(&part, dir.join(file_name(track)))?;
    Ok(())
}

/// Tracks this player wants: the films copyright choice also covers uploaded songs.
pub fn wanted(index: &Index, skip_copyrighted: bool) -> Vec<IndexTrack> {
    index
        .tracks
        .iter()
        .filter(|t| !(skip_copyrighted && t.rights != "original" && t.rights != "licensed"))
        .cloned()
        .collect()
}

pub async fn mirror_into<F, Fut>(dir: &Path, base: &str, index: &Index, skip_copyrighted: bool, fetch: F) -> Report
where
    F: Fn(String, u64) -> Fut,
    Fut: Future<Output = Result<Vec<u8>>>,
{
    let mut report = Report {
        seq: index.seq,
        ..Default::default()
    };
    if let Err(e) = fs::create_dir_all(dir) {
        report.failed.push(e.to_string());
        return report;
    }
    let mut present: BTreeMap<String, u64> = BTreeMap::new();
    if let Ok(entries) = fs::read_dir(dir) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            let Some(partial) = own_file(&name) else {
                continue;
            };
            if partial {
                let _ = fs::remove_file(entry.path());
            } else {
                let size = entry.metadata().map(|m| m.len()).unwrap_or(0);
                present.insert(name, size);
            }
        }
    }
    let tracks = wanted(index, skip_copyrighted);
    report.skipped = index.tracks.len() - tracks.len();
    let base = base.trim_end_matches('/');
    for track in &tracks {
        let name = file_name(track);
        if present.get(&name) == Some(&track.bytes) {
            continue;
        }
        let short = &track.sha[..12];
        let url = format!("{base}/t/{}.ogg", track.sha);
        let bytes = match fetch(url, track.bytes).await {
            Ok(bytes) => bytes,
            Err(e) => {
                report.failed.push(format!("{short}: {e}"));
                continue;
            }
        };
        if let Err(why) = check_track(&bytes, track) {
            report.failed.push(format!("{short}: {why}"));
            continue;
        }
        match land(dir, track, &bytes) {
            Ok(()) => report.downloaded += 1,
            Err(e) => report.failed.push(format!("{short}: {e}")),
        }
    }
    let keep: HashSet<String> = tracks.iter().map(file_name).collect();
    for name in present.keys() {
        if !keep.contains(name) && fs::remove_file(dir.join(name)).is_ok() {
            report.removed += 1;
        }
    }
    report
}

fn radio_url() -> String {
    #[cfg(debug_assertions)]
    {
        if let Ok(url) = std::env::var(config::RADIO_URL_ENV) {
            return url;
        }
    }
    config::DEFAULT_RADIO_URL.to_string()
}

pub async fn sync_from(base: &str, key_hex: &str, dir: &Path, guard: &Path, skip_copyrighted: bool) -> Result<Report> {
    let stamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let document = press::fetch_capped(format!("{base}/index.json?t={stamp}"), MAX_INDEX_BYTES).await?;
    let index = verify_index(&document, key_hex)?;
    let seen = press::read_seq(guard);
    if !press::accept_seq(seen, index.seq) {
        return Err(refused(format!("index {} is older than {seen}, already seen", index.seq)));
    }
    if index.seq > seen {
        press::write_seq(guard, index.seq)?;
    }
    Ok(mirror_into(dir, base, &index, skip_copyrighted, press::fetch_capped).await)
}

pub fn write_marker(path: &Path, launcher_version: &str) -> Result<()> {
    let bytes = serde_json::to_vec(&serde_json::json!({ "radio": 1, "launcher": launcher_version }))?;
    if fs::read(path).ok().as_deref() == Some(bytes.as_slice()) {
        return Ok(());
    }
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, bytes)?;
    Ok(())
}

pub async fn sync() -> Result<Option<Report>> {
    if RADIO_PUBLIC_KEY_HEX.is_empty() {
        return Ok(None);
    }
    if let Err(e) = write_marker(&config::radio_marker_path(), env!("CARGO_PKG_VERSION")) {
        session_log::log("radio", &format!("launcher marker not written: {e}"));
    }
    let skip = films::read_choices().skip_copyrighted.unwrap_or(false);
    sync_from(
        &radio_url(),
        RADIO_PUBLIC_KEY_HEX,
        &config::radio_tracks_dir(),
        &config::radio_seq_guard_path(),
        skip,
    )
    .await
    .map(Some)
}

pub fn log_outcome(outcome: &Result<Option<Report>>) {
    match outcome {
        Ok(None) => {}
        Ok(Some(r)) => session_log::log(
            "radio",
            &format!(
                "index {}: {} downloaded, {} removed, {} skipped, {} failed{}",
                r.seq,
                r.downloaded,
                r.removed,
                r.skipped,
                r.failed.len(),
                r.failed.first().map(|f| format!(" (first: {f})")).unwrap_or_default()
            ),
        ),
        Err(e) => session_log::log("radio", &format!("songs not updated: {e}")),
    }
}

pub fn refresh_until(stop: Arc<AtomicBool>) {
    press::run_every("radio-refresh", stop, REFRESH, REFRESH, || async { log_outcome(&sync().await) });
}

#[cfg(test)]
mod tests {
    use super::*;
    use ed25519_dalek::{Signer, SigningKey};
    use std::collections::HashMap;

    fn key() -> SigningKey {
        SigningKey::from_bytes(&[11u8; 32])
    }

    fn key_hex() -> String {
        hex::encode(key().verifying_key().to_bytes())
    }

    fn ogg(fill: u8) -> Vec<u8> {
        let mut out = b"OggS".to_vec();
        out.extend_from_slice(&[0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 30]);
        out.push(1);
        out.extend_from_slice(b"vorbis");
        out.extend(std::iter::repeat(fill).take(200));
        out
    }

    fn sha(bytes: &[u8]) -> String {
        hex::encode(Sha256::digest(bytes))
    }

    fn signed(payload: &serde_json::Value, with: &SigningKey, context: &[u8]) -> Vec<u8> {
        let payload = serde_json::to_vec(payload).unwrap();
        let mut message = context.to_vec();
        message.extend_from_slice(&payload);
        let b64 = base64::engine::general_purpose::STANDARD;
        serde_json::to_vec(&serde_json::json!({
            "payload": b64.encode(&payload),
            "sig": b64.encode(with.sign(&message).to_bytes()),
        }))
        .unwrap()
    }

    fn index_for(tracks: &[(&[u8], &str)], seq: u64) -> serde_json::Value {
        let entries: Vec<_> = tracks
            .iter()
            .map(|(b, rights)| serde_json::json!({"sha": sha(b), "bytes": b.len(), "rights": rights}))
            .collect();
        serde_json::json!({"v": 1, "seq": seq, "generated": 1, "tracks": entries})
    }

    fn parsed(tracks: &[(&[u8], &str)], seq: u64) -> Index {
        verify_index(&signed(&index_for(tracks, seq), &key(), SIGN_CONTEXT), &key_hex()).unwrap()
    }

    fn fetcher(files: HashMap<String, Vec<u8>>) -> impl Fn(String, u64) -> std::future::Ready<Result<Vec<u8>>> {
        move |url: String, limit: u64| {
            std::future::ready(match files.get(&url) {
                Some(b) if b.len() as u64 <= limit => Ok(b.clone()),
                Some(_) => Err(Error::Other("too large".into())),
                None => Err(Error::Http { url, status: 404 }),
            })
        }
    }

    fn scratch(name: &str) -> std::path::PathBuf {
        let dir = std::env::temp_dir().join(format!("plz-radio-test-{name}-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    fn run<F: Future>(f: F) -> F::Output {
        tokio::runtime::Builder::new_current_thread().build().unwrap().block_on(f)
    }

    #[test]
    fn a_signed_index_verifies_only_under_the_radio_context() {
        let song = ogg(1);
        assert_eq!(parsed(&[(&song, "original")], 3).tracks.len(), 1);
        let bare = signed(&index_for(&[(&song, "original")], 3), &key(), b"");
        assert!(verify_index(&bare, &key_hex()).is_err());
        let films = signed(&index_for(&[(&song, "original")], 3), &key(), b"plz-films-index-v1\n");
        assert!(verify_index(&films, &key_hex()).is_err());
        let other = SigningKey::from_bytes(&[9u8; 32]);
        assert!(verify_index(&signed(&index_for(&[(&song, "original")], 3), &other, SIGN_CONTEXT), &key_hex()).is_err());
    }

    #[test]
    fn impossible_tracks_are_refused_even_when_signed() {
        for bad in [
            serde_json::json!({"sha": "A".repeat(64), "bytes": 10}),
            serde_json::json!({"sha": "../../evil", "bytes": 10}),
            serde_json::json!({"sha": "a".repeat(64), "bytes": 0}),
            serde_json::json!({"sha": "a".repeat(64), "bytes": 99_000_000}),
        ] {
            let doc = signed(&serde_json::json!({"v": 1, "seq": 1, "tracks": [bad]}), &key(), SIGN_CONTEXT);
            assert!(verify_index(&doc, &key_hex()).is_err());
        }
        let twice = serde_json::json!({"sha": "a".repeat(64), "bytes": 10});
        let doc = signed(&serde_json::json!({"v": 1, "seq": 1, "tracks": [twice.clone(), twice]}), &key(), SIGN_CONTEXT);
        assert!(verify_index(&doc, &key_hex()).is_err());
    }

    #[test]
    fn a_track_must_be_the_ogg_vorbis_the_index_names() {
        let song = ogg(2);
        let index = parsed(&[(&song, "original")], 1);
        assert!(check_track(&song, &index.tracks[0]).is_ok());
        let mut wav = song.clone();
        wav[0..4].copy_from_slice(b"RIFF");
        let fake = IndexTrack { sha: sha(&wav), bytes: wav.len() as u64, rights: "original".into() };
        assert!(check_track(&wav, &fake).is_err());
        assert!(check_track(&ogg(3), &index.tracks[0]).is_err());
    }

    #[test]
    fn mirror_downloads_skips_copyrighted_on_request_and_purges_only_its_own() {
        let dir = scratch("mirror");
        let mine = ogg(4);
        let theirs = ogg(5);
        let stale = ogg(6);
        let stale_name = format!("plzradio_{}.ogg", sha(&stale));
        fs::write(dir.join(&stale_name), &stale).unwrap();
        fs::write(dir.join("notes.txt"), b"keep").unwrap();
        let files = HashMap::from([
            (format!("https://o/radio/t/{}.ogg", sha(&mine)), mine.clone()),
            (format!("https://o/radio/t/{}.ogg", sha(&theirs)), theirs.clone()),
        ]);
        let index = parsed(&[(&mine, "original"), (&theirs, "copyrighted")], 2);
        let report = run(mirror_into(&dir, "https://o/radio/", &index, true, fetcher(files.clone())));
        assert_eq!((report.downloaded, report.skipped, report.removed, report.failed.len()), (1, 1, 1, 0));
        assert!(dir.join(format!("plzradio_{}.ogg", sha(&mine))).exists());
        assert!(!dir.join(format!("plzradio_{}.ogg", sha(&theirs))).exists());
        assert!(!dir.join(&stale_name).exists());
        assert!(dir.join("notes.txt").exists());
        let keep = run(mirror_into(&dir, "https://o/radio", &index, false, fetcher(files)));
        assert_eq!((keep.downloaded, keep.skipped), (1, 0));
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn sync_refuses_a_replayed_older_index() {
        let dir = scratch("replay");
        let guard = dir.join("seq.json");
        press::write_seq(&guard, 10).unwrap();
        fs::write(dir.join("index.json"), signed(&index_for(&[], 9), &key(), SIGN_CONTEXT)).unwrap();
        let base = format!("file:///{}", dir.to_string_lossy().replace('\\', "/"));
        assert!(run(sync_from(&base, &key_hex(), &dir.join("tracks"), &guard, false)).is_err());
        assert_eq!(press::read_seq(&guard), 10);
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn the_marker_tells_the_game_a_radio_capable_launcher_ran() {
        let dir = scratch("marker");
        let path = dir.join("PLZRadio").join("launcher.json");
        write_marker(&path, "1.0.0").unwrap();
        let written: serde_json::Value = serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();
        assert_eq!(written, serde_json::json!({ "radio": 1, "launcher": "1.0.0" }));
        let _ = fs::remove_dir_all(&dir);
    }
}
