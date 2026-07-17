package com.credo.soundgroove.ui.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.lyrics.LyricsSearchHelper
import com.credo.soundgroove.ui.components.formatDuration
import com.credo.soundgroove.ui.theme.BorderSubtle
import com.credo.soundgroove.ui.theme.GlassBorder
import com.credo.soundgroove.ui.theme.GlassSurface
import com.credo.soundgroove.ui.theme.GraphiteAbyss
import com.credo.soundgroove.ui.theme.SgRadius
import com.credo.soundgroove.ui.theme.TextPrimary
import com.credo.soundgroove.ui.theme.TextTertiary

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

/**
 * Recherche Google in-app pour trouver des paroles à copier/coller manuellement.
 * Recherche initiée par l'utilisateur via WebView standard — pas de scraping automatique.
 *
 * ## Pourquoi pas de synchro ligne par ligne ici
 * Une page de résultats Google (ou les sites tiers qu'elle référence) est du HTML
 * libre, sans structure de timestamps garantie : il n'existe pas de format fiable
 * à parser pour aligner chaque ligne sur la position de lecture, et scraper ces
 * pages pour en extraire des horodatages serait fragile (mise en page qui change
 * à tout moment) et contraire aux CGU de Google. La seule sync fiable est le
 * format **LRC** (paroles horodatées `[mm:ss.xx]`), déjà géré nativement par
 * [com.credo.soundgroove.lyrics.LrcParser] / [com.credo.soundgroove.lyrics.LyricsRepository]
 * quand il est collé manuellement ou trouvé via LRCLIB.
 *
 * En attendant, cet écran affiche [playbackPositionMs] sous forme de repère temps
 * réel (mini-lecteur superposé à la WebView) afin que l'utilisateur puisse suivre
 * la progression du morceau pendant sa recherche, sans quitter l'écran.
 */
@Composable
fun LyricsWebSearchScreen(
    song: Song,
    accentColor: Color,
    playbackPositionMs: Long = 0L,
    isPlaying: Boolean = false,
    onPlayPause: () -> Unit = {},
    onClose: () -> Unit,
    onPasteFromClipboard: (String) -> Unit
) {
    val context = LocalContext.current
    val searchUrl = remember(song.id) { LyricsSearchHelper.buildGoogleLyricsSearchUrl(song) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) {
            view.goBack()
        } else {
            onClose()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GraphiteAbyss)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(GlassSurface, CircleShape)
                    .border(1.dp, GlassBorder, CircleShape)
                    .clickable {
                        val view = webView
                        if (view != null && view.canGoBack()) {
                            view.goBack()
                        } else {
                            onClose()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chercher en ligne",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.title} · ${song.artist}",
                    color = accentColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = accentColor,
                trackColor = BorderSubtle.copy(alpha = 0.35f)
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LyricsSearchWebView(
                url = searchUrl,
                onWebViewCreated = { webView = it },
                onLoadingChanged = { loading, progress ->
                    isLoading = loading
                    loadProgress = progress
                }
            )

            if (isLoading && loadProgress == 0) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = accentColor
                )
            }

            // Repère de lecture temps réel superposé à la WebView : Google ne peut pas
            // être scrollé automatiquement ligne par ligne (page HTML non structurée,
            // sans timestamps fiables), mais l'utilisateur voit toujours où en est la
            // lecture pendant sa recherche, sans revenir au Player.
            PlaybackPositionOverlay(
                song = song,
                positionMs = playbackPositionMs,
                isPlaying = isPlaying,
                accentColor = accentColor,
                onPlayPause = onPlayPause,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassSurface.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Google n'est pas synchronisé ligne par ligne (page web sans horodatage). " +
                        "Copiez les paroles puis collez-les dans SoundGroove — si elles sont au format " +
                        "LRC ([mm:ss.xx]), la synchro sera automatique.",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    readClipboardText(context)?.let { text ->
                        onPasteFromClipboard(text)
                    } ?: Toast.makeText(
                        context,
                        "Presse-papiers vide — copiez d'abord les paroles depuis la page",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SgRadius.pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Coller depuis le presse-papiers", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Mini-lecteur superposé en bas de la WebView : montre la position de lecture
 * en temps réel (mm:ss / mm:ss + barre de progression) pendant que l'utilisateur
 * cherche des paroles sur Google. Ce n'est **pas** une synchro ligne par ligne —
 * la page web n'a pas de timestamps exploitables — mais ça donne un repère
 * temporel fiable sans quitter l'écran de recherche.
 */
@Composable
private fun PlaybackPositionOverlay(
    song: Song,
    positionMs: Long,
    isPlaying: Boolean,
    accentColor: Color,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = song.duration.takeIf { it > 0L } ?: 1L
    val progress = (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .background(GraphiteAbyss.copy(alpha = 0.82f), RoundedCornerShape(SgRadius.lg))
            .border(1.dp, GlassBorder, RoundedCornerShape(SgRadius.lg))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(listOf(accentColor, accentColor.copy(alpha = 0.4f))),
                    RoundedCornerShape(topStart = SgRadius.lg, topEnd = SgRadius.lg)
                )
        )
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(accentColor.copy(alpha = 0.9f), CircleShape)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Lecture",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = song.title,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatDuration(positionMs)} / ${formatDuration(song.duration)}",
                    color = accentColor,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Recherche in-app ponctuelle : pas besoin de cache disque persistant entre deux
 * sessions de recherche (contrairement à un navigateur classique). [WebSettings.LOAD_NO_CACHE]
 * évite que chaque recherche Google alourdisse silencieusement le dossier `app_webview`
 * de l'app (HTML/CSS/JS/images des pages visitées) — c'est la principale source de
 * croissance de stockage observée après plusieurs recherches de paroles en ligne.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LyricsSearchWebView(
    url: String,
    onWebViewCreated: (WebView) -> Unit,
    onLoadingChanged: (loading: Boolean, progress: Int) -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            // Purge systématique en quittant l'écran : historique, cache disque et
            // formulaires ne doivent pas s'accumuler d'une recherche à l'autre.
            webViewRef?.apply {
                clearHistory()
                clearCache(true)
                clearFormData()
                destroy()
            }
            webViewRef = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    userAgentString = MOBILE_USER_AGENT
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        onLoadingChanged(false, 100)
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onLoadingChanged(newProgress < 100, newProgress)
                    }
                }
                webViewRef = this
                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { view ->
            if (view.url != url) {
                view.loadUrl(url)
            }
        }
    )
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    if (!clipboard.hasPrimaryClip()) return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotBlank() }
}
