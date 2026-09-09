package com.credo.soundgroove.playback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Présentation de file (sections, clés stables, durée restante) — pur Java / JVM.
 */
public final class QueuePresentation {

    private QueuePresentation() {}

    public static final class Sections {
        public final int historyStart;
        public final int historyEndExclusive;
        public final int nowPlaying;
        public final int upcomingStart;
        public final int upcomingEndExclusive;
        public final int historyCount;
        public final int upcomingCount;
        public final int totalCount;
        public final long remainingMs;

        public Sections(
                int historyStart,
                int historyEndExclusive,
                int nowPlaying,
                int upcomingStart,
                int upcomingEndExclusive,
                int totalCount,
                long remainingMs
        ) {
            this.historyStart = historyStart;
            this.historyEndExclusive = historyEndExclusive;
            this.nowPlaying = nowPlaying;
            this.upcomingStart = upcomingStart;
            this.upcomingEndExclusive = upcomingEndExclusive;
            this.historyCount = Math.max(0, historyEndExclusive - historyStart);
            this.upcomingCount = Math.max(0, upcomingEndExclusive - upcomingStart);
            this.totalCount = Math.max(0, totalCount);
            this.remainingMs = Math.max(0L, remainingMs);
        }

        public boolean hasHistory() {
            return historyCount > 0;
        }

        public boolean hasUpcoming() {
            return upcomingCount > 0;
        }

        public boolean hasNowPlaying() {
            return nowPlaying >= 0 && nowPlaying < totalCount;
        }
    }

    /**
     * Clé stable {@code id#occurrence} : deux titres identiques dans la file
     * restent distincts, et un reorder ne change pas la clé d'une occurrence.
     */
    public static String stableKey(long songId, int occurrence) {
        return songId + "#" + Math.max(0, occurrence);
    }

    public static List<String> stableKeys(long[] ids) {
        if (ids == null || ids.length == 0) return Collections.emptyList();
        Map<Long, Integer> seen = new HashMap<>();
        List<String> keys = new ArrayList<>(ids.length);
        for (long id : ids) {
            int occurrence = seen.getOrDefault(id, 0);
            seen.put(id, occurrence + 1);
            keys.add(stableKey(id, occurrence));
        }
        return keys;
    }

    public static List<String> stableKeys(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        long[] arr = new long[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            Long value = ids.get(i);
            arr[i] = value == null ? 0L : value;
        }
        return stableKeys(arr);
    }

    /** Durée restante = reliquat du titre en cours + toute la suite. */
    public static long remainingDurationMs(long[] durationsMs, int currentIndex, long positionMs) {
        if (durationsMs == null || durationsMs.length == 0) return 0L;
        int index = PlaybackWindowOps.clamp(currentIndex, 0, durationsMs.length - 1);
        long remainingCurrent = Math.max(0L, durationsMs[index] - Math.max(0L, positionMs));
        long upcoming = 0L;
        for (int i = index + 1; i < durationsMs.length; i++) {
            upcoming += Math.max(0L, durationsMs[i]);
        }
        return remainingCurrent + upcoming;
    }

    public static String formatRemaining(long durationMs) {
        if (durationMs < 1000L) return "0:00";
        long totalSeconds = durationMs / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    public static String remainingLabel(long durationMs, int upcomingCount, int totalCount) {
        String time = formatRemaining(durationMs);
        int titles = Math.max(0, totalCount);
        String count = titles == 1 ? "1 titre" : titles + " titres";
        if (upcomingCount > 0) {
            return time + " restant · " + count;
        }
        return time + " · " + count;
    }

    public static Sections split(int queueSize, int currentIndex, long[] durationsMs, long positionMs) {
        if (queueSize <= 0) {
            return new Sections(0, 0, -1, 0, 0, 0, 0L);
        }
        int now = PlaybackWindowOps.clamp(currentIndex, 0, queueSize - 1);
        long remaining = remainingDurationMs(
                durationsMs == null ? new long[queueSize] : durationsMs,
                now,
                positionMs
        );
        return new Sections(0, now, now, now + 1, queueSize, queueSize, remaining);
    }

    /** Après « Tout effacer » (upcoming), le courant reste, l'historique aussi. */
    public static int sizeAfterClearUpcoming(int queueSize, int currentIndex) {
        if (queueSize <= 0) return 0;
        int now = PlaybackWindowOps.clamp(currentIndex, 0, queueSize - 1);
        return now + 1;
    }

    public static int adjustCurrentAfterMove(int from, int to, int current) {
        if (from == to || from < 0 || to < 0) return current;
        if (current == from) return to;
        if (from < current && to >= current) return current - 1;
        if (from > current && to <= current) return current + 1;
        return current;
    }
}
