# Audit performance SoundGroove (Android) — 2026-08-21

Scope : application musique **`app/`** uniquement. Mesures approximatives (dev machine, pas de labo Perfetto dédié). Objectif : gains **safe / fort impact**, sans conflit avec agents Room / file de commandes player / theme.

## Inventaire assets APK

| Élément | Taille | Verdict |
|--------|--------|---------|
| `res/raw/sg_eq_bars.json` | ~5,6 KB | OK — Lottie léger |
| `res/raw/sg_pulse.riv` | ~0,2 KB | OK — Rive minimal |
| `assets/` | vide | N/A |
| Drawables launcher (`ic_launcher_foreground_asset.png`, etc.) | ~190 KB × densités | Branding — hors downscale runtime |
| Polices | Google Fonts via GMS (`Fonts.kt`) | Pas de TTF embarqués lourds |

**WebSockets** : absents du chemin lecture typique. Remote host LAN éventuel hors scope UI. **N/A** fuites WS.

---

## Findings & correctifs

### 1. Jaquettes / Coil (fort impact)

**Problème** : listes et thumbs décodent souvent la pochette **full-res** → RAM élevée + jank au scroll.

**Fait** :
- `CoilImageConfig` : memory cache ~18 %, disk 48 Mo (`coil_album_art`), `allowRgb565(true)`.
- `SgCoverImage` : `Precision.INEXACT`, `ImageRequest.size()` via `decodeWidth/Height` ou **`decodeEdgeDp`**.
- `AlbumArtThumb` / `ArtistAvatarView` / MiniPlayer : downscale = taille affichée.
- Call sites listes / sheets / détails : `decodeEdgeDp` (44–320 dp selon UI) ; fonds floutés Player/Lyrics déjà à 360×720.
- `AlbumArtPalette` : decode **96×96** pour extraction couleur (3 sites).

Coil décode déjà WebP/HEIF (API 28+) — **pas** de reconversion de la bibliothèque utilisateur.

### 2. Listes longues / cold start UI

- `LazyColumn` déjà virtuel + `key` / `contentType` (Home, Library, Search, Queue, etc.).
- ViewModel charge encore **toute** la bibliothèque en mémoire — **Paging3 non introduit** ici (coordination Room / autre agent).
- Raw Lottie/Rive déjà minuscules ; pas de gros work UI thread ajouté au cold start.
- `SgRive` : `onRelease { pause() }` pour éviter worker/animation orpheline.

### 3. Memory leaks (corrigés)

| Fuite | Correctif |
|-------|-----------|
| `Player.Listener` anonyme sur `MediaController` sans `removeListener` | `mediaControllerListener` + `removeListener` dans `onCleared` avant `releaseFuture` |
| `SgRive` AndroidView sans pause à la destruction | `onRelease { view.pause() }` |
| `LaunchedEffect` polling player (Main/Player) | Annulé avec le scope Composition (OK) |
| Flows ViewModel | `viewModelScope` / `WhileSubscribed` (OK) |

Zone touchée ViewModel : **uniquement** enregistrement/retrait listener MediaController — pas la queue de commandes player.

---

## Axes reportés

1. **Paging3 / chunks Room** — attendre / coordonner l’agent BDD.
2. **Reconvertir pochettes en WebP** — non (runtime Coil suffit).
3. **Shrink R8 / taille APK branding** — hors chantier perf runtime.
4. **Traces Perfetto + Profile Install** — device réel / CI.

---

## Fichiers clés

- `util/CoilImageConfig.kt`, `ui/motion/SgCoverImage.kt`, `ui/components/AlbumArtView.kt`
- `util/AlbumArtPalette.kt`
- Listes / écrans : `SongItem`, `LibraryTab`, `QueueScreen`, `MiniPlayer`, `ProfileTab`, `BottomSheets`, `MainScreen`, `PlaylistDetailScreen`, `AlbumDetailScreen`, `HomeTab`, `PlayerScreen`
- `viewmodel/SoundGrooveViewModel.kt` (`removeListener` only)
- `ui/motion/SgRive.kt`

---

## Vérification build

```bat
.\gradlew.bat :app:compileDebugKotlin
```

(ou `:app:assembleDebug`)
