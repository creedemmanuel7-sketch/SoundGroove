//! Scan de dossiers / fichiers avec limite et isolation des erreurs.

use crate::metadata::{is_audio_extension, read_track_meta, TrackMeta};
use serde::Serialize;
use std::path::{Path, PathBuf};
use walkdir::WalkDir;

pub const DEFAULT_MAX_FILES: usize = 500;
pub const HARD_MAX_FILES: usize = 2000;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ScanResult {
    pub tracks: Vec<TrackMeta>,
    pub scanned_files: usize,
    pub skipped_errors: usize,
    pub truncated: bool,
    pub errors: Vec<String>,
}

pub fn scan_paths(
    roots: &[PathBuf],
    cover_cache_dir: &Path,
    max_files: usize,
) -> ScanResult {
    let limit = max_files.clamp(1, HARD_MAX_FILES);
    let mut tracks = Vec::new();
    let mut scanned_files = 0usize;
    let mut skipped_errors = 0usize;
    let mut truncated = false;
    let mut errors = Vec::new();

    'outer: for root in roots {
        if !root.exists() {
            skipped_errors += 1;
            errors.push(format!("Introuvable: {}", root.display()));
            continue;
        }

        if root.is_file() {
            if !is_audio_extension(root) {
                continue;
            }
            scanned_files += 1;
            match read_track_meta(root, cover_cache_dir) {
                Ok(meta) => tracks.push(meta),
                Err(e) => {
                    skipped_errors += 1;
                    if errors.len() < 25 {
                        errors.push(format!("{}: {e}", root.display()));
                    }
                }
            }
            if tracks.len() >= limit {
                truncated = true;
                break 'outer;
            }
            continue;
        }

        for entry in WalkDir::new(root)
            .follow_links(false)
            .max_depth(12)
            .into_iter()
            .filter_map(|e| e.ok())
        {
            let path = entry.path();
            if !path.is_file() || !is_audio_extension(path) {
                continue;
            }
            scanned_files += 1;
            match read_track_meta(path, cover_cache_dir) {
                Ok(meta) => tracks.push(meta),
                Err(e) => {
                    skipped_errors += 1;
                    if errors.len() < 25 {
                        errors.push(format!("{}: {e}", path.display()));
                    }
                }
            }
            if tracks.len() >= limit {
                truncated = true;
                break 'outer;
            }
        }
    }

    tracks.sort_by(|a, b| {
        a.artist
            .to_lowercase()
            .cmp(&b.artist.to_lowercase())
            .then_with(|| a.album.to_lowercase().cmp(&b.album.to_lowercase()))
            .then_with(|| a.title.to_lowercase().cmp(&b.title.to_lowercase()))
    });

    ScanResult {
        tracks,
        scanned_files,
        skipped_errors,
        truncated,
        errors,
    }
}
