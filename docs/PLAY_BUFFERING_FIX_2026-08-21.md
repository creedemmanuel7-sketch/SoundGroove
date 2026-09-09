# Play / buffering UI fix — 2026-08-21

## Symptôme

Tap play → spinner 10–15 s, barre `0:00 / 0:00`, puis audio, spinner qui traîne encore.

## Cause racine

1. **`syncPlaybackUiFlags`** traitait `STATE_READY` et `STATE_IDLE` comme buffering dès que `playWhenReady && !isPlaying` (attente focus / prepare). Le spinner UI n’était donc **pas** lié au vrai `STATE_BUFFERING` ExoPlayer et pouvait rester jusqu’à `isPlaying=true` (souvent plusieurs secondes).
2. **`setMediaItems` direct (≤64)** sur le binder MediaController pouvait saturer 5–15 s avant le premier rendu, tout en gardant ce faux buffering.
3. Progression polled à **1 s** / PlayerScreen **500 ms** sans fallback `song.duration` → `0:00 / 0:00` tant que `Player.duration` est `TIME_UNSET`.

Les buffers courts Media3 / `isBuffering` « honnête » précédents **amplifiaient** le problème UI au lieu de le masquer.

## Correctifs

| Fix | Détail |
|-----|--------|
| Buffering UI | Spinner **uniquement** si `STATE_BUFFERING` + timeout **2,5 s** puis fallback icône |
| Fast-start toujours | Mono-piste `setMediaItem` + expand file async (gapless conservé) |
| Progression | Tick 200–250 ms en play/pending ; `playbackDuration` + fallback métadonnées Song |
| Connexion | `isControllerConnecting` distinct (« Connexion… ») vs buffer piste |
| Logs `SG_AUDIO` | `play_tap`, `bindReady`, `prepare`, `state=…`, `isBuffering=…`, `firstNonZeroPosition`, `play_latency_ms` |
| LoadControl | `bufferForPlaybackMs` **150** (local) |

## Non régressions

- Gapless : file reconstruite via `expandQueueAroundCurrent`
- Focus / noisy / session restore : inchangés (`playWhenReady=false` au restore)
- Pas de faux `isPlaying` avant rendu audio

## Vérif

```text
adb logcat -s SG_AUDIO
.\gradlew.bat assembleDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`
