mod filename;
mod metadata;
mod mpv;
mod remote;
mod scan;

use mpv::{MpvController, SharedMpv};
use remote::{RemoteServer, SharedRemote};
use scan::{scan_paths, DEFAULT_MAX_FILES};
use serde::Deserialize;
use std::path::PathBuf;
use std::sync::Arc;
use tauri::Manager;

fn cover_cache_dir(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    let dir = app
        .path()
        .app_cache_dir()
        .map_err(|e| format!("cache dir: {e}"))?
        .join("covers");
    std::fs::create_dir_all(&dir).map_err(|e| format!("mkdir covers: {e}"))?;
    Ok(dir)
}

#[tauri::command]
fn read_audio_tags(app: tauri::AppHandle, path: String) -> Result<metadata::TrackMeta, String> {
    let cache = cover_cache_dir(&app)?;
    metadata::read_track_meta(PathBuf::from(path).as_path(), &cache)
}

#[tauri::command]
async fn pick_music_folder() -> Option<String> {
    tauri::async_runtime::spawn_blocking(|| {
        rfd::FileDialog::new()
            .set_title("Choisir un dossier musical")
            .pick_folder()
            .map(|p| p.to_string_lossy().to_string())
    })
    .await
    .ok()
    .flatten()
}

#[tauri::command]
async fn pick_music_files() -> Vec<String> {
    tauri::async_runtime::spawn_blocking(|| {
        rfd::FileDialog::new()
            .set_title("Choisir des fichiers audio")
            .add_filter(
                "Audio",
                &["mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "aiff", "aif"],
            )
            .pick_files()
            .unwrap_or_default()
            .into_iter()
            .map(|p| p.to_string_lossy().to_string())
            .collect()
    })
    .await
    .unwrap_or_default()
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ScanArgs {
    paths: Vec<String>,
    max_files: Option<usize>,
}

#[tauri::command]
fn scan_library(app: tauri::AppHandle, args: ScanArgs) -> Result<scan::ScanResult, String> {
    let cache = cover_cache_dir(&app)?;
    let roots: Vec<PathBuf> = args.paths.into_iter().map(PathBuf::from).collect();
    if roots.is_empty() {
        return Err("Aucun chemin fourni".into());
    }
    let max = args.max_files.unwrap_or(DEFAULT_MAX_FILES);
    Ok(scan_paths(&roots, &cache, max))
}

#[tauri::command]
fn mpv_status(mpv: tauri::State<SharedMpv>) -> mpv::MpvStatus {
    mpv.status()
}

#[tauri::command]
fn mpv_play(mpv: tauri::State<SharedMpv>, path: String) -> Result<mpv::MpvStatus, String> {
    mpv.play(&path)
}

#[tauri::command]
fn mpv_pause(mpv: tauri::State<SharedMpv>) -> Result<(), String> {
    mpv.pause_toggle()
}

#[tauri::command]
fn mpv_stop(mpv: tauri::State<SharedMpv>) -> Result<(), String> {
    mpv.stop()
}

#[tauri::command]
fn remote_status(remote: tauri::State<SharedRemote>) -> remote::RemoteStatus {
    remote.status()
}

#[tauri::command]
fn remote_start(
    app: tauri::AppHandle,
    remote: tauri::State<SharedRemote>,
) -> Result<remote::RemoteStatus, String> {
    remote.start(app)
}

#[tauri::command]
fn remote_stop(remote: tauri::State<SharedRemote>) -> remote::RemoteStatus {
    remote.stop()
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let mpv: SharedMpv = Arc::new(MpvController::new());
    let remote: SharedRemote = Arc::new(RemoteServer::new());

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(mpv)
        .manage(remote)
        .invoke_handler(tauri::generate_handler![
            read_audio_tags,
            pick_music_folder,
            pick_music_files,
            scan_library,
            mpv_status,
            mpv_play,
            mpv_pause,
            mpv_stop,
            remote_status,
            remote_start,
            remote_stop
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
