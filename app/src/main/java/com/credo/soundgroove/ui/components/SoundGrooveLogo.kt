package com.credo.soundgroove.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.R
import com.credo.soundgroove.ui.theme.CyanAccent
import com.credo.soundgroove.ui.theme.SoundGrooveTheme

@Composable
fun SoundGrooveLogo(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black,
    iconColor: Color = CyanAccent
) {
    val description = stringResource(id = R.string.soundgroove_logo_description)
    Box(
        modifier = modifier
            .size(300.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(84.dp))
            .background(backgroundColor)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(64.dp)
        ) {
            val w = size.width
            val h = size.height
            val strokeWidth = w * 0.18f

            // 1. Bottom circular loop
            drawArc(
                color = iconColor,
                startAngle = -20f,
                sweepAngle = 305f,
                useCenter = false,
                topLeft = Offset(w * 0.08f, h * 0.48f),
                size = Size(w * 0.46f, h * 0.46f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Vertical Stem
            val stemX = w * 0.54f
            val splitY = h * 0.32f
            drawLine(
                color = iconColor,
                start = Offset(stemX, h * 0.71f),
                end = Offset(stemX, splitY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Butt
            )

            // 3. Top Horizontal Bar (Right)
            drawLine(
                color = iconColor,
                start = Offset(stemX, splitY),
                end = Offset(w * 0.88f, splitY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // 4. Top Hook (Left)
            drawArc(
                color = iconColor,
                startAngle = 0f,
                sweepAngle = -115f,
                useCenter = false,
                topLeft = Offset(w * 0.18f, h * 0.08f),
                size = Size(w * 0.36f, h * 0.48f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SoundGrooveLogoPreview() {
    SoundGrooveTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            SoundGrooveLogo()
        }
    }
}
