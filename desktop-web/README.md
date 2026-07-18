# SoundGroove — Desktop (`desktop-web/`)

Shell **Tauri 2** + React/Vite pour SoundGroove. Lecture audio via **mpv** (IPC), dialogues fichiers **async** (pas de `blocking_pick_*` sur le thread UI), scan bibliothèque hors thread async.

> L’app Android (`:app`) n’est pas touchée par ce dossier.

## Prérequis

| Plateforme | Outils |
|---|---|
| **Tous** | Node 18+, Rust stable récent via rustup (≥ 1.85 ; `rust-toolchain.toml`), npm |
| **Windows** (cible principale desktop) | Visual Studio Build Tools (C++), WebView2, **mpv** dans le `PATH` |
| **Linux** (smoke CI / cloud) | `libwebkit2gtk-4.1-dev`, `libayatana-appindicator3-dev`, `librsvg2-dev`, `patchelf` ; mpv optionnel pour `cargo check` |

## Commandes

```bash
cd desktop-web
npm install

# Smoke compile Rust (cloud Linux OK)
npm run check:rust
# équivalent : cargo check --manifest-path src-tauri/Cargo.toml

# Dev Tauri (UI + backend)
npm run tauri:dev

# Build release
npm run tauri:build
```

### Windows

```powershell
cd desktop-web
npm install
.\dev.ps1
# ou
npm run tauri:dev:win
```

Le wrapper `dev.ps1` lance `tauri dev` avec la politique d’exécution Bypass locale au script.

## Commandes Rust exposées (`invoke`)

| Commande | Rôle |
|---|---|
| `mpv_ensure` / `mpv_quit` | Démarre / arrête le process mpv |
| `mpv_load` / `mpv_play` / `mpv_pause` / `mpv_toggle_pause` / `mpv_stop` | Contrôle lecture |
| `mpv_seek` / `mpv_set_volume` / `mpv_get_status` | Position, volume, statut |
| `open_audio_path` | Charge un chemin audio connu |
| `pick_audio_file` | Dialogue **async** fichier audio |
| `pick_music_folder` | Dialogue **async** dossier |
| `scan_music_folder` | Scan récursif via `spawn_blocking` |

Les ACL correspondantes sont dans :

- `src-tauri/capabilities/default.json` — `allow-*` pour chaque invoke + `core:default` + `dialog:default`
- `src-tauri/permissions/app-commands.toml` — définitions explicites des permissions

## Windows vs cloud Linux

| Vérification | Windows | Cloud Linux |
|---|---|---|
| `cargo check` (src-tauri) | Oui | **Oui** (smoke principal en cloud) |
| `tauri dev` / UI WebView | Oui | Possible si deps GTK/WebKit installées ; pas le workflow cible |
| Lecture mpv réelle | Oui (mpv dans PATH) | Optionnel ; absent ⇒ erreurs runtime à l’invoke, compile OK |
| `dev.ps1` / `tauri:dev:win` | **Windows-only** | N/A |

En cloud, le livrable de smoke est **`cargo check`** dans `src-tauri/`. La validation UI + mpv se fait sur machine Windows (ou Linux desktop local avec mpv).

## Architecture rapide

```
desktop-web/
  src/                 # React (Vite)
  src-tauri/
    src/lib.rs         # Commandes Tauri
    src/mpv.rs         # Process mpv + IPC
    src/library.rs     # Scan audio
    capabilities/      # ACL fenêtres
    permissions/       # allow-* applicatifs
  dev.ps1              # Wrapper Windows
```
