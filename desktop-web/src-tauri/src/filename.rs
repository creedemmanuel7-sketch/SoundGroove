//! Fallback d'affichage quand les tags ID3/FLAC sont absents.
//! Nettoie les suffixes type `_320kbps`, `_256k`, codec, etc.

use regex::Regex;
use once_cell::sync::Lazy;

static UNKNOWN_MARKERS: &[&str] = &[
    "",
    "unknown",
    "<unknown>",
    "inconnu",
    "unknown artist",
    "artiste inconnu",
    "titre inconnu",
    "album inconnu",
];

static EXTENSION_RE: Lazy<Regex> = Lazy::new(|| {
    Regex::new(r"(?i)\.(mp3|aac|flac|ogg|opus|wav|m4a|alac|aiff?|wma)$").expect("ext regex")
});

static BITRATE_RE: Lazy<Regex> = Lazy::new(|| {
    // `_320kbps`, `_256k`, `.128kb`, ` 320 kbit`
    Regex::new(r"(?i)[_\-.\s]\d{2,4}\s?(kbps|kbit|kb|k)\b").expect("bitrate regex")
});

static CODEC_SUFFIX_RE: Lazy<Regex> = Lazy::new(|| {
    Regex::new(r"(?i)[_\-.]?(aac|mp3|flac|ogg|opus|wav|m4a|alac|aiff?)([_\-]?\d+)?$")
        .expect("codec regex")
});

static TRAILING_ID_RE: Lazy<Regex> = Lazy::new(|| Regex::new(r"[_\-]\d{3,}$").expect("id regex"));

static TRACK_PREFIX_RE: Lazy<Regex> =
    Lazy::new(|| Regex::new(r"^\d{1,3}[\s.\-_]+").expect("track prefix regex"));

pub fn is_unknown_marker(value: &str) -> bool {
    UNKNOWN_MARKERS.contains(&value.trim().to_lowercase().as_str())
}

pub fn display_artist(raw: Option<&str>) -> String {
    let value = raw.map(str::trim).unwrap_or("").to_string();
    if is_unknown_marker(&value) {
        "Artiste inconnu".into()
    } else {
        value
    }
}

pub fn display_album(raw: Option<&str>) -> String {
    let value = raw.map(str::trim).unwrap_or("").to_string();
    if is_unknown_marker(&value) {
        "Album inconnu".into()
    } else {
        value
    }
}

pub fn display_title(raw: Option<&str>, path: &str) -> String {
    let value = raw.map(str::trim).unwrap_or("").to_string();
    if is_unknown_marker(&value) || looks_like_filename(&value) {
        return clean_filename_title(path)
            .filter(|t| !is_unknown_marker(t))
            .unwrap_or_else(|| "Titre inconnu".into());
    }
    value
}

pub fn looks_like_filename(value: &str) -> bool {
    if value.contains('/') || value.contains('\\') {
        return true;
    }
    if EXTENSION_RE.is_match(value) {
        return true;
    }
    let underscores = value.chars().filter(|c| *c == '_').count();
    if underscores >= 2 && !value.contains(' ') {
        return true;
    }
    if CODEC_SUFFIX_RE.is_match(value) && underscores >= 1 {
        return true;
    }
    if BITRATE_RE.is_match(value) {
        return true;
    }
    false
}

fn strip_filename_junk(path: &str) -> Option<String> {
    let mut cleaned = path
        .rsplit(['/', '\\'])
        .next()
        .unwrap_or(path)
        .trim()
        .to_string();
    if cleaned.is_empty() {
        return None;
    }
    cleaned = EXTENSION_RE.replace(&cleaned, "").to_string();
    cleaned = BITRATE_RE.replace_all(&cleaned, "").to_string();
    cleaned = CODEC_SUFFIX_RE.replace(&cleaned, "").to_string();
    cleaned = TRAILING_ID_RE.replace(&cleaned, "").to_string();
    cleaned = TRACK_PREFIX_RE.replace(&cleaned, "").to_string();
    cleaned = cleaned.trim().to_string();
    if cleaned.is_empty() {
        None
    } else {
        Some(cleaned)
    }
}

fn humanize_stem(stem: &str) -> String {
    let cleaned = stem.replace('_', " ");
    // Conserve le séparateur " - " (artiste / titre), remplace les autres tirets.
    let cleaned = cleaned
        .split(" - ")
        .map(|part| part.replace('-', " "))
        .collect::<Vec<_>>()
        .join(" - ");
    let cleaned = Regex::new(r"\s+")
        .expect("ws")
        .replace_all(&cleaned, " ")
        .trim()
        .to_string();
    let mut chars = cleaned.chars();
    let first = chars
        .next()
        .map(|c| c.to_uppercase().to_string())
        .unwrap_or_default();
    format!("{first}{}", chars.as_str())
}

pub fn clean_filename_title(path: &str) -> Option<String> {
    let stem = strip_filename_junk(path)?;
    let cleaned = humanize_stem(&stem);
    if cleaned.is_empty() || is_unknown_marker(&cleaned) {
        return None;
    }
    Some(cleaned)
}

/// Tente "Artist - Title" depuis le nom de fichier.
pub fn parse_artist_title_from_filename(path: &str) -> (Option<String>, Option<String>) {
    let Some(stem) = strip_filename_junk(path) else {
        return (None, None);
    };
    let human = humanize_stem(&stem);
    if let Some((left, right)) = human.split_once(" - ") {
        let artist = left.trim();
        let title = right.trim();
        if !artist.is_empty() && !title.is_empty() {
            return (Some(artist.to_string()), Some(title.to_string()));
        }
    }
    if human.is_empty() || is_unknown_marker(&human) {
        (None, None)
    } else {
        (None, Some(human))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn strips_bitrate_junk() {
        let t = clean_filename_title(r"C:\Music\song_name_320kbps.mp3").unwrap();
        assert!(t.to_lowercase().contains("song name"));
        assert!(!t.to_lowercase().contains("320"));
    }

    #[test]
    fn parses_artist_dash_title() {
        let (a, t) = parse_artist_title_from_filename("Daft Punk - One More Time_256k.mp3");
        assert_eq!(a.as_deref(), Some("Daft Punk"));
        assert_eq!(t.as_deref(), Some("One More Time"));
    }
}
