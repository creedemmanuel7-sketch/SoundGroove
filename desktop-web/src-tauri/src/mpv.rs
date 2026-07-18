//! Contrôle léger d'un processus `mpv` via IPC JSON.
//!
//! - Linux/macOS : Unix domain socket dans le répertoire temporaire
//! - Windows : named pipe `\\.\pipe\soundgroove-mpv-<pid>`
//!
//! `cargo check` n'exige pas que le binaire `mpv` soit présent ; il est
//! résolu uniquement à l'exécution (`PATH`).

use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::thread;
use std::time::{Duration, Instant};

use serde::Serialize;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum MpvError {
    #[error("{0}")]
    Message(String),
}

impl From<MpvError> for String {
    fn from(value: MpvError) -> Self {
        value.to_string()
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MpvStatus {
    pub running: bool,
    pub path: Option<String>,
    pub paused: bool,
    pub position_secs: f64,
    pub duration_secs: f64,
    pub volume: f64,
}

struct MpvProcess {
    child: Child,
    ipc_path: PathBuf,
    current_path: Option<String>,
}

pub struct MpvController {
    inner: Mutex<Option<MpvProcess>>,
}

impl Default for MpvController {
    fn default() -> Self {
        Self {
            inner: Mutex::new(None),
        }
    }
}

impl MpvController {
    pub fn ensure(&self) -> Result<(), MpvError> {
        let mut guard = self
            .inner
            .lock()
            .map_err(|_| MpvError::Message("mpv lock".into()))?;
        if let Some(proc) = guard.as_mut() {
            if proc.child.try_wait().ok().flatten().is_none() {
                return Ok(());
            }
        }
        *guard = Some(spawn_mpv()?);
        Ok(())
    }

    pub fn quit(&self) -> Result<(), MpvError> {
        let mut guard = self
            .inner
            .lock()
            .map_err(|_| MpvError::Message("mpv lock".into()))?;
        if let Some(mut proc) = guard.take() {
            let _ = send_ipc(&proc.ipc_path, r#"{"command":["quit"]}"#);
            let _ = proc.child.kill();
            let _ = proc.child.wait();
            #[cfg(unix)]
            {
                let _ = std::fs::remove_file(&proc.ipc_path);
            }
        }
        Ok(())
    }

    pub fn load(&self, path: &str) -> Result<(), MpvError> {
        self.ensure()?;
        let path = normalize_path(path)?;
        let mut guard = self
            .inner
            .lock()
            .map_err(|_| MpvError::Message("mpv lock".into()))?;
        let proc = guard
            .as_mut()
            .ok_or_else(|| MpvError::Message("mpv non démarré".into()))?;
        let cmd = format!(
            r#"{{"command":["loadfile",{},"replace"]}}"#,
            serde_json::to_string(&path).unwrap_or_else(|_| format!("\"{path}\""))
        );
        send_ipc(&proc.ipc_path, &cmd)?;
        proc.current_path = Some(path);
        let _ = send_ipc(
            &proc.ipc_path,
            r#"{"command":["set_property","pause",false]}"#,
        );
        Ok(())
    }

    pub fn play(&self) -> Result<(), MpvError> {
        self.ensure()?;
        self.ipc_cmd(r#"{"command":["set_property","pause",false]}"#)
    }

    pub fn pause(&self) -> Result<(), MpvError> {
        self.ensure()?;
        self.ipc_cmd(r#"{"command":["set_property","pause",true]}"#)
    }

    pub fn toggle_pause(&self) -> Result<(), MpvError> {
        self.ensure()?;
        self.ipc_cmd(r#"{"command":["cycle","pause"]}"#)
    }

    pub fn stop(&self) -> Result<(), MpvError> {
        self.ensure()?;
        self.ipc_cmd(r#"{"command":["stop"]}"#)?;
        let mut guard = self
            .inner
            .lock()
            .map_err(|_| MpvError::Message("mpv lock".into()))?;
        if let Some(proc) = guard.as_mut() {
            proc.current_path = None;
        }
        Ok(())
    }

    pub fn seek(&self, position_secs: f64) -> Result<(), MpvError> {
        self.ensure()?;
        let cmd = format!(r#"{{"command":["seek",{position_secs},"absolute"]}}"#);
        self.ipc_cmd(&cmd)
    }

    pub fn set_volume(&self, volume: f64) -> Result<(), MpvError> {
        self.ensure()?;
        let volume = volume.clamp(0.0, 150.0);
        let cmd = format!(r#"{{"command":["set_property","volume",{volume}]}}"#);
        self.ipc_cmd(&cmd)
    }

    pub fn status(&self) -> Result<MpvStatus, MpvError> {
        let guard = self
            .inner
            .lock()
            .map_err(|_| MpvError::Message("mpv lock".into()))?;
        let Some(proc) = guard.as_ref() else {
            return Ok(MpvStatus {
                running: false,
                path: None,
                paused: true,
                position_secs: 0.0,
                duration_secs: 0.0,
                volume: 100.0,
            });
        };

        let paused = get_bool_property(&proc.ipc_path, "pause").unwrap_or(true);
        let position_secs = get_f64_property(&proc.ipc_path, "time-pos").unwrap_or(0.0);
        let duration_secs = get_f64_property(&proc.ipc_path, "duration").unwrap_or(0.0);
        let volume = get_f64_property(&proc.ipc_path, "volume").unwrap_or(100.0);

        Ok(MpvStatus {
            running: true,
            path: proc.current_path.clone(),
            paused,
            position_secs,
            duration_secs,
            volume,
        })
    }

    fn ipc_cmd(&self, payload: &str) -> Result<(), MpvError> {
        let guard = self
            .inner
            .lock()
            .map_err(|_| MpvError::Message("mpv lock".into()))?;
        let proc = guard
            .as_ref()
            .ok_or_else(|| MpvError::Message("mpv non démarré".into()))?;
        send_ipc(&proc.ipc_path, payload)
    }
}

fn normalize_path(path: &str) -> Result<String, MpvError> {
    let p = Path::new(path);
    if !p.exists() {
        return Err(MpvError::Message(format!("Fichier introuvable: {path}")));
    }
    Ok(p.to_string_lossy().into_owned())
}

fn spawn_mpv() -> Result<MpvProcess, MpvError> {
    let ipc_path = ipc_socket_path();
    #[cfg(unix)]
    {
        let _ = std::fs::remove_file(&ipc_path);
    }

    let ipc_arg = format!("--input-ipc-server={}", ipc_path.display());

    let child = Command::new("mpv")
        .args([
            "--idle=yes",
            "--force-window=no",
            "--no-video",
            "--keep-open=yes",
            &ipc_arg,
        ])
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|e| {
            MpvError::Message(format!(
                "Impossible de démarrer mpv ({e}). Installez mpv et assurez-vous qu'il est dans le PATH."
            ))
        })?;

    wait_for_ipc(&ipc_path, Duration::from_secs(5))?;

    Ok(MpvProcess {
        child,
        ipc_path,
        current_path: None,
    })
}

fn ipc_socket_path() -> PathBuf {
    #[cfg(windows)]
    {
        PathBuf::from(format!(
            r"\\.\pipe\soundgroove-mpv-{}",
            std::process::id()
        ))
    }
    #[cfg(not(windows))]
    {
        std::env::temp_dir().join(format!("soundgroove-mpv-{}.sock", std::process::id()))
    }
}

fn wait_for_ipc(path: &Path, timeout: Duration) -> Result<(), MpvError> {
    let start = Instant::now();
    while start.elapsed() < timeout {
        #[cfg(windows)]
        {
            if try_connect(path).is_ok() {
                return Ok(());
            }
        }
        #[cfg(not(windows))]
        {
            if path.exists() {
                thread::sleep(Duration::from_millis(50));
                return Ok(());
            }
        }
        thread::sleep(Duration::from_millis(40));
    }
    Err(MpvError::Message(
        "Timeout en attendant le socket IPC mpv".into(),
    ))
}

fn send_ipc(path: &Path, payload: &str) -> Result<(), MpvError> {
    let mut stream = try_connect(path)?;
    let line = if payload.ends_with('\n') {
        payload.to_string()
    } else {
        format!("{payload}\n")
    };
    stream
        .write_all(line.as_bytes())
        .map_err(|e| MpvError::Message(format!("IPC write: {e}")))?;
    let _ = stream.flush();
    // Best-effort: consommer une ligne de réponse
    let mut reader = BufReader::new(stream);
    let mut response = String::new();
    let _ = reader.read_line(&mut response);
    Ok(())
}

fn get_f64_property(path: &Path, name: &str) -> Result<f64, MpvError> {
    let cmd = format!(r#"{{"command":["get_property","{name}"]}}"#);
    let value = ipc_request_value(path, &cmd)?;
    value
        .as_f64()
        .or_else(|| value.as_i64().map(|v| v as f64))
        .ok_or_else(|| MpvError::Message(format!("propriété {name} non numérique")))
}

fn get_bool_property(path: &Path, name: &str) -> Result<bool, MpvError> {
    let cmd = format!(r#"{{"command":["get_property","{name}"]}}"#);
    let value = ipc_request_value(path, &cmd)?;
    value
        .as_bool()
        .ok_or_else(|| MpvError::Message(format!("propriété {name} non booléenne")))
}

fn ipc_request_value(path: &Path, payload: &str) -> Result<serde_json::Value, MpvError> {
    let mut stream = try_connect(path)?;
    let line = if payload.ends_with('\n') {
        payload.to_string()
    } else {
        format!("{payload}\n")
    };
    stream
        .write_all(line.as_bytes())
        .map_err(|e| MpvError::Message(format!("IPC write: {e}")))?;
    let _ = stream.flush();
    let mut reader = BufReader::new(stream);
    let mut response = String::new();
    reader
        .read_line(&mut response)
        .map_err(|e| MpvError::Message(format!("IPC read: {e}")))?;
    let parsed: serde_json::Value = serde_json::from_str(response.trim())
        .map_err(|e| MpvError::Message(format!("IPC JSON: {e}")))?;
    if parsed.get("error").and_then(|e| e.as_str()) != Some("success") {
        return Err(MpvError::Message(format!("IPC error: {response}")));
    }
    Ok(parsed
        .get("data")
        .cloned()
        .unwrap_or(serde_json::Value::Null))
}

#[cfg(unix)]
fn try_connect(path: &Path) -> Result<std::os::unix::net::UnixStream, MpvError> {
    use std::os::unix::net::UnixStream;
    UnixStream::connect(path).map_err(|e| MpvError::Message(format!("IPC connect: {e}")))
}

#[cfg(windows)]
fn try_connect(path: &Path) -> Result<std::fs::File, MpvError> {
    use std::fs::OpenOptions;
    OpenOptions::new()
        .read(true)
        .write(true)
        .open(path)
        .map_err(|e| MpvError::Message(format!("IPC connect: {e}")))
}
