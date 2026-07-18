//! Scan de dossiers musique et helpers fichiers audio.

use std::path::{Path, PathBuf};

use serde::Serialize;
use walkdir::WalkDir;

const AUDIO_EXTENSIONS: &[&str] = &[
    "mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "wma", "aiff", "aif", "alac",
];

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ScannedTrack {
    pub path: String,
    pub file_name: String,
    pub extension: String,
    pub size_bytes: u64,
}

pub fn is_audio_path(path: &Path) -> bool {
    path.extension()
        .and_then(|e| e.to_str())
        .map(|e| AUDIO_EXTENSIONS.iter().any(|ext| e.eq_ignore_ascii_case(ext)))
        .unwrap_or(false)
}

/// Scan récursif (synchrone) — à appeler via `spawn_blocking` depuis une commande async.
pub fn scan_music_folder_sync(root: &str) -> Result<Vec<ScannedTrack>, String> {
    let root_path = PathBuf::from(root);
    if !root_path.is_dir() {
        return Err(format!("Dossier invalide: {root}"));
    }

    let mut tracks = Vec::new();
    for entry in WalkDir::new(&root_path)
        .follow_links(true)
        .into_iter()
        .filter_map(|e| e.ok())
    {
        let path = entry.path();
        if !path.is_file() || !is_audio_path(path) {
            continue;
        }
        let meta = entry.metadata().map_err(|e| e.to_string())?;
        let file_name = path
            .file_name()
            .map(|s| s.to_string_lossy().into_owned())
            .unwrap_or_default();
        let extension = path
            .extension()
            .and_then(|e| e.to_str())
            .unwrap_or("")
            .to_ascii_lowercase();
        tracks.push(ScannedTrack {
            path: path.to_string_lossy().into_owned(),
            file_name,
            extension,
            size_bytes: meta.len(),
        });
    }

    tracks.sort_by(|a, b| a.path.to_lowercase().cmp(&b.path.to_lowercase()));
    Ok(tracks)
}
