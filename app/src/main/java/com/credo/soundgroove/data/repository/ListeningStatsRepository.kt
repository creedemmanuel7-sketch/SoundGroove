package com.credo.soundgroove.data.repository

import android.content.Context
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

data class ListeningStats(
    val weekSeconds: Long,
    val monthSeconds: Long,
    val streakDays: Int,
    val totalSeconds: Long,
    // Jalons discrets Profil : temps d'écoute du jour (`daily[todayKey]`).
    val todaySeconds: Long = 0L
)

/**
 * Source de vérité unique des stats d'écoute.
 *
 * - **Semaine / mois** : fenêtres **calendaires** (début de semaine locale → aujourd'hui,
 *   1er du mois → aujourd'hui), sommées depuis `daily_listening_json`.
 * - **Total** : compteur lifetime `total_listening_seconds` (prefs), borné au moins
 *   à la somme journalière retenue (migration / écarts).
 *
 * Important : une semaine calendaire n'est **pas** un sous-ensemble du mois calendaire
 * (ex. début juillet qui inclut fin juin). On ne force donc plus mois ≥ semaine —
 * l'ancien `coerceAtLeast(week)` collapsait mois (= semaine) puis souvent total
 * quand l'historique récent dominait, d'où trois pastilles identiques.
 */
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
        val weekSeconds = sumCalendarWeek(today, daily)
        val monthSeconds = sumCalendarMonth(today, daily)
        val dailySum = daily.values.sum()
        // Lifetime : prefs ; floor sur la carte journalière si elle est en avance
        // (pas de plafond sur semaine/mois — fenêtres indépendantes).
        val resolvedTotal = maxOf(totalSeconds.coerceAtLeast(0L), dailySum)
        return ListeningStats(
            weekSeconds = weekSeconds,
            monthSeconds = monthSeconds,
            streakDays = calculateStreak(daily, today),
            totalSeconds = resolvedTotal,
            todaySeconds = daily[dateKey(today)] ?: 0L
        )
    }

    /** Du premier jour de la semaine locale jusqu'à aujourd'hui inclus. */
    private fun sumCalendarWeek(today: Calendar, daily: Map<String, Long>): Long {
        val start = today.clone() as Calendar
        val firstDay = start.firstDayOfWeek
        while (start.get(Calendar.DAY_OF_WEEK) != firstDay) {
            start.add(Calendar.DAY_OF_YEAR, -1)
        }
        return sumInclusiveRange(start, today, daily)
    }

    /** Du 1er du mois calendaire jusqu'à aujourd'hui inclus. */
    private fun sumCalendarMonth(today: Calendar, daily: Map<String, Long>): Long {
        val start = today.clone() as Calendar
        start.set(Calendar.DAY_OF_MONTH, 1)
        return sumInclusiveRange(start, today, daily)
    }

    private fun sumInclusiveRange(from: Calendar, to: Calendar, daily: Map<String, Long>): Long {
        var total = 0L
        val cursor = from.clone() as Calendar
        // Garde-fou : max 62 jours (semaine chevauchante + mois)
        var guard = 0
        while (!cursor.after(to) && guard < 62) {
            total += daily[dateKey(cursor)] ?: 0L
            cursor.add(Calendar.DAY_OF_YEAR, 1)
            guard++
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
