# SoundGroove Desktop (`desktop-web/`)

App **Tauri 2** (HTML/JS + Rust) pour une bibliothèque audio locale type Spotify : titres, artistes, albums et **pochettes embarquées** lus depuis les tags du fichier (ID3 / FLAC / etc.).

Sources légales uniquement — pas de scraping. Un lookup MusicBrainz pourra venir plus tard.

## Fonctionnalités MVP

- Empty state + import dossier / fichiers
- Scan récursif avec limite (500, max dur 2000) et isolation des erreurs
- Métadonnées via [`lofty`](https://crates.io/crates/lofty) : title, artist, album, duration, cover
- Fallback nom de fichier (`Artist - Title_320kbps.mp3` → artiste/titre nettoyés)
- Liste avec vignette + Now Playing avec grande pochette
- Lecture **mpv** si disponible, sinon `<audio>` HTML
- Remote HTTP locale optionnelle (`http://127.0.0.1:17890` — `/play` `/pause` `/next` `/prev`)

## Prérequis

- Node.js 18+
- Rust (stable récent)
- [Prérequis Tauri](https://tauri.app/start/prerequisites/) (WebView2 sur Windows)
- Optionnel : [mpv](https://mpv.io/) dans le `PATH`

## Lancer

```powershell
# Windows
.\dev.ps1
# ou
npm install
npm run tauri:dev:win
```

```bash
# Linux / macOS
cd desktop-web
npm install
npm run tauri:dev
```

Tests Rust (fallback filename, etc.) :

```bash
npm run test:rust
```

## Commandes Tauri (Rust)

| Commande | Rôle |
|---|---|
| `pick_music_folder` / `pick_music_files` | Dialogues natifs |
| `scan_library` | Scan + tags + covers (cache app) |
| `read_audio_tags` | Tags d’un seul fichier |
| `mpv_*` | Contrôle mpv IPC |
| `remote_*` | Télécommande HTTP locale |

Les pochettes sont écrites sous le cache app (`covers/`) et exposées aussi en data-URL si elles restent raisonnables (&lt; ~750 Ko).

## Suite possible

- Enrichissement MusicBrainz quand tags absents
- Resize / thumbnails plus légers pour très gros dossiers
- Sync / remote plus riche (queue, volume)
