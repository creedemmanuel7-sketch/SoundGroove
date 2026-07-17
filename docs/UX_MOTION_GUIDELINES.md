# UX & Motion Guidelines — SoundGroove

Document de référence pour le **design system** et la **motion** de l'app.
Rédigé par l'AGENT DESIGN SYSTEM + MOTION à partir d'une extraction de sources
externes (Laws of UX, NN/g, Material 3, Apple HIG, Android Compose, études de
cas Apple Music / Spotify) et de l'état réel du code au moment de la rédaction.

Portée respectée : `ui/theme/Motion.kt`, `ui/theme/Design.kt`,
`ui/components/MiniPlayer.kt`, `ui/screens/PlayerScreen.kt`,
`ui/navigation/AppNavigation.kt`. Aucune modification sur Home / Search /
Profile (autres agents), ni sur `MainActivity.kt`.

---

## 1. Insights extraits des sources

### 1.1 Laws of UX (lawsofux.com)

| Loi | Principe | Application SoundGroove |
|---|---|---|
| **Fitts's Law** | Le temps pour atteindre une cible dépend de sa taille et de la distance à parcourir. | `SgTapTarget` (Design.kt) impose 48dp mini sur les icônes de contrôle petites (mini-player, header Player, shuffle/repeat). Corrigé cette passe : `PlayerActionButton` (Favori/File/Vitesse/Infos/Paroles) n'atteignait pas 48dp en largeur → `defaultMinSize(48dp, 48dp)` ajouté. |
| **Hick's Law** | Le temps de décision augmente avec le nombre/complexité des choix. | Le menu d'options du Player (⋮) ne liste que les actions **absentes** de la rangée de boutons du bas, pour ne pas dupliquer les choix. Rangée de contrôles principale limitée à 5 actions groupées visuellement (icône + label court). |
| **Jakob's Law** | Les utilisateurs transposent les habitudes des autres apps. | Pattern mini-player bas d'écran → plein écran au tap (Spotify/Apple Music/YouTube Music). Un seul lecteur visible à la fois (mini OU plein écran, jamais les deux — ajouté en commentaire dans `AppNavigation.kt`). Swipe horizontal sur pochette = piste suivante/précédente ; swipe vertical = file d'attente. Bottom nav 4 onglets. |
| **Aesthetic-Usability Effect** | Un design perçu comme beau est perçu comme plus utilisable. | Glassmorphism (`GlassCard`, `GlassBorder`), halos radiaux de couleur d'accent, couleur dynamique extraite de la pochette (Palette API) — cohérence esthétique déjà en place. |
| **Doherty Threshold** | Productivité optimale si le système répond en < 400ms. | Durées `SgMotion` toutes ≤ 350ms (SlowMs). Polling position lecture à 500ms lissé par `tweenProgress` pour éviter la sensation de retard perçu. |
| **Miller's Law / Chunking** | ~7 éléments max en mémoire de travail ; grouper l'info. | Rangée d'actions du Player groupée par paires icône+label plutôt que texte seul ; menu ⋮ à 4 entrées max. |

### 1.2 Nielsen Norman Group (nngroup.com/articles)

- **10 heuristiques de Nielsen** (visibilité de l'état système, contrôle utilisateur, cohérence, prévention d'erreur, reconnaissance > mémorisation, feedback...) : la barre de progression, l'état play/pause et l'accent couleur dynamique donnent un feedback visuel continu de "l'état système".
- **Liquid Glass is cracked, usability suffers in iOS 26** (NN/g, oct. 2025) : avertissement que la transparence/le glassmorphism ne doit **jamais nuire à la lisibilité** du contenu — retenu comme garde-fou : le `GlassCard` du titre garde un fond suffisamment opaque (`SurfaceOverlay`/`CardSurface`), pas de glass sur le texte lui-même.
- **UI Elements glossary** : vocabulaire de référence pour rester cohérent dans les futurs composants (sheet, chip, slider...).

### 1.3 Material Design 3 — Motion (m3.material.io/styles/motion)

- **4 principes du motion Material** (design.google/library/making-motion-meaningful) : le motion doit *guider*, pas seulement décorer ; il doit sembler naturel (accélération/décélération, jamais linéaire sauf cas type barre de progression) ; il communique symboliquement le changement d'état.
- **Tokens Easing & Duration** : courbes "Standard" (symétriques, micro-interactions) vs "Emphasized" (entrée decelerate marquée / sortie accelerate franche, écrans pleine page) ; durées Short (~150ms), Medium (~250ms), Long/Medium3 (~350ms pour les transitions "emphasized").
  → **Déjà repris fidèlement dans `SgMotion`** (constantes `FastMs/MediumMs/SlowMs`, courbes `EmphasizedDecelerate/EmphasizedAccelerate/Standard*`).

### 1.4 Apple Human Interface Guidelines — Motion

- « **Add motion purposefully, supporting the experience without overshadowing it.** Don't add motion for the sake of adding motion. »
- « **Aim for brevity and precision** in feedback animations » — animations courtes et précises perçues comme plus légères qu'une animation prononcée.
- « **Make motion optional** » : toujours respecter le réglage d'accessibilité de réduction des animations.
- Repère chiffré : animations système typiquement **250–400ms**, au-delà l'utilisateur perçoit un ralentissement ("laggy").
  → **Nouveau cette passe** : `rememberSgReducedMotion()` dans `Motion.kt`, branché sur le morph d'ouverture du Player (voir §3).

### 1.5 Android Compose Animation (developer.android.com, androidx releases, skydoves/compose-animations)

- Le *quick guide* Compose distingue `animate*AsState` (valeur unique réactive), `AnimatedVisibility`/`AnimatedContent` (apparition/disparition/changement de contenu) et les APIs bas niveau (`Animatable`, `Transition`) pour des séquences orchestrées comme le morph pochette → chrome.
- `compose-animations` (skydoves) illustre de bonnes pratiques de `graphicsLayer` + `spring()` pour des interactions de press/scale légères — pattern déjà utilisé dans `sgPressScale`.

### 1.6 Études de cas Apple Music / Spotify

- **Kodeco — Recreating the Apple Music Now Playing transition** : la technique de référence n'est **pas** un vrai shared element position-matché entre deux `UIViewController`, mais un **snapshot figé** de l'écran d'origine posé en arrière-plan + une pochette qui **grandit en continu** vers sa taille finale, pendant que le reste du chrome apparaît en fondu juste après. C'est exactement le principe déjà implémenté dans `PlayerScreen` (`artScale` 0.9→1 + `chromeAlpha` retardé) : le "morph" perçu vient du grossissement de la pochette, pas d'un vrai déplacement pixel-perfect d'une vue à l'autre.
- **60fps.design/apps/spotify**, **Dribbble "Music" collection**, **Tubik Studio "Echo" case study** : convergent sur les mêmes patterns visuels pour une now-playing screen musicale — fond immersif dérivé de la pochette (flou/dégradé), contrôles centrés, actions secondaires en second plan visuel. Confirme les choix déjà en place (fond flouté + dégradé d'accent, hiérarchie visuelle pochette > titre > contrôles > actions).
- **Fil Spotify Community "Modernize Spotify's Design"** : les utilisateurs demandent explicitement plus de fluidité et moins de à-coups dans les transitions — argument pour garder les durées courtes (Doherty Threshold) plutôt que des animations longues et spectaculaires.

---

## 2. État du design system Motion (au moment de cette passe)

`ui/theme/Motion.kt` (`SgMotion`) était déjà largement aligné M3 avant cette
session (durées, courbes, transitions de nav, transition Player, overlays,
tab content) — cf. commentaires en tête de fichier référençant directement les
pages M3 consultées. Cette passe **complète** plutôt que ne réécrit :

- Durées : `FastMs=150` (M3 Short3), `MediumMs=250` (M3 Medium1), `SlowMs=350` (M3 Medium3), `ProgressMs=500` (calé sur le polling ExoPlayer).
- Courbes : `EmphasizedDecelerate`/`EmphasizedAccelerate` (cubic-bezier M3 officielles) pour les transitions "à fort enjeu visuel" (nav, Player), `Standard*` pour les micro-interactions symétriques.
- Transition Player : morph pochette (grandissement) + chrome en fondu retardé, inspiré Kodeco/Apple Music, **sans** shared element (voir §4).

---

## 3. Changements appliqués cette passe

1. **`Motion.kt`** — ajout de `rememberSgReducedMotion()` : détecte le réglage
   système "Suppression des animations" (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`),
   équivalent Android du "Reduce Motion" des Apple HIG.
2. **`PlayerScreen.kt`** :
   - Le morph d'ouverture (`artScale`/`chromeAlpha`) respecte désormais
     `rememberSgReducedMotion()` : si activé, la pochette et le chrome
     apparaissent directement dans leur état final (`snapTo`), sans animation.
   - `PlayerActionButton` (Favori / File / Vitesse / Infos / Paroles) : cible
     tactile portée à 48×48dp minimum (`defaultMinSize`) — elle était
     légèrement sous ce seuil en largeur (Fitts's Law).
   - Crossfade des `AsyncImage` (fond flouté + pochette) recalé sur le token
     `SgMotion.MediumMs` au lieu du défaut Coil (200ms non documenté), pour que
     le fond et la pochette soient perçus comme **un seul** morph cohérent.
3. **`MiniPlayer.kt`** : crossfade de la miniature aligné sur `SgMotion.FastMs`
   (cohérence avec le token M3 "Short" pour une micro-surface).
4. **`AppNavigation.kt`** : commentaire explicitant la règle Jakob's Law déjà
   respectée par le code (mini-player masqué sur la route Player — jamais deux
   surfaces "now playing" visibles en même temps).

Aucun changement de comportement visible pour un utilisateur sans réglage
d'accessibilité particulier ; les seuls deltas visuels sont la durée de
crossfade des pochettes (légèrement plus longue, 250ms vs ~200ms) et la zone
cliquable élargie des boutons d'action du Player.

---

## 4. Fond dynamique de la pochette — performance

Le fond immersif du Player (`PlayerScreen`) utilise déjà la bonne pratique
perf : l'image source est **sous-échantillonnée avant le flou**
(`.size(480, 960)` sur l'`ImageRequest`), car un `Modifier.blur()` coûte
beaucoup plus cher sur une image en pleine résolution — surtout en fallback
logiciel sous Android < 12 (API 31), où `blur()` n'a pas d'accélération
`RenderEffect` matérielle. La couleur dominante extraite via Palette API
(`rememberAlbumArtAccentColor`, `util/AlbumArtPalette.kt`) porte l'essentiel de
l'ambiance visuelle (dégradé `displayAccent`), le flou n'étant qu'un appoint.
Il n'y a **aucune animation par frame** sur ce fond (pas de blur animé, pas de
parallax) : le seul mouvement est le crossfade Coil au changement de morceau,
désormais calé sur `SgMotion.MediumMs` (§3). Aucun risque de lag identifié à
date sur ce point ; à surveiller si un futur agent ajoute un effet Ken
Burns / parallax sur ce fond (coûteux en recomposition continue).

---

## 5. Shared element mini-player → Player (livré)

**Statut à jour** : le vrai `SharedTransitionLayout` / `Modifier.sharedElement`
est livré — voir `docs/FEATURES_C_SHARED_ELEMENT.md` (clés `album_art_<id>` +
`track_meta_<id>`). Le morph manuel de `PlayerScreen` ne s'active qu'en fallback
hors contexte shared.

`rememberSgReducedMotion()` OR **Mode performance** (prefs) désactive le morph
shared (snap), le blur immersif, la rotation vinyle, ThemeReveal, les slides
nav/tabs, `sgPressScale`, et plafonne les crossfades Coil à 0 ms. Source de
vérité motion : `ui/theme/Motion.kt` + plan canvas motion.

---

## 6. Build

`./gradlew assembleDebug` — cf. section livrable de la réponse pour le
statut réel de cette session (l'exécution shell locale a été indisponible par
intermittence pendant cette passe).

---

## 7. Références (sources prioritaires)

| Source | Lien | Principe appliqué |
|---|---|---|
| **M3 Motion — how it works** | https://m3.material.io/styles/motion/overview/how-it-works | Motion guide l’attention ; enter decelerate / exit accelerate ; un porteur de mouvement dominant. |
| **M3 Easing & duration** | https://m3.material.io/styles/motion/easing-and-duration | Tokens Short/Medium (~150/250/350 ms) → `FastMs` / `MediumMs` / `SlowMs` ; courbes Emphasized + Standard dans `SgMotion`. |
| **NN/g — Animation duration** | https://www.nngroup.com/articles/animation-duration/ | UI ≤ ~100–400 ms (plafond pratique **≤ 350 ms** / `SlowMs`) ; ease-out à l’entrée, ease-in à la sortie ; respecter reduce motion. |
| **Compose shared elements** | https://developer.android.com/develop/ui/compose/animation/shared-elements | `SharedTransitionLayout` + `sharedElement` / `sharedBounds` ; Customize : `boundsTransform` + `OverlayClip`. |
| **Navigation + shared elements** | https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation | Scopes via NavHost / CompositionLocal ; fade-only où le morph shared porte le mouvement. |

### Principes appliqués (checklist)

1. **Durées uniquement via tokens `SgMotion`** — pas de ms inventés hors `FastMs` (150), `MediumMs` (250), `SlowMs` (350), `PlayerMorphMs` (220), `ProgressMs` (500), springs existants.
2. **Easing M3** — enter = Emphasized/Standard decelerate ; exit = Emphasized/Standard accelerate ; Linear réservé à la barre de progression.
3. **Un seul porteur** — Player / album / artiste : shared morph + fade NavHost (pas de double slide).
4. **Reduced motion** — `rememberSgReducedMotion()` (système OR Mode perf) snappe overlays, nav, shared, press scale, ThemeReveal, coils.
5. **Micro-interactions** — `sgPressScale` (SpringSnappy + FastMs) sur CTA Lecture/Aléatoire, chips, bottom nav, contrôles file.

---

## 8. Sources consultées (historique)

- Laws of UX — https://lawsofux.com
- NN/g Articles — https://www.nngroup.com/articles/
- Material Design 3 — https://m3.material.io · https://m3.material.io/styles · https://m3.material.io/styles/motion/overview/how-it-works · https://m3.material.io/styles/motion/easing-and-duration
- Making Motion Meaningful (Google Design) — https://design.google/library/making-motion-meaningful
- Android Compose animation quick guide — https://developer.android.com/develop/ui/compose/animation/quick-guide
- androidx Compose Animation releases — https://developer.android.com/jetpack/androidx/releases/compose-animation
- skydoves/compose-animations — https://github.com/skydoves/compose-animations
- Recreating the Apple Music Now Playing transition (Kodeco) — https://www.kodeco.com/221-recreating-the-apple-music-now-playing-transition
- Tubik Studio, Echo case study — https://blog.tubikstudio.com/case-study-echo-designing-uxui/
- 60fps.design — Spotify — https://60fps.design/apps/spotify
- Dribbble, collection Music — https://dribbble.com/ashish9999/collections/1447597-Music
- Spotify Community, "Modernize Spotify's Design" — https://community.spotify.com/t5/Live-Ideas/Modernize-Spotify-s-Design-Fluid-Animations-amp-Sleeker-Visuals/idi-p/7460772
- Apple Human Interface Guidelines, Motion — https://developer.apple.com/design/human-interface-guidelines
