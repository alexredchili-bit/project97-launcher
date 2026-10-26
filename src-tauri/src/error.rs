use serde::{Serialize, Serializer};

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error(
        "Project Zomboid was not found. Detection only covers Steam's own libraries, \
         so open Details and point the launcher at your game folder."
    )]
    InstallNotFound,

    #[error(
        "{path}\n\
         is not a Project Zomboid install. In Steam, right-click Project Zomboid, \
         choose Manage > Browse local files, and pick the folder that opens."
    )]
    NotAnInstall { path: String },

    #[error("Project Zomboid is already running. Quit it fully, then try again.")]
    GameAlreadyRunning,

    #[error("Steam is not installed, or steam.exe could not be located.")]
    SteamNotFound,

    #[error(
        "This launcher's patch was built for a different version of Project Zomboid.\n\
         The game updated ({actual}) but the payload expects {expected}.\n\
         Wait for a launcher update -- running it anyway would silently revert engine fixes."
    )]
    StaleJar { expected: String, actual: String },

    #[error(
        "The server is running patch build {server}, this launcher has build {local}.\n\
         The server is probably mid-update. Try again in a few minutes."
    )]
    BuildMismatch { server: u64, local: u64 },

    #[error(
        "Steam has not downloaded the server's mod update yet: {mods}.
         Your copy is from {local}; the server is running the build published {expected}.
         The game would sit on the join screen with no explanation, so the launcher stopped here.
         Leave Project Zomboid closed, let Steam finish the Workshop download, then press Play again."
    )]
    WorkshopBehind {
        mods: String,
        local: String,
        expected: String,
    },

    #[error(
        "Steam has not finished with your Workshop mods: {mods}.\n\
         Starting now makes the game ask Steam for mod folders it has not written yet, and \
         the answer is cached for the whole session -- so anything refused stays refused. \
         An invisible character, invisible vehicles and a blank map are the usual result.\n\
         Leave Project Zomboid closed, wait for Steam's Downloads page to go quiet, then \
         press Play again. If Steam shows nothing downloading, restart Steam -- it applies \
         pending Workshop updates on startup."
    )]
    WorkshopNotReady { mods: String },

    #[error(
        "The server is running an older copy of a mod than Steam has: {mods}.\n\
         The server last loaded its Workshop mods at {since}, before that update, so it will \
         refuse every player until it restarts and downloads it.\n\
         This is not something you can fix from here. Tell an admin the server needs a \
         restart, then press Play again."
    )]
    ServerWorkshopBehind { mods: String, since: String },

    #[error("{url} returned {status}")]
    Http { url: String, status: u16 },

    #[error("Payload signature check failed. Refusing to install unverified code.")]
    BadSignature,

    #[error(
        "The update server offered patch build {offered}, but this machine has already seen\n\
         build {seen}. Updates never go backwards, so the launcher stopped rather than install\n\
         older code that a valid signature cannot prove is current.\n\
         If the real server was rolled back on purpose, the next update will carry a HIGHER\n\
         build number and this will clear itself."
    )]
    Rollback { seen: u64, offered: u64 },

    #[error("{path}: expected sha256 {expected}, got {actual}")]
    BadChecksum {
        path: String,
        expected: String,
        actual: String,
    },

    #[error(
        "The game did not start within {0}s. Your install has been restored.\n\
         A common cause is a corrupted ProjectZomboid64.json -- try verifying the game files in Steam."
    )]
    GameNeverStarted(u64),

    #[error(
        "The patch cannot be handed to the game from this folder:\n\
         {path}\n\
         Project Zomboid's own launcher passes that path on to Java in your system's ANSI \
         codepage, so the accented character in it would arrive mangled and the patch would be \
         skipped without a word. The launcher normally works around that with the plain-ASCII \
         short name Windows keeps for such folders, but this drive has short names switched off.\n\
         Please report this with your Windows username -- it needs a fix on our side, not yours."
    )]
    PathNotAscii { path: String },

    #[error("ProjectZomboid64.json is not valid JSON: {0}")]
    MalformedJson(String),

    #[error("could not reach the server ({0})")]
    Query(String),

    #[error("{0}")]
    Io(String),

    #[error("{0}")]
    Other(String),
}

impl From<std::io::Error> for Error {
    fn from(e: std::io::Error) -> Self {
        Error::Io(e.to_string())
    }
}

impl From<serde_json::Error> for Error {
    fn from(e: serde_json::Error) -> Self {
        Error::MalformedJson(e.to_string())
    }
}

impl From<reqwest::Error> for Error {
    fn from(e: reqwest::Error) -> Self {
        let mut msg = format!("network: {e}");
        let mut source = std::error::Error::source(&e);
        while let Some(cause) = source {
            let text = cause.to_string();
            if !msg.contains(&text) {
                msg.push_str(": ");
                msg.push_str(&text);
            }
            source = cause.source();
        }
        Error::Other(msg)
    }
}

impl From<tauri_plugin_updater::Error> for Error {
    fn from(e: tauri_plugin_updater::Error) -> Self {
        Error::Other(format!("launcher update: {e}"))
    }
}

impl From<rusqlite::Error> for Error {
    fn from(e: rusqlite::Error) -> Self {
        Error::Other(format!("server list db: {e}"))
    }
}

impl Serialize for Error {
    fn serialize<S: Serializer>(&self, s: S) -> std::result::Result<S::Ok, S::Error> {
        s.serialize_str(&self.to_string())
    }
}

pub type Result<T> = std::result::Result<T, Error>;

#[cfg(test)]
mod tests {
    use super::Error;

    #[test]
    fn a_network_failure_names_its_cause() {
        let port = std::net::TcpListener::bind("127.0.0.1:0")
            .unwrap()
            .local_addr()
            .unwrap()
            .port();
        let err = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap()
            .block_on(reqwest::get(format!("http://127.0.0.1:{port}/manifest.json")))
            .unwrap_err();
        let msg = Error::from(err).to_string();
        assert!(msg.starts_with("network: error sending request"), "{msg}");
        assert!(msg.contains("tcp connect error"), "{msg}");
    }
}
