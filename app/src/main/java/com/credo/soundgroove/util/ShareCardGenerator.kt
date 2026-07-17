package com.credo.soundgroove.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Génère des cartes de partage visuelles pour les morceaux SoundGroove.
 *
 * ## Formats ([ShareCardFormat])
 *
 * - [ShareCardFormat.STORY] — 1080×1920 (9:16) : stories Instagram, Snapchat, TikTok.
 * - [ShareCardFormat.SQUARE] — 1080×1080 (1:1) : posts Instagram/Facebook, statuts WhatsApp.
 *
 * Format par défaut : [ShareCardFormat.STORY].
 * Exemple carré : `shareCard(context, song, format = ShareCardFormat.SQUARE)`.
 *
 * Le fond est extrait de la pochette via Palette API (dominant + accents vibrant).
 */
object ShareCardGenerator {

    enum class ShareCardFormat(val width: Int, val height: Int) {
        /** 9:16 — stories et vertical mobile. */
        STORY(1080, 1920),

        /** 1:1 — posts carrés et statuts. */
        SQUARE(1080, 1080),
    }

    private const val BRAND_PURPLE = 0xFFA855F7.toInt()
    private val DEFAULT_FORMAT = ShareCardFormat.STORY

    fun generateCardBitmap(
        context: Context,
        song: Song,
        accentArgb: Int = BRAND_PURPLE,
        format: ShareCardFormat = DEFAULT_FORMAT,
    ): Bitmap {
        val cardWidth = format.width
        val cardHeight = format.height
        val bitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val albumArt = loadAlbumArt(context, song)
        val palette = extractPalette(albumArt, accentArgb)

        drawBackground(canvas, cardWidth, cardHeight, palette)
        drawDecorativeElements(canvas, cardWidth, cardHeight, palette)

        val artSizeRatio = if (format == ShareCardFormat.SQUARE) 0.74f else 0.72f
        val artSize = (cardWidth * artSizeRatio).toInt()
        val artLeft = (cardWidth - artSize) / 2f
        val artTop = cardHeight * if (format == ShareCardFormat.SQUARE) 0.12f else 0.14f
        val cornerRadius = if (format == ShareCardFormat.SQUARE) 40f else 44f

        drawAlbumArtWithShadow(canvas, albumArt, artLeft, artTop, artSize.toFloat(), cornerRadius)

        val horizontalPadding = 72f
        val textMaxWidth = cardWidth - (horizontalPadding * 2).toInt()
        val textCenterX = cardWidth / 2f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (format == ShareCardFormat.SQUARE) 58f else 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = -0.02f
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            textSize = if (format == ShareCardFormat.SQUARE) 40f else 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.01f
        }

        val titleY = artTop + artSize + if (format == ShareCardFormat.SQUARE) 80f else 96f
        drawTextWithShadow(
            canvas,
            ellipsize(song.title, titlePaint, textMaxWidth),
            textCenterX,
            titleY,
            titlePaint,
            shadowAlpha = 0.45f,
            shadowDy = 4f,
        )
        drawTextWithShadow(
            canvas,
            ellipsize(song.artist, artistPaint, textMaxWidth),
            textCenterX,
            titleY + if (format == ShareCardFormat.SQUARE) 62f else 72f,
            artistPaint,
            shadowAlpha = 0.35f,
            shadowDy = 3f,
        )

        drawBranding(context, canvas, cardWidth, cardHeight, format)

        albumArt?.recycle()
        return bitmap
    }

    private const val SHARE_CACHE_DIR = "share"

    fun shareCard(
        context: Context,
        song: Song,
        accentArgb: Int = BRAND_PURPLE,
        format: ShareCardFormat = DEFAULT_FORMAT,
    ) {
        val bitmap = generateCardBitmap(context, song, accentArgb, format)
        val cacheDir = File(context.cacheDir, SHARE_CACHE_DIR).apply { mkdirs() }
        // Chaque carte est un fichier temporaire (au format PNG plein cadre) uniquement
        // nécessaire pour l'Intent.ACTION_SEND qui suit : on purge les cartes précédentes
        // avant d'en écrire une nouvelle pour éviter une accumulation silencieuse dans le
        // cache de partage à chaque utilisation de la fonctionnalité.
        cacheDir.listFiles()?.forEach { it.delete() }
        val file = File(cacheDir, "soundgroove_share_${song.id}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
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

    /** Taille totale actuelle du cache de cartes de partage temporaires, en octets. */
    fun shareCacheSizeBytes(context: Context): Long =
        runCatching {
            File(context.cacheDir, SHARE_CACHE_DIR).listFiles()?.sumOf { it.length() } ?: 0L
        }.getOrDefault(0L)

    fun clearShareCache(context: Context): Int =
        runCatching {
            val files = File(context.cacheDir, SHARE_CACHE_DIR).listFiles() ?: return 0
            files.count { it.delete() }
        }.getOrDefault(0)

    private data class CardPalette(
        val dominant: Int,
        val accent: Int,
        val secondary: Int,
        val glow: Int,
    )

    private fun extractPalette(bitmap: Bitmap?, fallbackAccent: Int): CardPalette {
        if (bitmap == null) {
            return CardPalette(
                dominant = 0xFF141418.toInt(),
                accent = fallbackAccent,
                secondary = BRAND_PURPLE,
                glow = fallbackAccent,
            )
        }
        val palette = Palette.from(bitmap).generate()
        val rawDominant = palette.dominantSwatch?.rgb ?: 0xFF1A1A1E.toInt()
        val accent = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: fallbackAccent
        val secondary = palette.lightVibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: accent
        val glow = palette.vibrantSwatch?.rgb ?: accent
        return CardPalette(
            dominant = darken(rawDominant, 0.5f),
            accent = accent,
            secondary = secondary,
            glow = glow,
        )
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int, palette: CardPalette) {
        val topColor = blend(darken(palette.dominant, 0.25f), palette.accent, 0.18f)
        val midColor = darken(palette.dominant, 0.55f)
        val bottomColor = blend(0xFF050508.toInt(), palette.glow, 0.12f)

        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                width * 0.15f,
                height.toFloat(),
                intArrayOf(topColor, midColor, bottomColor),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val vignettePaint = Paint().apply {
            shader = RadialGradient(
                width / 2f,
                height * 0.38f,
                width * 0.85f,
                intArrayOf(withAlpha(0x000000, 0f), withAlpha(0x000000, 0.55f)),
                floatArrayOf(0.35f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
    }

    private fun drawDecorativeElements(canvas: Canvas, width: Int, height: Int, palette: CardPalette) {
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        glowPaint.shader = RadialGradient(
            width * 0.88f,
            height * 0.08f,
            width * 0.55f,
            intArrayOf(withAlpha(palette.glow, 0.28f), withAlpha(palette.glow, 0f)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(width * 0.88f, height * 0.08f, width * 0.55f, glowPaint)

        glowPaint.shader = RadialGradient(
            width * 0.05f,
            height * 0.72f,
            width * 0.48f,
            intArrayOf(withAlpha(palette.secondary, 0.22f), withAlpha(palette.secondary, 0f)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(width * 0.05f, height * 0.72f, width * 0.48f, glowPaint)

        glowPaint.shader = RadialGradient(
            width * 0.72f,
            height * 0.95f,
            width * 0.42f,
            intArrayOf(withAlpha(palette.accent, 0.16f), withAlpha(palette.accent, 0f)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(width * 0.72f, height * 0.95f, width * 0.42f, glowPaint)

        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = withAlpha(palette.accent, 0.12f)
        }
        canvas.drawCircle(width * 0.12f, height * 0.22f, width * 0.18f, shapePaint)

        shapePaint.color = withAlpha(palette.secondary, 0.10f)
        shapePaint.strokeWidth = 2.5f
        canvas.drawCircle(width * 0.92f, height * 0.58f, width * 0.11f, shapePaint)

        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = withAlpha(palette.accent, 0.07f)
        }
        val wavePath = Path().apply {
            moveTo(0f, height * 0.42f)
            cubicTo(
                width * 0.25f, height * 0.36f,
                width * 0.55f, height * 0.48f,
                width.toFloat(), height * 0.40f,
            )
            lineTo(width.toFloat(), height * 0.52f)
            cubicTo(
                width * 0.6f, height * 0.58f,
                width * 0.3f, height * 0.46f,
                0f, height * 0.54f,
            )
            close()
        }
        canvas.drawPath(wavePath, wavePaint)

        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = withAlpha(palette.glow, 0.14f)
        }
        canvas.drawArc(
            RectF(width * -0.08f, height * 0.62f, width * 0.55f, height * 1.08f),
            10f,
            120f,
            false,
            arcPaint,
        )
    }

    private fun drawAlbumArtWithShadow(
        canvas: Canvas,
        albumArt: Bitmap?,
        left: Float,
        top: Float,
        size: Float,
        cornerRadius: Float,
    ) {
        val shadowRect = RectF(left, top, left + size, top + size)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (layer in 5 downTo 1) {
            val offset = layer * 7f
            shadowPaint.color = withAlpha(0x000000, (12 + layer * 10) / 255f)
            canvas.drawRoundRect(
                shadowRect.left,
                shadowRect.top + offset,
                shadowRect.right,
                shadowRect.bottom + offset,
                cornerRadius,
                cornerRadius,
                shadowPaint,
            )
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(Color.WHITE, 0.10f)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        if (albumArt != null) {
            val scaled = Bitmap.createScaledBitmap(albumArt, size.toInt(), size.toInt(), true)
            val clipPath = Path().apply {
                addRoundRect(shadowRect, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(scaled, left, top, null)
            canvas.restore()
            if (scaled !== albumArt) scaled.recycle()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF25252A.toInt()
            }
            canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, placeholderPaint)
        }

        canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, borderPaint)
    }

    private fun drawTextWithShadow(
        canvas: Canvas,
        text: String,
        centerX: Float,
        y: Float,
        paint: Paint,
        shadowAlpha: Float,
        shadowDy: Float,
    ) {
        val shadowPaint = Paint(paint).apply {
            color = withAlpha(0x000000, shadowAlpha)
        }
        canvas.drawText(text, centerX, y + shadowDy, shadowPaint)
        canvas.drawText(text, centerX, y, paint)
    }

    private fun drawBranding(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        format: ShareCardFormat,
    ) {
        val logoSize = if (format == ShareCardFormat.SQUARE) 52 else 56
        val logoBitmap = ContextCompat.getDrawable(context, R.drawable.ic_brand_waveform)
            ?.toBitmap(logoSize, logoSize)

        val brandTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BRAND_PURPLE
            textSize = if (format == ShareCardFormat.SQUARE) 34f else 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        val taglinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(Color.WHITE, 0.45f)
            textSize = if (format == ShareCardFormat.SQUARE) 22f else 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            letterSpacing = 0.08f
        }

        val brandText = "SoundGroove"
        val textWidth = brandTextPaint.measureText(brandText)
        val logoGap = 16f
        val totalWidth = logoSize + logoGap + textWidth
        val baseY = height - if (format == ShareCardFormat.SQUARE) 88f else 108f
        val startX = (width - totalWidth) / 2f

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(BRAND_PURPLE, 0.22f)
            strokeWidth = 1.5f
        }
        val dividerY = baseY - 52f
        canvas.drawLine(width * 0.22f, dividerY, width * 0.78f, dividerY, dividerPaint)

        logoBitmap?.let { logo ->
            canvas.drawBitmap(logo, startX, baseY - logoSize + 8f, null)
            logo.recycle()
        }

        val textX = startX + logoSize + logoGap
        canvas.drawText(brandText, textX, baseY, brandTextPaint)
        canvas.drawText("ÉCOUTE & PARTAGE", textX, baseY + 34f, taglinePaint)
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

    private fun darken(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(color) * f).toInt(),
            (Color.green(color) * f).toInt(),
            (Color.blue(color) * f).toInt(),
        )
    }

    private fun blend(colorA: Int, colorB: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(colorA) * (1f - t) + Color.red(colorB) * t).toInt(),
            (Color.green(colorA) * (1f - t) + Color.green(colorB) * t).toInt(),
            (Color.blue(colorA) * (1f - t) + Color.blue(colorB) * t).toInt(),
        )
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
