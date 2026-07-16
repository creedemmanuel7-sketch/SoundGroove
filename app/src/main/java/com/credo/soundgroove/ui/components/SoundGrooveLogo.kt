package com.credo.soundgroove.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.R
import com.credo.soundgroove.ui.theme.ChampagneGold
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SoundGrooveTheme

/**
 * Rend la véritable identité visuelle de l'app (les mêmes vecteurs que l'icône du
 * launcher : `ic_launcher_background` + `ic_launcher_foreground`), afin que le logo
 * affiché à l'écran de bienvenue / dans l'app soit identique à l'icône installée sur
 * l'appareil — plus de divergence entre les deux. Un halo discret dans la couleur
 * d'accent du thème choisi enrichit la présentation sans changer la marque elle-même.
 */
@Composable
fun SoundGrooveLogo(
    modifier: Modifier = Modifier,
    accentColor: Color = ChampagneGold,
    backgroundColor: Color = Color(0xFF000000)
) {
    val description = stringResource(id = R.string.soundgroove_logo_description)
    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(accentColor.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.86f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(SgRadius.xl))
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(SgRadius.xl))
                .semantics { contentDescription = description }
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SoundGrooveLogoPreview() {
    SoundGrooveTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            SoundGrooveLogo()
        }
    }
}
