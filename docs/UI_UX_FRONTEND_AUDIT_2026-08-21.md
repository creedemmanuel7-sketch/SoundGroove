# Audit UI/UX frontend — SoundGroove (2026-08-21)

Scope : thème Compose, écrans shell (Home / Bibliothèque / Player / Paroles / MiniPlayer), motion/blur, landing `website/`. Hors scope volontaire : logique playback `SoundGrooveViewModel`, migrations Room.

## Findings

| Zone | Problème | Sévérité | Correctif |
|------|----------|----------|-----------|
| Contraste sombre | `TextSecondary` / `TextTertiary` sous AA approx. sur noir (`#9A9CA3`, `#68696F`) | Haute | Tokens remontés dans `Theme.kt` / défauts `Color.kt` |
| Tablette / large | Contenu plein écran sans plafond de largeur ; albums figés à 2 colonnes | Haute | `Adaptive.kt` + application Home / Library / Player / Lyrics / MiniPlayer |
| Blur runtime | Paroles à **46dp** blur + decode 480×960 ; coût élevé pendant scroll | Haute | `rememberSgAllowBlur` + `sgSoftBlur` ; 14dp / decode réduit ; off pendant fling |
| Player blur | 20dp permanent hors Mode perf | Moyenne | 12dp + decode 360×720 + même gate |
| Touch targets | `SgIconButton` à 40dp | Moyenne | 48dp (`SgSpacing.hitTarget`) |
| a11y contrôles | Shuffle EN, Repeat générique, icône Tune sans label | Moyenne | `contentDescription` FR + états |
| Imports morts | `.blur` importé sans usage (BottomNav, SongItem, etc.) | Basse | Nettoyés |
| Website | Pas de `:focus-visible` ; `--muted` faible ; CTA étroit mobile ; scroll smooth ignore reduced-motion | Moyenne | `globals.css` + `DownloadButton` |

## Correctifs livrés

### Android Compose

- `app/.../ui/theme/Adaptive.kt` — `SgWidthSizeClass`, `sgScreenHorizontal()`, `sgAlbumGridColumns()`, `Modifier.sgConstrainWidth`
- `Theme.kt` / `Color.kt` — contrastes secondaires / tertiaires (Noir, Graphite, Clair)
- `Motion.kt` — `rememberSgAllowBlur`, `Modifier.sgSoftBlur`
- `Design.kt` — `SgIconButton` ≥ 48dp
- `LibraryTab.kt` — largeur max 720dp, gutters adaptatifs, grille albums 2/3/4
- `HomeTab.kt` — même contrainte largeur + gutters
- `PlayerScreen.kt` / `LyricsScreen.kt` — chrome max 520dp, blur conditionnel
- `MiniPlayer.kt` — max 560dp centré
- Imports blur inutilisés retirés

### Website

- `website/app/globals.css` — `--muted` plus clair, focus ring violet, `overflow-x: hidden`, breakpoints + CTA full-width tablette/mobile, `prefers-reduced-motion` (scroll + transitions CTA)
- `website/components/DownloadButton.tsx` — wrapper `.cta-wrap` pour alignement responsive

## Notes perf (60 FPS)

1. **Blur** : coût RenderEffect ; toujours sous-échantillonner Coil (`decodeWidth/Height`) avant flou ; rayon ≤ 14dp ; **désactiver** si `rememberSgReducedMotion()` (système OU Mode performance) ou scroll/fling paroles.
2. **Préférer** `graphicsLayer` (alpha / translation / scale) pour micro-interactions — déjà la norme via `SgMotion` / `sgPressScale`.
3. **Glass** (`Glass.kt`) : brush + bordure, **pas** de blur runtime — conserver ce modèle pour chrome listes.
4. **Lottie / Rive** : déjà gated par `rememberSgReducedMotion` (`SgLottie`, `SgRive`, `NowPlayingIndicator`).
5. Sur tablette, plafonner la largeur réduit aussi le fill-rate des gradients / shared elements.

## Chemins clés

```
app/src/main/java/com/credo/soundgroove/ui/theme/Adaptive.kt
app/src/main/java/com/credo/soundgroove/ui/theme/Theme.kt
app/src/main/java/com/credo/soundgroove/ui/theme/Color.kt
app/src/main/java/com/credo/soundgroove/ui/theme/Motion.kt
app/src/main/java/com/credo/soundgroove/ui/theme/Design.kt
app/src/main/java/com/credo/soundgroove/ui/screens/{HomeTab,LibraryTab,PlayerScreen,LyricsScreen}.kt
app/src/main/java/com/credo/soundgroove/ui/components/MiniPlayer.kt
website/app/globals.css
website/components/DownloadButton.tsx
docs/UI_UX_FRONTEND_AUDIT_2026-08-21.md
```

## Vérification build

- Android : `./gradlew :app:compileDebugKotlin` (ou `assembleDebug`) — à lancer localement si le sandbox agent ne permet pas Gradle.
- Website : `cd website && npm run build` si Node dispo.

## Suite possible (non faite)

- Appliquer `sgConstrainWidth` à Recherche / Settings / détails album.
- Pré-générer bitmaps ambiance (Palette) pour supprimer tout blur runtime.
- Audit TalkBack exhaustif des bottom sheets.
