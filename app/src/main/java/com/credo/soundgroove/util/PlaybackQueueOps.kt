package com.credo.soundgroove.util

/**
 * Opérations pures sur une file d'attente — sans Media3 / ExoPlayer.
 * Utilisable pour tests JVM et pour factoriser les mutations de file
 * côté ViewModel sans toucher au moteur de lecture.
 */
object PlaybackQueueOps {

    fun <T> moveItems(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        val mutable = items.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        return mutable
    }

    fun <T> removeItemAt(items: List<T>, index: Int): List<T> {
        if (index !in items.indices) return items
        return items.toMutableList().also { it.removeAt(index) }
    }

    fun move(ids: List<Long>, from: Int, to: Int): List<Long> = moveItems(ids, from, to)

    fun removeAt(ids: List<Long>, index: Int): List<Long> = removeItemAt(ids, index)

    /**
     * Après suppression à [removedIndex], recalcule l'index courant.
     * Si on retire la piste en cours, on garde le même slot (piste suivante),
     * borné à la nouvelle taille.
     */
    fun adjustCurrentIndexAfterRemove(
        currentIndex: Int,
        removedIndex: Int,
        newSize: Int,
    ): Int {
        if (newSize <= 0) return 0
        return when {
            removedIndex < currentIndex -> (currentIndex - 1).coerceIn(0, newSize - 1)
            removedIndex == currentIndex -> currentIndex.coerceIn(0, newSize - 1)
            else -> currentIndex.coerceIn(0, newSize - 1)
        }
    }

    /** Insère [songId] juste après [currentIndex] (play next). */
    fun insertNext(ids: List<Long>, currentIndex: Int, songId: Long): List<Long> {
        if (ids.isEmpty()) return listOf(songId)
        val insertAt = (currentIndex + 1).coerceIn(0, ids.size)
        return ids.toMutableList().also { it.add(insertAt, songId) }
    }

    fun append(ids: List<Long>, songId: Long): List<Long> = ids + songId
}
