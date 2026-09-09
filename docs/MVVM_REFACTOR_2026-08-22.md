# Refactor MVVM SoundGroove — 2026-08-22

## Contexte

`SoundGrooveViewModel` (~2000 lignes) cumulait bibliothèque, lecture Media3, playlists, remote LAN, EQ, thème et stats. C'était le « God ViewModel » identifié dans l'audit produit du 2026-08-21.

## Architecture cible (incremental)

```
SoundGrooveViewModel (orchestrateur ~650 lignes)
├── LibraryManager      — scan, favoris, playlists, métadonnées
├── PlaybackManager     — MediaController, file, play/skip/seek
├── LyricsViewModel     — écran paroles (déjà isolé)
├── HomeViewModel       — heuristiques Accueil (déjà isolé)
└── SearchViewModel     — recherche (déjà isolé)
```

## Fichiers extraits

| Fichier | Responsabilité |
|---------|----------------|
| `library/LibraryManager.kt` | Chansons, dossiers masqués, favoris, playlists smart/manuelles, overrides métadonnées, scan SAF/MediaStore |
| `playback/PlaybackManager.kt` | Bind MediaController, queue UI, fast-start mono-piste, expand gapless, session restore, erreurs lecture |
| `viewmodel/SoundGrooveViewModel.kt` | Thème, accents, EQ prefs, sleep timer, remote LAN, backup, délégation publique inchangée pour l'UI |

## Ce qui n'a PAS bougé (volontairement)

- **PlaybackService** / ExoPlayer engine — reste côté service Android
- **Repositories Room** (`DatabaseRepository`, `CombinedMusicRepository`) — déjà présents
- **API publique ViewModel** — `AppNavigation` et écrans continuent d'injecter un seul `SoundGrooveViewModel`
- **LyricsViewModel** — déjà conforme MVVM (position poussée depuis l'UI)

## Prochaines étapes suggérées

1. `SettingsViewModel` — thème, EQ, perf mode, notifications
2. `RemoteHostController` — WebSocket LAN
3. Tests unitaires ciblés sur `PlaybackManager` (queue ops, session restore)
4. Modularisation Gradle seulement si l'équipe grossit (pas prématuré)

## Impact UI (session)

- File d'attente : panneau unifié type Rivage (liste inline, long-press reorder)
- Player : pochette agrandie (82 % → 94 % largeur)
- Paroles : boutons modifier/supprimer compacts dans l'en-tête
