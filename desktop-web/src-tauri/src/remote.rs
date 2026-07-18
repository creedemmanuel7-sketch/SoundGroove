//! Télécommande HTTP locale minimale (pause / next / prev / status).
//! Les actions sont poussées vers le frontend via un événement Tauri.

use parking_lot::Mutex;
use serde::Serialize;
use std::sync::Arc;
use std::thread;
use tauri::{AppHandle, Emitter};
use tiny_http::{Header, Method, Response, Server, StatusCode};

const DEFAULT_PORT: u16 = 17890;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RemoteStatus {
    pub running: bool,
    pub port: u16,
    pub url: String,
}

pub struct RemoteServer {
    inner: Mutex<RemoteState>,
}

struct RemoteState {
    running: bool,
    port: u16,
    stop_flag: Arc<Mutex<bool>>,
}

impl Default for RemoteServer {
    fn default() -> Self {
        Self::new()
    }
}

impl RemoteServer {
    pub fn new() -> Self {
        Self {
            inner: Mutex::new(RemoteState {
                running: false,
                port: DEFAULT_PORT,
                stop_flag: Arc::new(Mutex::new(false)),
            }),
        }
    }

    pub fn status(&self) -> RemoteStatus {
        let state = self.inner.lock();
        RemoteStatus {
            running: state.running,
            port: state.port,
            url: format!("http://127.0.0.1:{}", state.port),
        }
    }

    pub fn start(&self, app: AppHandle) -> Result<RemoteStatus, String> {
        let mut state = self.inner.lock();
        if state.running {
            return Ok(RemoteStatus {
                running: true,
                port: state.port,
                url: format!("http://127.0.0.1:{}", state.port),
            });
        }

        let port = state.port;
        let addr = format!("127.0.0.1:{port}");
        let server = Server::http(&addr).map_err(|e| format!("Bind remote {addr}: {e}"))?;

        *state.stop_flag.lock() = false;
        let stop_flag = Arc::clone(&state.stop_flag);
        state.running = true;

        thread::spawn(move || {
            for request in server.incoming_requests() {
                if *stop_flag.lock() {
                    break;
                }

                let url = request.url().to_string();
                let method = request.method().clone();

                let (status, body, event) = match (method, url.as_str()) {
                    (Method::Get, "/") | (Method::Get, "/status") => (
                        StatusCode(200),
                        r#"{"ok":true,"service":"soundgroove-remote","actions":["/play","/pause","/next","/prev"]}"#
                            .to_string(),
                        None,
                    ),
                    (Method::Get, "/play") | (Method::Post, "/play") => {
                        (StatusCode(200), r#"{"ok":true,"action":"play"}"#.into(), Some("play"))
                    }
                    (Method::Get, "/pause") | (Method::Post, "/pause") => {
                        (StatusCode(200), r#"{"ok":true,"action":"pause"}"#.into(), Some("pause"))
                    }
                    (Method::Get, "/next") | (Method::Post, "/next") => {
                        (StatusCode(200), r#"{"ok":true,"action":"next"}"#.into(), Some("next"))
                    }
                    (Method::Get, "/prev") | (Method::Post, "/prev") => {
                        (StatusCode(200), r#"{"ok":true,"action":"prev"}"#.into(), Some("prev"))
                    }
                    _ => (
                        StatusCode(404),
                        r#"{"ok":false,"error":"not found"}"#.into(),
                        None,
                    ),
                };

                if let Some(action) = event {
                    let _ = app.emit("remote-command", action);
                }

                let mut response = Response::from_string(body).with_status_code(status);
                if let Ok(h) = Header::from_bytes(&b"Content-Type"[..], &b"application/json"[..]) {
                    response.add_header(h);
                }
                let _ = request.respond(response);
            }
        });

        Ok(RemoteStatus {
            running: true,
            port,
            url: format!("http://127.0.0.1:{port}"),
        })
    }

    pub fn stop(&self) -> RemoteStatus {
        let mut state = self.inner.lock();
        *state.stop_flag.lock() = true;
        state.running = false;
        // Débloque éventuellement le serveur.
        let _ = std::net::TcpStream::connect(format!("127.0.0.1:{}", state.port));
        RemoteStatus {
            running: false,
            port: state.port,
            url: format!("http://127.0.0.1:{}", state.port),
        }
    }
}

pub type SharedRemote = Arc<RemoteServer>;
