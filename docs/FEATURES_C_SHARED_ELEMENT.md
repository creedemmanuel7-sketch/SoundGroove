# Shared element mini-player → Player (livré)

## Contexte

Précédemment (voir historique git), cette fonctionnalité avait été **reportée** :
le mini-player étant rendu en overlay hors des destinations `NavHost`, il ne
disposait pas de l'`AnimatedVisibilityScope` requis par `Modifier.sharedElement`.
Un morph manuel (scale + offset sur la pochette du Player) servait de filet de
sécurité en attendant.

Cette passe **AGENT PLAYER EXPERIENCE** livre l'implémentation réelle.

## Livré

- **`SharedTransitionLayout` réel** (Compose Animation 1.7, `@ExperimentalSharedTransitionApi`)
  enveloppant tout le contenu de `AppNavigation` (NavHost + overlays), au lieu du
  morph manuel seul.
- La pochette **morphe réellement** (position + taille interpolées nativement par
  Compose, pas par des `Animatable` gérés à la main) entre :
  - le mini-player overlay de `AppNavigation` (affiché sur Recherche, Album,
    Artiste, Playlist, Dossier…) ;
  - le mini-player **intégré à `MainScreen`** (onglet Accueil) ;
  - la pochette plein écran de `PlayerScreen` (destination `player` du `NavHost`).
- **Fallback conservé** : si le contexte `SharedTransitionScope`/`AnimatedVisibilityScope`
  n'est pas disponible (ex. preview, futur call site non raccordé), `PlayerScreen`
  retombe automatiquement sur son ancien morph manuel scale + position. Aucune
  régression possible sur un appelant non migré.

## Implémentation

### CompositionLocal plutôt que paramètres (`ui/theme/Motion.kt`)

Le mini-player existe à **deux endroits distincts** (overlay `AppNavigation` et
overlay interne à `MainScreen`/`LegacyMainHost`) et la pochette plein écran vit
dans une destination `NavHost`. Faire remonter le `SharedTransitionScope` par
paramètre à travers toute la hiérarchie d'appel (`MainScreen`, `LegacyMainHost`…)
aurait nécessité de modifier de nombreuses signatures pour un simple pass-through.

La documentation officielle recommande explicitement une alternative dans ce cas :

> In order to use multiple scopes, save your required scopes in a
> `CompositionLocal`, use context receivers in Kotlin, or pass the scopes as
> parameters to your functions.
> — [Shared element transitions in Compose](https://developer.android.com/develop/ui/compose/animation/shared-elements)

D'où deux `CompositionLocal` ajoutés dans `Motion.kt` :

- `LocalSharedTransitionScope` — le `SharedTransitionScope` actif (fourni une
  seule fois, au niveau `AppNavigation`).
- `LocalSgAnimatedVisibilityScope` — l'`AnimatedVisibilityScope` du point
  d'entrée courant (fourni à chaque call site : destination `player` du
  `NavHost`, `AnimatedVisibility` du mini-player overlay, `AnimatedVisibility`
  du mini-player intégré à `MainScreen`).

Deux fonctions exposent ce contexte **sans jamais faire fuiter de type
expérimental dans une signature publique** (donc sans forcer `PlayerScreen` ou
`MiniPlayer` à s'annoter `@OptIn(ExperimentalSharedTransitionApi::class)`) :

- `rememberSgSharedElementActive(): Boolean` — pour désactiver un comportement
  alternatif (ici : le morph manuel de `PlayerScreen`).
- `Modifier.sgSharedAlbumArt(key: String): Modifier` — applique
  `Modifier.sharedElement(...)` si le contexte est disponible, sinon no-op.

### Câblage

- **`AppNavigation.kt`** : `@OptIn(ExperimentalSharedTransitionApi::class)`,
  tout le contenu enveloppé dans `SharedTransitionLayout { ... }`, qui fournit
  `LocalSharedTransitionScope`. La destination `player` fournit
  `LocalSgAnimatedVisibilityScope` via `this@composable` (l'`AnimatedContentScope`
  du `NavHost`, qui implémente `AnimatedVisibilityScope`). Le mini-player overlay
  est passé d'un simple `if` à un `AnimatedVisibility` pour exposer son propre scope.
- **`MainScreen.kt`** : le mini-player intégré (onglet Accueil) fournit lui aussi
  `LocalSgAnimatedVisibilityScope` via son `AnimatedVisibility` existant — aucun
  changement de signature, juste un `CompositionLocalProvider` autour de l'appel.
- **`MiniPlayer.kt`** : `albumArtModifier` a désormais pour valeur par défaut
  `Modifier.sgSharedAlbumArt(key = "album_art_${song.id}")` — le shared element
  est donc actif par défaut partout où `MiniPlayer` est utilisé, sans qu'aucun
  appelant n'ait à y penser.
- **`PlayerScreen.kt`** : la pochette applique le même `sgSharedAlbumArt` (même
  clé, basée sur `song.id`, donc stable entre mini-player et plein écran pour un
  même morceau). `rememberSgSharedElementActive()` désactive l'animation manuelle
  `artScale`/`artOffsetY` quand le shared element réel prend le relais (sinon les
  deux animations se cumuleraient et produiraient un à-coup) ; le fondu du
  "chrome" (titre, slider, contrôles) reste géré manuellement dans tous les cas.

### Pourquoi c'est sûr

- Aucune signature publique n'expose de type `@ExperimentalSharedTransitionApi` :
  seuls `Motion.kt` (déclaration) et `AppNavigation.kt` (utilisation directe de
  `SharedTransitionLayout`) portent l'annotation `@OptIn`.
  `PlayerScreen`/`MiniPlayer`/`MainScreen` restent inchangés sur ce plan.
- Le fallback manuel (scale + position) reste intact et continue de fonctionner
  pour tout appelant hors contexte `SharedTransitionLayout`.
- Les gestes existants de `PlayerScreen` (swipe horizontal = piste suivante/
  précédente, swipe vertical = fermer/ouvrir la file) sont inchangés : le
  shared element est appliqué en plus du `pointerInput` existant, pas à sa place.

## Clés shared (contrat)

| Transition | API | Clé |
|---|---|---|
| Mini ↔ Player (pochette) | `sharedElement` via `sgSharedAlbumArt` | `album_art_<songId>` |
| Mini ↔ Player (titre/artiste) | `sharedBounds` via `sgSharedBounds` | `track_meta_<songId>` |
| Mini ↔ Player (play) | `sharedBounds` via `sgSharedBounds` | `play_control_<songId>` (`sgPlayControlSharedKey`) |
| Grille album → détail hero | `sharedBounds` | `album_cover_<Uri.encode(name)>` (`sgAlbumCoverSharedKey`) |
| Liste artiste → avatar hero | `sharedBounds` | `artist_avatar_<Uri.encode(name)>` (`sgArtistAvatarSharedKey`) |

Helpers dans `Motion.kt`. Radius : albums `SgRadius.xl` (~24dp) des deux côtés ; artistes `CircleShape`.

**Customize (Compose)** : `sgSharedAlbumArt` / `sgSharedBounds` utilisent `boundsTransform` tokenisé
(`PlayerMorphMs` / `MediumMs` + Emphasized decelerate) et `OverlayClip` (`SgAlbumArtSharedClip`,
`SgAlbumCoverSharedClip`, `SgArtistAvatarSharedClip`, `CircleShape` pour play) — cf.
[Customize shared element transitions](https://developer.android.com/develop/ui/compose/animation/shared-elements#customize).

**Nav** : destinations `album/{…}` et `artist/{…}` en **fade only** (comme le Player) pour éviter double-morph avec le slide H `navForward`. `HOME`/`SEARCH` sortent en fade vers ces routes. Scopes `LocalSgAnimatedVisibilityScope` fournis sur `HOME`, `SEARCH`, `ALBUM_DETAIL`, `ARTIST_DETAIL`, `PLAYER` et les `AnimatedVisibility` mini-player.

**Sources shared album/artiste** : Bibliothèque (`LibraryTab`) **et** Recherche (`SearchScreen` — rows résultats Albums / Artistes, mêmes clés `sgAlbumCoverSharedKey` / `sgArtistAvatarSharedKey`). Accueil : pas de tuiles album détail → pas de câblage `album_cover_` (skip volontaire). Les chansons Recherche lancent la lecture sans ouvrir `Player` → pas de câblage `album_art_` / `play_control_` sur ces rows (sauf `SongListItem` avec `enablePlayerSharedElements`).

**Reduced motion / Mode performance** : `sgShared*` et `rememberSgSharedElementActive()` no-op / snap (inchangé).

## Référence

[Shared element transitions in Compose](https://developer.android.com/develop/ui/compose/animation/shared-elements) ·
[Navigation with shared elements](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation) ·
[M3 Easing & duration](https://m3.material.io/styles/motion/easing-and-duration) ·
[NN/g Animation duration](https://www.nngroup.com/articles/animation-duration/)
