package com.credo.soundgroove.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.credo.soundgroove.R

@Composable
fun songsCountLabel(count: Int): String =
    pluralStringResource(R.plurals.songs_count, count, count)

@Composable
fun tracksCountLabel(count: Int): String =
    pluralStringResource(R.plurals.tracks_count, count, count)

@Composable
fun playlistsCountLabel(count: Int): String =
    pluralStringResource(R.plurals.playlists_count, count, count)

@Composable
fun listsCountLabel(count: Int): String =
    pluralStringResource(R.plurals.lists_count, count, count)

fun Context.songsCountLabel(count: Int): String =
    resources.getQuantityString(R.plurals.songs_count, count, count)

fun Context.tracksCountLabel(count: Int): String =
    resources.getQuantityString(R.plurals.tracks_count, count, count)

fun Context.playlistsCountLabel(count: Int): String =
    resources.getQuantityString(R.plurals.playlists_count, count, count)
