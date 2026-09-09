# Player engine audit — 2026-08-21

Audit / renfort du pipeline de lecture locale Media3 (ExoPlayer + MediaSession),
sans casser les gains du 2026-08-07 (`AUDIO_UI_FIXES` : buffers courts, fast-start,
`isBuffering` honnête).

## Problèmes trouvés

### Races / état UI
- `skipNext` / `skipPrevious` / play-pause mutaient le `MediaController` sans
  sérialisation → clics rapides (next×10) pouvaient empiler des seeks et laisser
  l’UI / l’index de file désynchronisés.
- Player plein écran appelait `PlayerGuards.safeSeek*` **directement** sur le
  player, en parallèle du ViewModel (mini-player / Auto / widget).
- Shuffle / repeat : état Compose **local** dans `PlayerScreen`, pas aligné sur
  ExoPlayer (`shuffleModeEnabled` / `repeatMode`) → désync après widget / Auto /
  rotation.
- Pas de message clair si `MediaController` pas encore bound.

### Buffer / préchargement
- `bufferForPlaybackMs = 350` conservé (first play rapide).
- `minBuffer` / `maxBuffer` un peu bas pour enchaîner confortablement la piste
  suivante déjà présente dans la timeline Media3.
- Fast-start (>64 items) déjà bon ; l’expand async devait rester sous le même
  verrou que les skips pour ne pas corrompre la file.

### Erreurs
- `onPlayerError` loggé côté service seulement → pas d’état UI, pas de retry,
  pas de skip automatique.
- Crossfade pouvait laisser `volume = 0` après une erreur mid-fade.

## Correctifs

### 1. File de commandes (`PlayerCommandGate`)
- Mutex Main + channel pour play/pause, shuffle, repeat, seek.
- **Coalesce** next/prev : fenêtre 45 ms → un seul `applySkipDelta(n)`
  (next×10 = seek net de +10, pas 10 transitions).
- Guards si controller null ; message « Lecteur non prêt ».

### 2. État UI = ExoPlayer
- `isPlaying` / `isBuffering` inchangés (déjà dérivés de `isPlaying` +
  `playWhenReady` + `playbackState`).
- Nouveaux `shuffleEnabled` / `repeatMode` synchronisés via listeners Media3.
- `PlayerScreen` ne mute plus le player en local : callbacks ViewModel.

### 3. Buffer / next track
- LoadControl : `min=20s`, `max=60s`, `bufferForPlayback=350`, after-rebuffer=`800`.
- File Media3 + expand post fast-start inchangés (piste suivante dans la timeline).
- Crossfade : reset volume sur `onPlayerError` + try/catch tick.

### 4. Erreurs de lecture
- `playbackError: StateFlow<String?>` → Toast (AppNavigation) / Snackbar (Legacy).
- Messages par code (`FILE_NOT_FOUND`, permission, codec, réseau…).
- Retry limité (1) puis skip suivant ; stop après 5 auto-skips consécutifs.
- MediaIds en échec mémorisés (`invalidMediaIds`).

## Fichiers touchés

| Fichier | Rôle |
|---------|------|
| `util/PlayerCommandGate.kt` | **nouveau** — sérialisation + coalesce |
| `util/PlayerGuards.kt` | skip delta, shuffle/repeat, messages erreur |
| `util/CrossfadeController.kt` | volume sûr sur erreur |
| `PlaybackService.kt` | loadControl + ensureAudible sur erreur |
| `viewmodel/SoundGrooveViewModel.kt` | gate, erreurs, shuffle/repeat |
| `ui/screens/PlayerScreen.kt` | callbacks ViewModel |
| `ui/navigation/AppNavigation.kt` | wire + Toast erreur |
| `ui/navigation/LegacyMainHost.kt` | Snackbar erreur |

## Scénario mental : next×10
1. Dix `enqueue(SkipNext)` en <45 ms → `pendingSkipDelta = 10`.
2. Flush unique → `applySkipDelta(+10)` → un `seekToDefaultPosition`.
3. Mutex empêche un `playSongs` concurrent de `setMediaItems` pendant le seek.
4. UI : `onMediaItemTransition` met à jour `currentSong` / index une fois.

## Risques restants
- Cold-start MediaController (~3–4 s émulateur) inchangé.
- Crossfade mono-player = fondu volume, pas vrai overlap de deux décodeurs.
- `invalidMediaIds` non persisté (session mémoire seulement).
- Widget / Auto mutent encore le player hors gate (listeners resync UI).

## Build
- Cible : `assembleDebug` ou `compileDebugKotlin`.
- APK attendu : `app/build/outputs/apk/debug/app-debug.apk`.
