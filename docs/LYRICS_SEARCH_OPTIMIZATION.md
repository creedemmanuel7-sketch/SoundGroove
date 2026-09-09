# Optimisation recherche paroles SoundGroove — référence Rivage

Document pour le port Rivage (agent sibling). SoundGroove reste la source de vérité.

## Pipeline en cascade (offline-first)

1. **Cache app** — `LyricsCacheStore` (`filesDir/lyrics/<songId>.txt`, LRU)
2. **Fichier voisin** — `.lrc` / `.txt` même dossier que l'audio (`LyricsFileResolver`)
3. **LRCLIB** — uniquement si local incomplet (`LyricsRepository.fetchOnlineLyrics`)
4. **Recherche Google WebView** — manuelle, filet de secours (`LyricsWebSearchScreen`)

## Pourquoi c'est rapide

| Mécanisme | Fichier | Effet |
|-----------|---------|-------|
| Cache hit immédiat | `LyricsViewModel.loadLyricsForSong` | Skip réseau si `loadedSongId` + contenu complet |
| IO hors Main | `Dispatchers.IO` dans ViewModel + Repository | UI jamais bloquée |
| Annulation job | `loadJob?.cancel()` au changement de titre | Pas de course stale |
| Pas de re-fetch auto après delete | `deleteLyrics` → `NotFound` sans relance LRCLIB | Évite spam réseau |
| `LyricsAvailability.revision` | Flow Room-like pour playlist « Avec paroles » | Invalidation ciblée, pas de rescan complet |
| LRCLIB cache write-through | Succès online → cache + fichier voisin | Second accès instantané |

## LRCLIB (détail)

- Client : `LrcLibClient.fetchLyrics(song)`
- Préfère `syncedLyrics` (LRC) → `LyricsContent.Synced`
- Fallback texte brut → `PlainText` si ≥ 8 caractères (`MIN_PLAIN_TEXT_LENGTH`)
- **Pas de clé API** — API publique LRCLIB

## Sync temps réel (LRC)

- `LyricsViewModel.updatePlaybackPosition(positionMs)` — appelé depuis UI, pas depuis God VM
- Offset utilisateur : `LyricsPreferences.syncOffsetMs` (défaut −200 ms)
- Ligne active : `indexOfLast { timeMs <= adjustedPosition }`

## À porter tel quel sur Rivage

1. Même ordre cache → local → LRCLIB → WebView manuel
2. ViewModel paroles isolé avec position poussée depuis le player
3. `loadJob` cancel + guard `loadedSongId`
4. Ne jamais scraper Google — copier-coller utilisateur seulement

## Fichiers clés SoundGroove

- `lyrics/LyricsRepository.kt`
- `lyrics/LyricsViewModel.kt`
- `lyrics/LyricsCacheStore.kt`
- `lyrics/LyricsFileResolver.kt`
- `lyrics/LrcLibClient.kt` (si présent)
- `ui/screens/LyricsWebSearchScreen.kt`
- `docs/FEATURES_LYRICS_WEB_SEARCH.md`
