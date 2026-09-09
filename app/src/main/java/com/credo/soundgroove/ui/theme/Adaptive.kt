package com.credo.soundgroove.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Breakpoints type WindowSizeClass (largeur) sans dépendance material3-adaptive —
 * alignés sur les seuils Material (~600 / ~840 dp).
 *
 * Compact = téléphone / multi-fenêtre étroit ;
 * Medium = pliable / tablette portrait ;
 * Expanded = tablette paysage / desktop-like.
 */
enum class SgWidthSizeClass {
    Compact,
    Medium,
    Expanded,
}

object SgAdaptive {
    const val CompactMaxDp = 600
    const val MediumMaxDp = 840

    /** Largeur max du contenu bibliothèque / accueil (évite l’étirement tablette). */
    val ContentMax = 720.dp

    /** Contenu élargi (settings / listes densifiées) sur Expanded. */
    val ContentMaxExpanded = 960.dp

    /** Chrome player (pochette + contrôles) centré sur fenêtres larges. */
    val PlayerChromeMax = 520.dp

    /** Mini-player plafonné sur tablette / multi-fenêtre. */
    val MiniPlayerMax = 560.dp
}

@Composable
@ReadOnlyComposable
fun rememberSgWidthSizeClass(): SgWidthSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < SgAdaptive.CompactMaxDp -> SgWidthSizeClass.Compact
        widthDp < SgAdaptive.MediumMaxDp -> SgWidthSizeClass.Medium
        else -> SgWidthSizeClass.Expanded
    }
}

/** Gutter horizontal adaptatif (phone → tablette). */
@Composable
@ReadOnlyComposable
fun sgScreenHorizontal(): Dp = when (rememberSgWidthSizeClass()) {
    SgWidthSizeClass.Compact -> SgSpacing.screenHorizontal
    SgWidthSizeClass.Medium -> 28.dp
    SgWidthSizeClass.Expanded -> 40.dp
}

/** Colonnes grille Albums (Bibliothèque). */
@Composable
@ReadOnlyComposable
fun sgAlbumGridColumns(): Int = when (rememberSgWidthSizeClass()) {
    SgWidthSizeClass.Compact -> 2
    SgWidthSizeClass.Medium -> 3
    SgWidthSizeClass.Expanded -> 4
}

/** Colonnes artistes / dossiers (un cran plus dense que albums). */
@Composable
@ReadOnlyComposable
fun sgListGridColumns(): Int = when (rememberSgWidthSizeClass()) {
    SgWidthSizeClass.Compact -> 1
    SgWidthSizeClass.Medium -> 2
    SgWidthSizeClass.Expanded -> 3
}

/**
 * True quand une disposition dual-pane (liste | détail) est utile —
 * à brancher progressivement sur Library / Search.
 */
@Composable
@ReadOnlyComposable
fun sgPreferDualPane(): Boolean =
    rememberSgWidthSizeClass() == SgWidthSizeClass.Expanded

/** Plafond contenu selon classe (évite 720 dp trop étroit sur Expanded). */
@Composable
@ReadOnlyComposable
fun sgContentMaxWidth(): Dp = when (rememberSgWidthSizeClass()) {
    SgWidthSizeClass.Compact,
    SgWidthSizeClass.Medium -> SgAdaptive.ContentMax
    SgWidthSizeClass.Expanded -> SgAdaptive.ContentMaxExpanded
}

/**
 * Centre et plafonne la largeur — à enchaîner après [fillMaxWidth] / sur un parent
 * qui occupe déjà l’écran.
 */
fun Modifier.sgConstrainWidth(maxWidth: Dp = SgAdaptive.ContentMax): Modifier =
    this
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = maxWidth)

/** Variante qui lit la WindowSizeClass courante. */
@Composable
fun Modifier.sgConstrainContentWidth(): Modifier =
    sgConstrainWidth(sgContentMaxWidth())
