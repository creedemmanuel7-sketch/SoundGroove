# Shared element mini-player → player (reporté)

## Livré (scénario C)
- Transition **scale + slide** améliorée sur la route `player` dans `AppNavigation.kt` (entrée/sortie plus fluide).
- `MiniPlayer.kt` accepte un `albumArtModifier` optionnel pour brancher un shared element ultérieurement.

## Non livré (volontairement)
- **SharedTransitionLayout / sharedElement** entre le mini-player overlay et `PlayerScreen`.

## Raison
Le mini-player est rendu **en dehors** des destinations `NavHost` (overlay en bas d'écran). L'API `Modifier.sharedElement` exige un `AnimatedVisibilityScope` fourni par chaque destination Compose Navigation. L'overlay n'a pas ce scope sans refactor (déplacer le mini-player dans une route dédiée ou dupliquer la logique player).

Modifier `PlayerScreen` (gestes swipe horizontal/vertical, queue) pour expérimental `@ExperimentalSharedTransitionApi` augmente le risque de régression avec le travail **AGENT STABILITÉ**.

## Piste d'implémentation future
1. Envelopper `NavHost` dans `SharedTransitionLayout`.
2. Utiliser la même clé (`rememberSharedContentState("album_art")`) sur la pochette du mini-player et celle du player.
3. Obtenir `AnimatedVisibilityScope` via la destination courante (`LocalAnimatedVisibilityScope`) ou migrer le mini-player vers une route `mini_player` transparente.
4. Référence : [Share element transitions with Navigation Compose](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation)
