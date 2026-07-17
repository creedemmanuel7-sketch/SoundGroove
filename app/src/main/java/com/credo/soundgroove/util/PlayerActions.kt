package com.credo.soundgroove.util

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.credo.soundgroove.data.model.Song

object PlayerActions {

    fun shareSong(context: Context, song: Song) {
        val text = "${song.title} — ${song.artist}"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, song.uri)
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("audio", song.uri)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Partager"))
    }

    fun shareSongCard(
        context: Context,
        song: Song,
        accentArgb: Int = 0xFFA855F7.toInt(),
        format: ShareCardGenerator.ShareCardFormat = ShareCardGenerator.ShareCardFormat.STORY,
    ) {
        ShareCardGenerator.shareCard(context, song, accentArgb, format)
    }

    fun setAsRingtone(context: Context, song: Song): Boolean {
        return try {
            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                song.uri
            )
            Toast.makeText(context, "Sonnerie définie", Toast.LENGTH_SHORT).show()
            true
        } catch (_: SecurityException) {
            Toast.makeText(
                context,
                "Autorisation requise : accordez la modification des paramètres système",
                Toast.LENGTH_LONG
            ).show()
            false
        } catch (_: Exception) {
            Toast.makeText(context, "Impossible de définir la sonnerie", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
