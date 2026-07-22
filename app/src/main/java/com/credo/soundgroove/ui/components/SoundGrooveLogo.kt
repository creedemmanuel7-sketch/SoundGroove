package com.credo.soundgroove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.R
import com.credo.soundgroove.ui.theme.BrandBlack
import com.credo.soundgroove.ui.theme.BrandPurple
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.SoundGrooveTheme

/**
 * Logo in-app — waveform ring, **violet sur noir** (choix premium AMOLED).
 *
 * Variantes évaluées : violet/noir, noir/violet, émeraude/noir.
 * Retenue : violet signature sur plaque noire — cohérent launcher + site, sans blanc.
 */
@Composable
fun SoundGrooveLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    markColor: Color = BrandPurple,
) {
    val description = stringResource(id = R.string.soundgroove_logo_description)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(markColor.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.88f)
                .clip(RoundedCornerShape(SgRadius.xl))
                .background(BrandBlack)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_brand_waveform),
                contentDescription = null,
                tint = markColor,
                modifier = Modifier.fillMaxSize(0.86f),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SoundGrooveLogoPreview() {
    SoundGrooveTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandBlack),
            contentAlignment = Alignment.Center
        ) {
            SoundGrooveLogo()
        }
    }
}
