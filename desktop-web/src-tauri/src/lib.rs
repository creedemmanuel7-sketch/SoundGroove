mod library;
mod mpv;

use std::sync::Mutex;

use library::ScannedTrack;
use mpv::{MpvController, MpvStatus};
use tauri::{AppHandle, Manager, State};
use tauri_plugin_dialog::{DialogExt, FilePath};

struct AppState {
    mpv: MpvController,
    /// Dernier chemin audio ouvert / sélectionné.
    last_audio_path: Mutex<Option<String>>,
}

impl Default for AppState {
    fn default() -> Self {
        Self {
            mpv: MpvController::default(),
            last_audio_path: Mutex::new(None),
        }
    }
}

// ─── mpv_* ───────────────────────────────────────────────────────────────────

#[tauri::command]
fn mpv_ensure(state: State<'_, AppState>) -> Result<(), String> {
    state.mpv.ensure().map_err(Into::into)
}

#[tauri::command]
fn mpv_quit(state: State<'_, AppState>) -> Result<(), String> {
    state.mpv.quit().map_err(Into::into)
}

#[tauri::command]
fn mpv_load(state: State<'_, AppState>, path: String) -> Result<(), String> {
    state.mpv.load(&path)?;
    if let Ok(mut last) = state.last_audio_path.lock() {
        *last = Some(path);
    }
    Ok(())
}

#[tauri::command]
fn mpv_play(state: State<'_, AppState>) -> Result<(), String> {
    state.mpv.play().map_err(Into::into)
}

#[tauri::command]
fn mpv_pause(state: State<'_, AppState>) -> Result<(), String> {
    state.mpv.pause().map_err(Into::into)
}

#[tauri::command]
fn mpv_toggle_pause(state: State<'_, AppState>) -> Result<(), String> {
    state.mpv.toggle_pause().map_err(Into::into)
}

#[tauri::command]
fn mpv_stop(state: State<'_, AppState>) -> Result<(), String> {
    state.mpv.stop().map_err(Into::into)
}

#[tauri::command]
fn mpv_seek(state: State<'_, AppState>, position_secs: f64) -> Result<(), String> {
    state.mpv.seek(position_secs).map_err(Into::into)
}

#[tauri::command]
fn mpv_set_volume(state: State<'_, AppState>, volume: f64) -> Result<(), String> {
    state.mpv.set_volume(volume).map_err(Into::into)
}

#[tauri::command]
fn mpv_get_status(state: State<'_, AppState>) -> Result<MpvStatus, String> {
    state.mpv.status().map_err(Into::into)
}

// ─── Fichiers / bibliothèque ─────────────────────────────────────────────────

/// Ouvre un chemin audio connu (charge dans mpv).
#[tauri::command]
fn open_audio_path(state: State<'_, AppState>, path: String) -> Result<(), String> {
    state.mpv.load(&path)?;
    if let Ok(mut last) = state.last_audio_path.lock() {
        *last = Some(path);
    }
    Ok(())
}

/// Dialogue async — ne jamais appeler `blocking_pick_*` sur le thread UI.
#[tauri::command]
async fn pick_audio_file(app: AppHandle) -> Result<Option<String>, String> {
    let (tx, rx) = tokio::sync::oneshot::channel();
    app.dialog()
        .file()
        .add_filter(
            "Audio",
            &["mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "wma", "aiff"],
        )
        .pick_file(move |file| {
            let _ = tx.send(file);
        });

    match rx.await {
        Ok(Some(path)) => Ok(Some(file_path_to_string(path)?)),
        Ok(None) => Ok(None),
        Err(_) => Err("Dialogue fichier interrompu".into()),
    }
}

/// Dialogue async de dossier musique.
#[tauri::command]
async fn pick_music_folder(app: AppHandle) -> Result<Option<String>, String> {
    let (tx, rx) = tokio::sync::oneshot::channel();
    app.dialog().file().pick_folder(move |folder| {
        let _ = tx.send(folder);
    });

    match rx.await {
        Ok(Some(path)) => Ok(Some(file_path_to_string(path)?)),
        Ok(None) => Ok(None),
        Err(_) => Err("Dialogue dossier interrompu".into()),
    }
}

/// Scan IO sur un worker (`spawn_blocking`) pour ne pas bloquer le runtime async.
#[tauri::command]
async fn scan_music_folder(path: String) -> Result<Vec<ScannedTrack>, String> {
    tauri::async_runtime::spawn_blocking(move || library::scan_music_folder_sync(&path))
        .await
        .map_err(|e| format!("scan join: {e}"))?
}

fn file_path_to_string(path: FilePath) -> Result<String, String> {
    path.into_path()
        .map(|p| p.to_string_lossy().into_owned())
        .map_err(|e| e.to_string())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .manage(AppState::default())
        .invoke_handler(tauri::generate_handler![
            mpv_ensure,
            mpv_quit,
            mpv_load,
            mpv_play,
            mpv_pause,
            mpv_toggle_pause,
            mpv_stop,
            mpv_seek,
            mpv_set_volume,
            mpv_get_status,
            open_audio_path,
            pick_audio_file,
            pick_music_folder,
            scan_music_folder,
        ])
        .setup(|app| {
            // Garde une référence stable à l'état (évite les warnings unused sur certaines configs).
            let _ = app.state::<AppState>();
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("erreur au démarrage de SoundGroove desktop");
}
