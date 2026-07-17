package com.credo.soundgroove.lyrics

/** Une ligne de paroles avec son horodatage (en millisecondes) dans le morceau. */
data class LyricLine(
    val timeMs: Long,
    val text: String
)
