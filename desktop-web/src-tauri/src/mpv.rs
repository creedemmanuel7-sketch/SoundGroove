//! Contrôle minimal de mpv via IPC (JSON). Fallback soft si mpv absent.

use parking_lot::Mutex;
use serde::Serialize;
use std::io::Write;
use std::net::Shutdown;
use std::process::{Child, Command, Stdio};
use std::sync::Arc;
use std::time::Duration;

#[cfg(windows)]
fn ipc_path() -> String {
    r"\\.\pipe\soundgroove-mpv".to_string()
}

#[cfg(not(windows))]
fn ipc_path() -> String {
    let dir = std::env::temp_dir();
    dir.join("soundgroove-mpv.sock")
        .to_string_lossy()
        .to_string()
}

pub struct MpvController {
    inner: Mutex<MpvState>,
}

struct MpvState {
    child: Option<Child>,
    ipc: String,
    available: Option<bool>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MpvStatus {
    pub available: bool,
    pub running: bool,
    pub message: String,
}

impl Default for MpvController {
    fn default() -> Self {
        Self::new()
    }
}

impl MpvController {
    pub fn new() -> Self {
        Self {
            inner: Mutex::new(MpvState {
                child: None,
                ipc: ipc_path(),
                available: None,
            }),
        }
    }

    pub fn status(&self) -> MpvStatus {
        let mut state = self.inner.lock();
        let available = match state.available {
            Some(v) => v,
            None => {
                let ok = Command::new("mpv")
                    .arg("--version")
                    .stdout(Stdio::null())
                    .stderr(Stdio::null())
                    .status()
                    .map(|s| s.success())
                    .unwrap_or(false);
                state.available = Some(ok);
                ok
            }
        };
        let running = state
            .child
            .as_mut()
            .map(|c| c.try_wait().ok().flatten().is_none())
            .unwrap_or(false);
        MpvStatus {
            available,
            running,
            message: if available {
                "mpv détecté".into()
            } else {
                "mpv introuvable — lecture via lecteur HTML".into()
            },
        }
    }

    pub fn play(&self, path: &str) -> Result<MpvStatus, String> {
        let status = self.status();
        if !status.available {
            return Ok(status);
        }

        let mut state = self.inner.lock();
        if let Some(mut child) = state.child.take() {
            let _ = child.kill();
            let _ = child.wait();
        }

        #[cfg(not(windows))]
        {
            let _ = std::fs::remove_file(&state.ipc);
        }

        let child = Command::new("mpv")
            .args([
                "--force-window=no",
                "--no-terminal",
                "--idle=yes",
                &format!("--input-ipc-server={}", state.ipc),
                path,
            ])
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()
            .map_err(|e| format!("Échec lancement mpv: {e}"))?;

        state.child = Some(child);
        // Laisse le temps au socket IPC.
        drop(state);
        std::thread::sleep(Duration::from_millis(200));
        Ok(self.status())
    }

    pub fn send_command(&self, command: &[&str]) -> Result<(), String> {
        let state = self.inner.lock();
        let payload = serde_json::json!({ "command": command });
        let line = format!("{payload}\n");

        #[cfg(windows)]
        {
            use std::fs::OpenOptions;
            let mut pipe = OpenOptions::new()
                .write(true)
                .open(&state.ipc)
                .map_err(|e| format!("IPC mpv (pipe): {e}"))?;
            pipe.write_all(line.as_bytes())
                .map_err(|e| format!("IPC write: {e}"))?;
        }

        #[cfg(not(windows))]
        {
            use std::os::unix::net::UnixStream;
            let mut stream = UnixStream::connect(&state.ipc)
                .map_err(|e| format!("IPC mpv (socket): {e}"))?;
            stream
                .write_all(line.as_bytes())
                .map_err(|e| format!("IPC write: {e}"))?;
            let _ = stream.shutdown(Shutdown::Both);
        }

        Ok(())
    }

    pub fn pause_toggle(&self) -> Result<(), String> {
        self.send_command(&["cycle", "pause"])
    }

    pub fn stop(&self) -> Result<(), String> {
        let mut state = self.inner.lock();
        if let Some(mut child) = state.child.take() {
            let _ = child.kill();
            let _ = child.wait();
        }
        Ok(())
    }
}

pub type SharedMpv = Arc<MpvController>;
