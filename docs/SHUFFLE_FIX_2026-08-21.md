# Shuffle / repeat fix — 2026-08-21

## Cause

1. **Skip ignorait le shuffle** : `PlayerGuards.applySkipDelta` faisait `current + delta` (index linéaire). Même avec `shuffleModeEnabled = true`, next/prev (gate, mini-player, Car Mode) restaient séquentiels. Media3 shuffle ne s’applique qu’via `Timeline.getNextWindowIndex` / `seekToNextMediaItem`.
2. **UI** : état local Compose (HEAD) ou sync ViewModel fragile (`safeToggleShuffle` exigeait `mediaItemCount > 0`, relecture immédiate du getter MediaController parfois encore à `false`).
3. **Persistance** : absente — restart service = shuffle OFF.

## Correctif

| Zone | Changement |
|------|------------|
| `PlayerGuards.applySkipDelta` | Marche la timeline avec `shuffleModeEnabled` (coalesce next×N OK) |
| `safeToggleShuffle` | Retourne `Boolean?` (nouvel état) ; plus de garde `mediaItemCount` |
| `SoundGrooveViewModel` | Toggle / cycle via `PlayerCommandGate` ; UI optimistic + prefs |
| `PlayerScreen` | Tint actif = accent ; inactif = `TextPrimary` ; pastille quand ON |
| `PlaybackPreferences` + `PlaybackService` | `KEY_SHUFFLE_ENABLED` / `KEY_REPEAT_MODE` |

## Hors scope

- MiniPlayer / CarMode : pas de bouton shuffle (skip déjà via gate → corrigé).
- Zone `isBuffering` / play spinner : non touchée.
