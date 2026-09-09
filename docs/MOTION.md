# Motion — Compose, Lottie, Rive

## Hiérarchie

1. **Jetpack Compose Animation + `SgMotion`** — base de toute la motion UI
   (transitions nav, Player morph, press scale, sheets, reduced motion).
2. **Lottie (`SgLottie`)** — accents vectoriels légers (ex. barres now-playing
   `res/raw/sg_eq_bars.json` via `NowPlayingBars`).
3. **Rive (`SgRive` / `SgRiveAccent`)** — accents optionnels. Wrapper prêt ;
   asset minimal `res/raw/sg_pulse.riv` (généré via `tools/generate_sg_pulse_riv.mjs`) ;
   absent / illisible = no-op / fallback (pas de crash).

## Reduced motion

`rememberSgReducedMotion()` (échelle système Animator = 0 **ou** Mode perf) :

| Couche | Comportement |
|--------|----------------|
| Compose / `SgMotion` | `snap()`, pas de springs / loops |
| Coil / `SgCoverImage` | crossfade 0 ms ; pas de Crossfade URI |
| Lottie / `SgLottie` | progress figé (`reducedMotionProgress`), pas de loop |
| Rive / `SgRive` | pause, `Loop.ONESHOT` |

## Pochette — anti « jump »

`SgCoverImage` combine Crossfade Compose (URI, ~MediumMs) + Coil crossfade court
(≤ FastMs). Utilisé sur AlbumArt / mini / Player **et** listes majeures
(Queue, Library, Home, Search via AlbumArt*, détails, sheets, paroles…).
Les shared elements (`sgSharedAlbumArt` / `sgSharedBounds`) restent inchangés :
on adoucit le bitmap **sous** le morph. Listes : `uriCrossfade = false`
(Coil only) ; heroes / now-playing : `uriCrossfade = true`.

## Rive — `sg_pulse.riv`

Asset embarqué : `app/src/main/res/raw/sg_pulse.riv` (artboard `Pulse`, anim `pulse`).
Régénérer / remplacer :

1. Exporter depuis [Rive Editor](https://rive.app), **ou**
2. Node (depuis la racine du repo) :

```bash
npm install @stevysmith/rive-generator --prefix tools
node tools/generate_sg_pulse_riv.mjs
```

Branche active : mini-player (`SgRiveAccent`) overlay léger sur la pochette en
lecture — si le raw manque, rien ne s’affiche (pas de crash).

## Dépendances

- `com.airbnb.android:lottie-compose` (catalog `lottie`)
- `app.rive:rive-android` (catalog `rive`) + Jetpack Startup `RiveInitializer`

Voir aussi `docs/UX_MOTION_GUIDELINES.md` pour les tokens M3 / SgMotion.
