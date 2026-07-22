# SoundGroove

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-API%2024%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Media3](https://img.shields.io/badge/Media3-1.3.1-FF6F00)](https://developer.android.com/guide/topics/media/media3)
[![Site](https://img.shields.io/badge/site-soundgroove.app-111111)](https://soundgroove.app)

Lecteur de musique **locale** pour Android — bibliothèque MediaStore, paroles synchronisées, égaliseur, thèmes personnalisables. Pas de compte, pas de tracking, pas de streaming cloud.

Site vitrine / téléchargement APK : [soundgroove.app](https://soundgroove.app)

---

## Table des matières

- [Pitch](#pitch)
- [Fonctionnalités](#fonctionnalités)
- [Captures d'écran](#captures-décran)
- [Stack technique](#stack-technique)
- [Architecture](#architecture)
- [Structure du dépôt](#structure-du-dépôt)
- [Prérequis et build](#prérequis-et-build)
- [Gestes principaux](#gestes-principaux)
- [Configuration et permissions](#configuration-et-permissions)
- [Documentation](#documentation)
- [Roadmap](#roadmap)
- [Contribution](#contribution)
- [License](#license)
- [Crédits](#crédits)
- [English (short)](#english-short)

---

## Pitch

SoundGroove lit les fichiers audio déjà présents sur l'appareil (MediaStore et, en option, dossiers ajoutés via SAF). Room conserve favoris, playlists, historique et overrides. Les paroles peuvent venir du cache local, de fichiers `.lrc` voisins, de [LRCLIB](https://lrclib.net/), ou d'un collage manuel après recherche Google in-app.

L'expérience vise un lecteur immersif (Player plein écran, mini-player unifié, shared element pochette, gestes) plutôt qu'un simple explorateur de fichiers.

---

## Fonctionnalités

Basé sur le code et la documentation du dépôt (`docs/`, `app/`). Pas de feature inventée.

### Bibliothèque et lecture

- Bibliothèque locale via **MediaStore** + dossiers optionnels (**SAF**)
- Lecture **Media3 / ExoPlayer** avec notification et service foreground média
- Favoris, playlists (dont smart), historique / récemment joué
- Overrides métadonnées et pochettes personnalisées
- Gapless, crossfade, vitesse / pitch, sleep timer
- Égaliseur 10 bandes (presets ; EQ per-track en fondation, UX partielle)
- Scrobbling / stats d'écoute **locales** (seuil 50 % / 4 min)

### Paroles

- Paroles synchronisées **LRC** (auto-scroll, surbrillance ligne)
- Sources : fichiers locaux voisins, cache app (LRU borné), **LRCLIB**
- Recherche Google in-app (WebView) + collage presse-papiers — sans scraping automatique
- Édition / suppression des paroles enregistrées
- Détails : [docs/FEATURES_LYRICS_WEB_SEARCH.md](docs/FEATURES_LYRICS_WEB_SEARCH.md), [docs/STORAGE_AND_CACHE.md](docs/STORAGE_AND_CACHE.md)

### UI / UX

- Jetpack Compose + Material 3, design system interne (`Sg*`)
- Player immersif + mini-player global + morph pochette (shared element)
- Gestes Player ↔ Paroles ↔ File d'attente (voir [contrat de navigation](docs/NAVIGATION_CONTRACT.md))
- Thèmes et accents (dont accent dynamique depuis la pochette)
- Mode voiture (plein écran, gros contrôles, listes courtes)
- Predictive back (API 33+) et respect du reduced motion

### Système

- Widget home (layouts compact / large, shuffle sur grand format)
- Android Auto browse (Playlists / Dossiers / Favoris via `MediaLibraryService`)
- Backup / import-export réglages et playlists (`BackupSnapshot`)
- Gestion de cache bornée (paroles, WebView, cartes de partage) + section Stockage dans Réglages
- Partage morceau + lien site (`https://soundgroove.app`)

---

## Captures d'écran

Dossier prévu : [`screenshots_/`](screenshots_/)

| Accueil | Lecteur | Paroles |
|---------|---------|---------|
| ![Accueil](screenshots_/home.png) | ![Lecteur](screenshots_/player.png) | ![Paroles](screenshots_/lyrics.png) |

> Placeholders : déposer des PNG/WebP nommés `home.png`, `player.png`, `lyrics.png` (ou mettre à jour les chemins ci-dessus) dans `screenshots_/`.

---

## Stack technique

| Couche | Technologie | Notes |
|--------|-------------|--------|
| Langage | Kotlin **2.0.21** | AGP **8.13.2**, JDK 11 |
| UI | Jetpack Compose + Material 3 | BOM `2024.09.00` |
| Navigation | Navigation Compose **2.8.3** | + SharedTransitionLayout |
| Lecture | Media3 ExoPlayer + Session **1.3.1** | `PlaybackService` = `MediaLibraryService` |
| Persistance | Room **2.8.4** + KSP | Favoris, playlists, historique, overrides |
| Préférences | DataStore + SharedPreferences | Thème, lecture, hints gestes… |
| Images | Coil Compose **2.6.0** | Cache mémoire / disque configuré |
| Couleurs | Palette KTX | Accent depuis pochette |
| SDK | min **24** / target & compile **36** | `applicationId` : `com.credo.soundgroove` |

**Site vitrine** (`website/`) : Next.js 15, React 19, TypeScript, déploiement Netlify.

**Desktop** (`desktop/`) : skeleton Compose Multiplatform Windows (en cours — voir `desktop/README.md`).

---

## Architecture

Pattern : **MVVM + Repository + service média**.

```
MainActivity
  └─ SoundGrooveTheme / AppNavigation
       ├─ Screens (Compose) ← StateFlow
       ├─ SoundGrooveViewModel (+ Home / Search / Lyrics VM)
       │    ├─ MusicRepository / CombinedMusicRepository (MediaStore + SAF)
       │    ├─ DatabaseRepository (Room)
       │    ├─ ListeningStats / Scrobble / SearchHistory / Backup
       │    └─ MediaController ──► PlaybackService (ExoPlayer + MediaSession)
       └─ LyricsRepository → LyricsCacheStore / LrcLibClient / fichiers locaux
```

Couches principales sous `app/src/main/java/com/credo/soundgroove/` :

| Package | Rôle |
|---------|------|
| `ui/` | Écrans, composants, navigation, thème / motion |
| `viewmodel/` | État UI et orchestration |
| `data/` | Modèles, repositories, backup, smart playlists |
| `lyrics/` | Pipeline paroles (isolé) |
| `widget/` | Widget home + actions |
| `auto/` | Catalogue / IDs Android Auto |
| `util/` | EQ, prefs, stockage, Coil, liens… |
| `PlaybackService.kt` | Lecture + session + notification |

Détail : [docs/architecture-app.md](docs/architecture-app.md).

---

## Structure du dépôt

```
SoundGroove/
├── app/                 # Application Android (module principal)
├── website/             # Landing Next.js (soundgroove.app)
├── desktop/             # Skeleton CMP Desktop (WIP)
├── shared/              # Réservé KMP (pas encore module Gradle)
├── docs/                # Documentation produit / technique
├── screenshots_/        # Captures pour le README (à remplir)
├── gradle/              # Version catalog (libs.versions.toml)
├── build.gradle.kts
├── settings.gradle.kts  # :app, :desktop
└── README.md            # Ce fichier
```

Index docs : [docs/index.md](docs/index.md) · Vue d'ensemble : [docs/project-overview.md](docs/project-overview.md).

---

## Prérequis et build

### Prérequis

- [Android Studio](https://developer.android.com/studio) récent (compatible AGP 8.13)
- JDK **11+**
- SDK Android avec **compileSdk 36**
- Émulateur ou appareil physique (**API 24+**)
- Gradle Wrapper du dépôt (`gradlew` / `gradlew.bat`)

### Build app (debug)

```bash
# Linux / macOS
./gradlew :app:assembleDebug

# Windows (PowerShell)
.\gradlew.bat :app:assembleDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`

### Autres commandes

| Action | Commande |
|--------|----------|
| Install debug | `./gradlew :app:installDebug` |
| Tests unitaires | `./gradlew :app:testDebugUnitTest` |
| Tests instrumentés | `./gradlew :app:connectedDebugAndroidTest` |
| Release | `./gradlew :app:assembleRelease` |

Ouvrir la **racine** du dépôt dans Android Studio (pas uniquement `app/`), puis Run sur le module `app`.

### Site vitrine

```bash
cd website
npm install
npm run dev
```

Voir [website/README.md](website/README.md).

---

## Gestes principaux

Résumé ; référence complète : [docs/NAVIGATION_CONTRACT.md](docs/NAVIGATION_CONTRACT.md).

| Geste | Écran | Effet |
|-------|-------|--------|
| Swipe bas | Player | Réduit vers le mini-player (`popBackStack`) |
| Swipe haut | Player | Ouvre la file d'attente (split bandeau) |
| Drag horizontal | Player ↔ Paroles | Peek / commit paroles |
| Swipe bas / droite | Paroles (peek actif) | Retour Player |
| Tap pochette / barre | Mini-player | Ouvre le Player plein écran |

Hints de découverte one-shot : `GestureHintBanner` (DataStore). TalkBack : actions sémantiques (réduire, file, paroles) sans texte décoratif superflu.

---

## Configuration et permissions

Aucune clé API obligatoire pour le fonctionnement de base. Internet sert surtout à LRCLIB et à la WebView de recherche de paroles.

Permissions déclarées (`AndroidManifest.xml`) :

| Permission | Usage |
|------------|--------|
| `READ_MEDIA_AUDIO` | Bibliothèque audio (API 33+) |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Bibliothèque audio (API ≤ 32) |
| `INTERNET` | LRCLIB / WebView paroles |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Lecture arrière-plan |
| `POST_NOTIFICATIONS` | Notification média (API 33+) |

Pas de compte utilisateur ni de télémétrie tierce documentée dans l'app.

---

## Documentation

| Document | Contenu |
|----------|---------|
| [docs/index.md](docs/index.md) | Point d'entrée docs |
| [docs/NAVIGATION_CONTRACT.md](docs/NAVIGATION_CONTRACT.md) | Routes, overlays, gestes, back |
| [docs/ROADMAP_APP_NEXT.md](docs/ROADMAP_APP_NEXT.md) | Statut features & suite |
| [docs/STORAGE_AND_CACHE.md](docs/STORAGE_AND_CACHE.md) | Cache, plafonds, purge |
| [docs/FEATURES_LYRICS_WEB_SEARCH.md](docs/FEATURES_LYRICS_WEB_SEARCH.md) | Recherche paroles WebView |
| [docs/FEATURES_C_SHARED_ELEMENT.md](docs/FEATURES_C_SHARED_ELEMENT.md) | Morph mini-player → Player |
| [docs/UX_MOTION_GUIDELINES.md](docs/UX_MOTION_GUIDELINES.md) | Motion / reduced motion |
| [docs/architecture-app.md](docs/architecture-app.md) | Architecture Android |
| [docs/development-guide-app.md](docs/development-guide-app.md) | Guide dev app |
| [docs/SMOKE_TEST_CHECKLIST.md](docs/SMOKE_TEST_CHECKLIST.md) | Smoke tests |

---

## Roadmap

Suivi vivant dans **[docs/ROADMAP_APP_NEXT.md](docs/ROADMAP_APP_NEXT.md)**.

Exemples de suites documentées (non exhaustif) :

- Finaliser l'UX EQ « pour ce morceau » dans `EqualizerBottomSheet`
- Audit perf listes (`key` / `contentType` LazyColumn)
- Android Auto : recherche browse / smart playlists dans l'arbre si besoin

Desktop Windows (CMP) : voir [docs/desktop/](docs/desktop/) et [desktop/README.md](desktop/README.md).

---

## Contribution

1. Fork / branche depuis `main`
2. Garder le build Android vert : `./gradlew :app:assembleDebug` (et tests unitaires pertinents)
3. Respecter le contrat de navigation et les guidelines motion (`docs/NAVIGATION_CONTRACT.md`, `docs/UX_MOTION_GUIDELINES.md`)
4. Ne pas committer de junk (`build/`, `.kotlin/errors/`, logs locaux, APK, secrets)
5. PR courte : quoi / pourquoi, captures si UI

Issues et PR bienvenues pour bugs, docs et petites améliorations ciblées.

---

## License

Aucun fichier `LICENSE` n'est présent à la racine du dépôt pour l'instant.

En l'absence de licence explicite, le code reste sous le droit d'auteur des contributeurs — **tous droits réservés** jusqu'à publication d'une licence (MIT, Apache-2.0, etc.). Si vous forkez ou réutilisez, vérifiez d'abord avec le mainteneur ou attendez l'ajout d'un `LICENSE`.

---

## Crédits

- Application Android : module `app/` (`com.credo.soundgroove`)
- Site : [soundgroove.app](https://soundgroove.app) — sources dans [`website/`](website/)
- Paroles en ligne : [LRCLIB](https://lrclib.net/) (recherche initiée par l'utilisateur / pipeline app)
- Stack open source : Kotlin, AndroidX, Media3, Coil, Room, etc.

---

## English (short)

**SoundGroove** is a **local-first** Android music player (Kotlin, Jetpack Compose, Media3). It plays files from MediaStore (and optional SAF folders), with synced LRC lyrics (local files, bounded cache, LRCLIB, optional in-app Google WebView paste), immersive player gestures, EQ, themes/accents, home widgets, Android Auto browse, car mode, and local listening stats — no account, no cloud streaming.

- Site: [soundgroove.app](https://soundgroove.app)
- Build: `./gradlew :app:assembleDebug`
- Docs: [`docs/`](docs/) (navigation contract, roadmap, storage, architecture)
- License: **not declared yet** (no `LICENSE` file in the repo)
