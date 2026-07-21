package com.credo.soundgroove.ui.navigation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.ui.theme.SgSpacing

/**
 * Règle unique de visibilité du mini-player.
 *
 * Contrat détaillé : [docs/NAVIGATION_CONTRACT.md](../../../../../../../docs/NAVIGATION_CONTRACT.md)
 */
object MiniPlayerVisibility {

    /**
     * @param currentRoute Route NavHost active ([Routes]).
     * @param hasCurrentSong Une piste est en cours (ViewModel).
     * @param persistentEnabled Réglage « Mini-player persistant » (Settings).
     * @param homeSuppressed Overlay Accueil masquant le mini (ex. « Récemment joué » plein écran).
     */
    fun shouldShow(
        currentRoute: String?,
        hasCurrentSong: Boolean,
        persistentEnabled: Boolean,
        homeSuppressed: Boolean = false,
    ): Boolean {
        if (!persistentEnabled || !hasCurrentSong) return false
        if (currentRoute == Routes.PLAYER) return false
        if (currentRoute == Routes.HOME && homeSuppressed) return false
        return true
    }

    /** Décalage bas pour aligner le mini au-dessus du bottom nav sur [Routes.HOME]. */
    fun bottomPadding(currentRoute: String?): Dp = when (currentRoute) {
        Routes.HOME -> SgSpacing.navHeight + SgSpacing.xs * 2
        else -> 8.dp
    }
}
