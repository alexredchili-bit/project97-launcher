use crate::error::{Error, Result};
use plist::Value;
use std::io::Cursor;

const OPTIONS_KEY: &str = "JVMOptions";
const AGENT_FLAG: &str = "-javaagent:";
const AGENT_JAR_NAME: &str = "plz-agent.jar";

fn is_our_agent(option: &str) -> bool {
    option.starts_with(AGENT_FLAG) && option.ends_with(AGENT_JAR_NAME)
}

fn parse(bytes: &[u8]) -> Result<Value> {
    Value::from_reader(Cursor::new(bytes))
        .map_err(|e| Error::Other(format!("Info.plist could not be read ({e}). Verify the game files in Steam.")))
}

fn options_mut(value: &mut Value) -> Result<&mut Vec<Value>> {
    value
        .as_dictionary_mut()
        .and_then(|dict| dict.get_mut(OPTIONS_KEY))
        .and_then(Value::as_array_mut)
        .ok_or_else(|| Error::Other("Info.plist has no JVMOptions list. Verify the game files in Steam.".into()))
}

fn to_xml(value: &Value) -> Result<Vec<u8>> {
    let mut out = Vec::new();
    value
        .to_writer_xml(&mut out)
        .map_err(|e| Error::Other(format!("Info.plist could not be written ({e})")))?;
    out.push(b'\n');
    Ok(out)
}

/// The plist without any agent line of ours, byte-identical to the input when there was none,
/// so a clean file banks with the sha256 Steam shipped.
pub fn without_agent(bytes: &[u8]) -> Result<Vec<u8>> {
    let mut value = parse(bytes)?;
    let options = options_mut(&mut value)?;
    let before = options.len();
    options.retain(|o| !o.as_string().is_some_and(is_our_agent));
    if options.len() == before {
        return Ok(bytes.to_vec());
    }
    to_xml(&value)
}

/// The JVM reads agents in option order, so ours goes first, ahead of anything a player added.
pub fn with_agent(bytes: &[u8], jar_path: &str) -> Result<Vec<u8>> {
    let mut value = parse(bytes)?;
    let options = options_mut(&mut value)?;
    options.retain(|o| !o.as_string().is_some_and(is_our_agent));
    options.insert(0, Value::String(format!("{AGENT_FLAG}{jar_path}")));
    to_xml(&value)
}

#[cfg(test)]
mod tests {
    use super::*;

    const STEAM_PLIST: &str = include_str!("../tests/fixtures/mac-Info.plist");

    const JAR: &str = "/Users/p/Library/Application Support/Steam/steamapps/common/ProjectZomboid/Project Zomboid.app/Contents/Java/agent/plz-agent.jar";

    fn options(bytes: &[u8]) -> Vec<String> {
        let mut value = parse(bytes).unwrap();
        options_mut(&mut value)
            .unwrap()
            .iter()
            .map(|v| v.as_string().unwrap().to_string())
            .collect()
    }

    #[test]
    fn a_clean_plist_is_banked_byte_for_byte() {
        assert_eq!(without_agent(STEAM_PLIST.as_bytes()).unwrap(), STEAM_PLIST.as_bytes());
    }

    #[test]
    fn the_agent_goes_first_and_the_rest_is_kept() {
        let patched = with_agent(STEAM_PLIST.as_bytes(), JAR).unwrap();
        let got = options(&patched);
        assert_eq!(got[0], format!("-javaagent:{JAR}"));
        assert_eq!(&got[1..], options(STEAM_PLIST.as_bytes()).as_slice());
        assert!(got.iter().any(|o| o == "-XstartOnFirstThread"));
        let text = String::from_utf8(patched).unwrap();
        assert!(text.contains("<key>JVMMainClassName</key>"));
        assert!(text.contains("$APP_ROOT/Contents/Java"));
    }

    #[test]
    fn applying_twice_leaves_one_agent_line() {
        let once = with_agent(STEAM_PLIST.as_bytes(), JAR).unwrap();
        let twice = with_agent(&once, JAR).unwrap();
        assert_eq!(options(&twice).iter().filter(|o| o.starts_with("-javaagent:")).count(), 1);
    }

    #[test]
    fn a_hand_added_line_from_the_guide_is_stripped_and_others_survive() {
        let manual = STEAM_PLIST.replace(
            "<array>\n\t\t<string>-Djava.awt.headless=true</string>",
            "<array>\n\t\t<string>-javaagent:/old/place/plz-agent.jar</string>\n\t\t<string>-javaagent:/mods/storm.jar</string>\n\t\t<string>-Djava.awt.headless=true</string>",
        );
        let cleaned = options(&without_agent(manual.as_bytes()).unwrap());
        let mut expected = vec!["-javaagent:/mods/storm.jar".to_string()];
        expected.extend(options(STEAM_PLIST.as_bytes()));
        assert_eq!(cleaned, expected);
    }

    #[test]
    fn a_plist_without_jvm_options_is_refused() {
        let broken = STEAM_PLIST.replace("<key>JVMOptions</key>", "<key>Other</key>");
        assert!(with_agent(broken.as_bytes(), JAR).is_err());
    }
}
