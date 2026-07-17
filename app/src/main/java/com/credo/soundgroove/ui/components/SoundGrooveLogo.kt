package com.credo.soundgroove.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.credo.soundgroove.ui.theme.BrandBlack
import com.credo.soundgroove.ui.theme.BrandCyan
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SoundGrooveTheme

/**
 * Affiche la même identité visuelle que l'icône du launcher
 * (note cyan sur fond noir — [R.drawable.ic_launcher_playstore]).
 */
@Composable
fun SoundGrooveLogo(
    modifier: Modifier = Modifier,
    accentColor: Color = BrandCyan,
    backgroundColor: Color = BrandBlack
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
                        listOf(accentColor.copy(alpha = 0.18f), Color.Transparent)
                    )
                )
        )
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_playstore),
            contentDescription = description,
            modifier = Modifier
                .fillMaxSize(0.86f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(SgRadius.xl))
                .background(backgroundColor)
                .semantics { contentDescription = description },
            contentScale = ContentScale.Fit
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SoundGrooveLogoPreview() {
    SoundGrooveTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(BrandBlack),
            contentAlignment = Alignment.Center
        ) {
            SoundGrooveLogo()
        }
    }
}
