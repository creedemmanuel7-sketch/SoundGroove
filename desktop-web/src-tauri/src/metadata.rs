//! Lecture des métadonnées embarquées (ID3 / FLAC / etc.) via lofty.

use crate::filename::{
    display_album, display_artist, display_title, parse_artist_title_from_filename,
};
use base64::{engine::general_purpose::STANDARD, Engine};
use lofty::file::{AudioFile, TaggedFileExt};
use lofty::picture::MimeType;
use lofty::probe::Probe;
use lofty::tag::Accessor;
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TrackMeta {
    pub path: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_ms: u64,
    /// Chemin cache local de la pochette (si extraite).
    pub cover_path: Option<String>,
    /// Data-URL légère pour l'UI (max ~pochette originale encodée) — None si absente / trop grosse.
    pub cover_data_url: Option<String>,
}

const MAX_COVER_BYTES_INLINE: usize = 750_000;

pub fn read_track_meta(path: &Path, cover_cache_dir: &Path) -> Result<TrackMeta, String> {
    let path_str = path.to_string_lossy().to_string();

    let tagged = Probe::open(path)
        .map_err(|e| format!("Ouverture impossible: {e}"))?
        .read()
        .map_err(|e| format!("Lecture tags impossible: {e}"))?;

    let duration_ms = tagged.properties().duration().as_millis() as u64;

    let tag = tagged.primary_tag().or_else(|| tagged.first_tag());

    let mut title_raw = tag.and_then(|t| t.title().map(|s| s.to_string()));
    let mut artist_raw = tag.and_then(|t| t.artist().map(|s| s.to_string()));
    let album_raw = tag.and_then(|t| t.album().map(|s| s.to_string()));

    if title_raw.as_ref().map(|s| s.trim().is_empty()).unwrap_or(true)
        || artist_raw.as_ref().map(|s| s.trim().is_empty()).unwrap_or(true)
    {
        let (parsed_artist, parsed_title) = parse_artist_title_from_filename(&path_str);
        if title_raw.as_ref().map(|s| s.trim().is_empty()).unwrap_or(true) {
            title_raw = parsed_title;
        }
        if artist_raw.as_ref().map(|s| s.trim().is_empty()).unwrap_or(true) {
            artist_raw = parsed_artist;
        }
    }

    let title = display_title(title_raw.as_deref(), &path_str);
    let artist = display_artist(artist_raw.as_deref());
    let album = display_album(album_raw.as_deref());

    let (cover_path, cover_data_url) = extract_cover(tag, path, cover_cache_dir);

    Ok(TrackMeta {
        path: path_str,
        title,
        artist,
        album,
        duration_ms,
        cover_path,
        cover_data_url,
    })
}

fn extract_cover(
    tag: Option<&lofty::tag::Tag>,
    audio_path: &Path,
    cover_cache_dir: &Path,
) -> (Option<String>, Option<String>) {
    let Some(tag) = tag else {
        return (None, None);
    };
    let picture = tag.pictures().first();
    let Some(picture) = picture else {
        return (None, None);
    };

    let data = picture.data();
    if data.is_empty() {
        return (None, None);
    }

    let ext = match picture.mime_type() {
        Some(MimeType::Jpeg) => "jpg",
        Some(MimeType::Png) => "png",
        Some(MimeType::Gif) => "gif",
        Some(MimeType::Bmp) => "bmp",
        Some(MimeType::Tiff) => "tiff",
        _ => "img",
    };
    let mime: String = match picture.mime_type() {
        Some(MimeType::Jpeg) => "image/jpeg".into(),
        Some(MimeType::Png) => "image/png".into(),
        Some(MimeType::Gif) => "image/gif".into(),
        Some(MimeType::Bmp) => "image/bmp".into(),
        Some(MimeType::Tiff) => "image/tiff".into(),
        Some(MimeType::Unknown(s)) if !s.is_empty() => s.clone(),
        _ => "application/octet-stream".into(),
    };

    let hash = {
        let mut hasher = Sha256::new();
        hasher.update(audio_path.to_string_lossy().as_bytes());
        hasher.update(data);
        hex::encode(hasher.finalize())
    };

    let _ = fs::create_dir_all(cover_cache_dir);
    let out: PathBuf = cover_cache_dir.join(format!("{hash}.{ext}"));
    if !out.exists() {
        if let Err(e) = fs::write(&out, data) {
            eprintln!("cover cache write failed: {e}");
        }
    }

    let cover_path = if out.exists() {
        Some(out.to_string_lossy().to_string())
    } else {
        None
    };

    let cover_data_url = if data.len() <= MAX_COVER_BYTES_INLINE {
        Some(format!("data:{mime};base64,{}", STANDARD.encode(data)))
    } else {
        None
    };

    (cover_path, cover_data_url)
}

pub fn is_audio_extension(path: &Path) -> bool {
    matches!(
        path.extension()
            .and_then(|e| e.to_str())
            .map(|e| e.to_ascii_lowercase())
            .as_deref(),
        Some("mp3")
            | Some("flac")
            | Some("m4a")
            | Some("aac")
            | Some("ogg")
            | Some("opus")
            | Some("wav")
            | Some("wma")
            | Some("aiff")
            | Some("aif")
            | Some("alac")
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use lofty::config::WriteOptions;
    use lofty::picture::{Picture, PictureType};
    use lofty::prelude::*;
    use lofty::tag::{Tag, TagType};

    fn make_short_mp3(path: &Path) {
        let status = std::process::Command::new("ffmpeg")
            .args([
                "-y",
                "-f",
                "lavfi",
                "-i",
                "anullsrc=r=44100:cl=mono",
                "-t",
                "0.3",
                "-q:a",
                "9",
                path.to_str().expect("utf8 path"),
            ])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status()
            .expect("ffmpeg must be available for this test");
        assert!(status.success(), "ffmpeg failed");
    }

    #[test]
    fn reads_id3_tags_and_cover_from_mp3() {
        let dir = std::env::temp_dir().join(format!("sg-meta-{}", std::process::id()));
        let _ = fs::create_dir_all(&dir);
        let path = dir.join("demo_track_320kbps.mp3");
        make_short_mp3(&path);

        let mut tag = Tag::new(TagType::Id3v2);
        tag.set_title("Neon Nights".into());
        tag.set_artist("Groove Pilot".into());
        tag.set_album("City Lights".into());
        // PNG 1x1
        let png: &[u8] = &[
            0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48,
            0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x00, 0x00,
            0x00, 0x90, 0x77, 0x53, 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, 0x08,
            0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00, 0x00, 0x00, 0x03, 0x00, 0x01, 0x00, 0x05, 0xFE,
            0xD4, 0xEF, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82,
        ];
        tag.push_picture(Picture::new_unchecked(
            PictureType::CoverFront,
            Some(MimeType::Png),
            None,
            png.to_vec(),
        ));
        tag.save_to_path(&path, WriteOptions::default())
            .expect("save tags");

        let cache = dir.join("covers");
        let meta = read_track_meta(&path, &cache).expect("read meta");
        assert_eq!(meta.title, "Neon Nights");
        assert_eq!(meta.artist, "Groove Pilot");
        assert_eq!(meta.album, "City Lights");
        assert!(meta.cover_path.is_some() || meta.cover_data_url.is_some());

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn falls_back_when_untagged() {
        let dir = std::env::temp_dir().join(format!("sg-meta-fb-{}", std::process::id()));
        let _ = fs::create_dir_all(&dir);
        let path = dir.join("Artist Name - Cool Song_256k.mp3");
        make_short_mp3(&path);
        let cache = dir.join("covers");
        let meta = read_track_meta(&path, &cache).expect("read meta");
        assert!(
            meta.title.to_lowercase().contains("cool song"),
            "title={}",
            meta.title
        );
        assert!(
            meta.artist.to_lowercase().contains("artist"),
            "artist={}",
            meta.artist
        );
        let _ = fs::remove_dir_all(&dir);
    }
}
