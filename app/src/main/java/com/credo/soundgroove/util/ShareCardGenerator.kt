package com.credo.soundgroove.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.credo.soundgroove.data.model.Song
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object ShareCardGenerator {

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1920

    fun generateCardBitmap(
        context: Context,
        song: Song,
        accentArgb: Int = 0xFFD4AF37.toInt()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_HEIGHT.toFloat(),
                intArrayOf(0xFF0A0A0A.toInt(), 0xFF1A1A1A.toInt(), 0xFF0D0D0D.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

        val artSize = (CARD_WIDTH * 0.72f).toInt()
        val artLeft = (CARD_WIDTH - artSize) / 2f
        val artTop = CARD_HEIGHT * 0.18f
        val cornerRadius = 32f

        val albumArt = loadAlbumArt(context, song)
        if (albumArt != null) {
            val scaled = Bitmap.createScaledBitmap(albumArt, artSize, artSize, true)
            val clipPath = Path().apply {
                addRoundRect(
                    RectF(artLeft, artTop, artLeft + artSize, artTop + artSize),
                    cornerRadius,
                    cornerRadius,
                    Path.Direction.CW
                )
            }
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(scaled, artLeft, artTop, null)
            canvas.restore()
            if (scaled !== albumArt) scaled.recycle()
            albumArt.recycle()
        } else {
            val placeholderPaint = Paint().apply { color = 0xFF2A2A2A.toInt() }
            canvas.drawRoundRect(
                RectF(artLeft, artTop, artLeft + artSize, artTop + artSize),
                cornerRadius,
                cornerRadius,
                placeholderPaint
            )
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentArgb
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }

        val textCenterX = CARD_WIDTH / 2f
        val titleY = artTop + artSize + 100f
        canvas.drawText(ellipsize(song.title, titlePaint, CARD_WIDTH - 120), textCenterX, titleY, titlePaint)
        canvas.drawText(ellipsize(song.artist, artistPaint, CARD_WIDTH - 120), textCenterX, titleY + 70f, artistPaint)

        canvas.drawText("SOUNDGROOVE", textCenterX, CARD_HEIGHT - 120f, brandPaint)

        return bitmap
    }

    fun shareCard(context: Context, song: Song, accentArgb: Int = 0xFFD4AF37.toInt()) {
        val bitmap = generateCardBitmap(context, song, accentArgb)
        val cacheDir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(cacheDir, "soundgroove_share_${song.id}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.artist}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("image", uri)
        }
        context.startActivity(Intent.createChooser(intent, "Partager la carte"))
    }

    private fun loadAlbumArt(context: Context, song: Song): Bitmap? {
        val uri = song.albumArtUri ?: return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Int): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return if (end > 0) text.substring(0, min(end, text.length)) + "…" else "…"
    }
}
