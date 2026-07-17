package com.credo.soundgroove.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.credo.soundgroove.ui.theme.GlassBorder
import com.credo.soundgroove.ui.theme.GlassSurface
import com.credo.soundgroove.ui.theme.SgSpacing
import com.credo.soundgroove.ui.theme.TextSecondary

/**
 * Switch SoundGroove : thumb diamètre exact [SgSpacing.switchThumb] (28dp).
 */
@Composable
fun SgSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val thumb = SgSpacing.switchThumb
    val trackH = SgSpacing.switchTrackHeight
    val trackW = SgSpacing.switchTrackWidth
    val pad = ((trackH - thumb) / 2).coerceAtLeast(0.dp)
    Box(
        modifier = modifier
            .size(width = trackW, height = trackH)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (checked) accentColor else GlassSurface)
            .border(
                width = 1.dp,
                color = if (checked) accentColor.copy(alpha = 0.35f) else GlassBorder.copy(alpha = 0.35f),
                shape = RoundedCornerShape(percent = 50)
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = pad)
                .size(thumb)
                .background(
                    color = if (checked) Color.White else TextSecondary,
                    shape = CircleShape
                )
        )
    }
}

/**
 * Seekbar Player : track 4dp, thumb cercle 16dp, hitSlop 44dp (custom — M3 insuffisant).
 */
@Composable
fun SgSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    accentColor: Color,
    inactiveTrackColor: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thumbDp = SgSpacing.seekThumb
    val trackDp = SgSpacing.seekTrack
    val hitSlopDp = SgSpacing.seekHitSlop
    var widthPx by remember { mutableFloatStateOf(0f) }
    val thumbPx = with(density) { thumbDp.toPx() }
    val trackPx = with(density) { trackDp.toPx() }

    fun fractionFromX(x: Float): Float {
        val travel = (widthPx - thumbPx).coerceAtLeast(1f)
        return ((x - thumbPx / 2f) / travel).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(hitSlopDp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(widthPx) {
                detectTapGestures { offset ->
                    onValueChange(fractionFromX(offset.x))
                    onValueChangeFinished()
                }
            }
            .pointerInput(widthPx) {
                detectDragGestures(
                    onDragEnd = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() },
                    onDrag = { change, _ ->
                        change.consume()
                        onValueChange(fractionFromX(change.position.x))
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(hitSlopDp)) {
            val cy = size.height / 2f
            val trackLeft = thumbPx / 2f
            val trackRight = size.width - thumbPx / 2f
            val trackWidth = (trackRight - trackLeft).coerceAtLeast(0f)
            val frac = value.coerceIn(0f, 1f)
            val activeW = trackWidth * frac
            val radius = trackPx / 2f
            drawRoundRect(
                color = inactiveTrackColor,
                topLeft = Offset(trackLeft, cy - radius),
                size = Size(trackWidth, trackPx),
                cornerRadius = CornerRadius(radius, radius)
            )
            if (activeW > 0f) {
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(trackLeft, cy - radius),
                    size = Size(activeW, trackPx),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
            val cx = trackLeft + activeW
            drawCircle(
                color = accentColor,
                radius = thumbPx / 2f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = thumbPx / 2f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
