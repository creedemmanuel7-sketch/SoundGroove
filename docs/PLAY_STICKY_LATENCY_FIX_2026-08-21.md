# Play sticky + latence audio réelle — 2026-08-21

## Symptômes (après fix buffering 2,5 s)

1. Spinner disparaît ~2,5 s **mais le son démarre encore tard** (latence audio réelle).
2. Tap / skip vers **B** : UI / identité restent coincées sur **A** (sticky previous track).

## Causes racines

| # | Cause | Effet |
|---|--------|--------|
| 1 | `expandQueueAroundCurrent` tenait `PlayerCommandGate.withLock` pendant `addMediaItems` de **toute** la file (souvent toute la lib) | Le `playSongs` suivant attendait le mutex → `setMediaItem(B)` / `play()` retardés ; `_currentSong = B` était **dans** le bloc exclusive → UI sticky sur A |
| 2 | `CrossfadeController` mutait (`volume = 0` + fade-in) aussi sur `MEDIA_ITEM_TRANSITION_REASON_SEEK` | READY / `playWhenReady` OK mais **silence** jusqu’à fin du fade (surtout si gapless off ou crossfade > 0) |
| 3 | Pas de **generation / requestId** : callbacks `onMediaItemTransition` / restore session d’un play A lent pouvaient réappliquer A après tap B | Sticky titre / art / progress |
| 4 | `tryRestorePlaybackSession` pouvait `setMediaItem` + `pause()` en course avec un play utilisateur | Écrase le fast-start / coupe `playWhenReady` |
| 5 | Expand plein-binder + pas de pré-warm process | Latence cold-start restante avant premier audio |

## Correctifs

### A — Latence audio réelle

- Fast-start inchangé : `setMediaItem` mono-piste → `prepare()` → `play()` + `ensureAudibleVolume`.
- Expand **fenêtré** (`±32`) + `tryWithLock` (n’bloque plus le gate si busy).
- Crossfade / micro-pause gapless : fade **uniquement** sur transition `AUTO` ; SEEK / playlist replace → `resetVolume()`.
- Pré-warm `PlaybackService` dès `SoundGrooveApplication.onCreate` (+ `MainActivity` existant).
- Play tapé avant bind : **pending request** flushée à la connexion (plus d’abort silencieux).
- Session restore **abandonnée** si `playGeneration > 0` / pending user play.
- Logs `SG_AUDIO` : `play_tap` → `bindReady` → `setMediaItem` → `prepare` → `playIssued` → `state=READY` → `isPlaying` → `firstNonZeroPosition` (+ `stale_ignored`).

### B — Sticky previous song

- **Optimistic UI** immédiat dans `playSongs` / skip / seek-index (`currentSong`, durée, index, `pendingPlayMediaId`) **avant** le mutex.
- `playGeneration` : exclusive / expand / restore / media-transition d’une gen périmée → **ignorés**.
- `updateCurrentSongFromMediaItem` refuse d’appliquer un mediaId ≠ `pendingPlayMediaId`.

## Fichiers touchés

- `viewmodel/SoundGrooveViewModel.kt`
- `util/PlayerCommandGate.kt`
- `util/PlayLatencyTracker.kt`
- `util/CrossfadeController.kt`
- `SoundGrooveApplication.kt`

Non touchés (autre agent) : `HomeTab`, playlist picker Accueil.

## Vérif

```text
adb logcat -s SG_AUDIO
.\gradlew.bat :app:assembleDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`

Scénarios manuels :

1. Cold start → tap piste → audio rapide ; log `play_latency_ms` + `firstNonZeroPosition`.
2. Pendant un play lent A, tap B → titre/art B immédiat ; A ne revient pas.
3. Skip next ×N → identité suit la cible ; pas de retour à l’ancien titre.
