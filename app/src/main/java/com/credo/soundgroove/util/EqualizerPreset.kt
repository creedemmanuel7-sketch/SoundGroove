package com.credo.soundgroove.util

enum class EqualizerPreset(val label: String) {
    NORMAL("Normal"),
    ROCK("Rock"),
    POP("Pop"),
    JAZZ("Jazz"),
    CLASSICAL("Classical"),
    BASS_BOOST("Bass Boost"),
    VOCAL("Vocal"),
    CUSTOM("Personnalisé");

    /**
     * Retourne un gain relatif [-1, 1] pour chaque bande (0 = plat).
     * Les indices sont normalisés sur [0, bandCount).
     */
    fun relativeGains(bandCount: Int): FloatArray {
        if (bandCount <= 0) return FloatArray(0)
        return FloatArray(bandCount) { bandIndex ->
            val t = if (bandCount == 1) 0.5f else bandIndex.toFloat() / (bandCount - 1)
            when (this) {
                NORMAL -> 0f
                ROCK -> when {
                    t < 0.25f -> 0.55f
                    t < 0.5f -> 0.15f
                    t < 0.75f -> -0.05f
                    else -> 0.45f
                }
                POP -> when {
                    t < 0.3f -> 0.25f
                    t < 0.7f -> 0.35f
                    else -> 0.3f
                }
                JAZZ -> when {
                    t < 0.25f -> 0.2f
                    t < 0.75f -> 0.35f
                    else -> 0.15f
                }
                CLASSICAL -> when {
                    t < 0.2f -> -0.15f
                    t < 0.6f -> 0.1f
                    else -> 0.35f
                }
                BASS_BOOST -> when {
                    t < 0.35f -> 0.75f
                    t < 0.65f -> 0.15f
                    else -> -0.1f
                }
                VOCAL -> when {
                    t < 0.2f -> -0.25f
                    t < 0.45f -> 0.15f
                    t < 0.7f -> 0.65f
                    else -> 0.2f
                }
                CUSTOM -> 0f
            }
        }
    }

    companion object {
        fun fromStored(value: String?): EqualizerPreset =
            entries.find { it.name == value } ?: NORMAL
    }
}
