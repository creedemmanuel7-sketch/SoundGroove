package com.credo.soundgroove.data.repository

import android.content.Context
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

data class ListeningStats(
    val weekSeconds: Long,
    val monthSeconds: Long,
    val streakDays: Int,
    val totalSeconds: Long
)

class ListeningStatsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordSecond(totalSeconds: Long): ListeningStats {
        val today = dateKey(Calendar.getInstance())
        val daily = loadDailyMap().toMutableMap()
        daily[today] = (daily[today] ?: 0L) + 1L
        pruneOldEntries(daily)
        saveDailyMap(daily)
        return computeStats(daily, totalSeconds)
    }

    fun getStats(totalSeconds: Long): ListeningStats =
        computeStats(loadDailyMap(), totalSeconds)

    private fun loadDailyMap(): Map<String, Long> {
        val raw = prefs.getString(KEY_DAILY_LISTENING, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    put(key, json.getLong(key))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun saveDailyMap(daily: Map<String, Long>) {
        val json = JSONObject()
        daily.forEach { (key, value) -> json.put(key, value) }
        prefs.edit().putString(KEY_DAILY_LISTENING, json.toString()).apply()
    }

    private fun pruneOldEntries(daily: MutableMap<String, Long>) {
        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -RETENTION_DAYS) }
        val cutoffKey = dateKey(cutoff)
        daily.keys.filter { it < cutoffKey }.forEach { daily.remove(it) }
    }

    private fun computeStats(daily: Map<String, Long>, totalSeconds: Long): ListeningStats {
        val today = Calendar.getInstance()
        val weekSeconds = sumForDays(today, daily, 7)
        val monthSeconds = sumForDays(today, daily, 30)
        return ListeningStats(
            weekSeconds = weekSeconds,
            monthSeconds = monthSeconds,
            streakDays = calculateStreak(daily, today),
            totalSeconds = totalSeconds
        )
    }

    private fun sumForDays(from: Calendar, daily: Map<String, Long>, dayCount: Int): Long {
        var total = 0L
        val cursor = from.clone() as Calendar
        repeat(dayCount) {
            total += daily[dateKey(cursor)] ?: 0L
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        return total
    }

    private fun calculateStreak(daily: Map<String, Long>, today: Calendar): Int {
        val todayKey = dateKey(today)
        val listenedToday = (daily[todayKey] ?: 0L) >= MIN_DAY_SECONDS

        val start = today.clone() as Calendar
        if (!listenedToday) {
            start.add(Calendar.DAY_OF_YEAR, -1)
        }

        var streak = 0
        val cursor = start.clone() as Calendar
        while ((daily[dateKey(cursor)] ?: 0L) >= MIN_DAY_SECONDS) {
            streak++
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    private fun dateKey(calendar: Calendar): String =
        String.format(
            Locale.US,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )

    companion object {
        private const val PREFS_NAME = "soundgroove_prefs"
        private const val KEY_DAILY_LISTENING = "daily_listening_json"
        private const val MIN_DAY_SECONDS = 60L
        private const val RETENTION_DAYS = 90
    }
}
