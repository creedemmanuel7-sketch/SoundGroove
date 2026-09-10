package com.credo.soundgroove.playback;

import java.util.List;
import java.util.Objects;

/**
 * Moteur pur de fenêtre Media3 autour de l'index logique.
 * Pas d'Android / Media3 ici : tests JVM et mapping O(1) logical ↔ player.
 */
public final class PlaybackWindowOps {

    public static final int DEFAULT_RADIUS = 32;
    public static final int DEFAULT_HYSTERESIS = 8;

    private PlaybackWindowOps() {}

    /** Fenêtre [start, endExclusive) dans la file logique. */
    public static final class Window {
        public final int start;
        public final int endExclusive;
        public final int logicalIndex;
        public final int playerIndex;

        public Window(int start, int endExclusive, int logicalIndex) {
            this.start = start;
            this.endExclusive = endExclusive;
            this.logicalIndex = logicalIndex;
            this.playerIndex = logicalIndex - start;
        }

        public int size() {
            return Math.max(0, endExclusive - start);
        }

        public boolean containsLogical(int index) {
            return index >= start && index < endExclusive;
        }

        public int toPlayerIndex(int logical) {
            if (!containsLogical(logical)) return -1;
            return logical - start;
        }

        public int toLogicalIndex(int player) {
            if (player < 0 || player >= size()) return -1;
            return start + player;
        }

        /** Distance du courant au bord interne de la fenêtre (0 = collé au bord). */
        public int distanceToEdge() {
            int left = logicalIndex - start;
            int right = (endExclusive - 1) - logicalIndex;
            return Math.min(left, right);
        }

        public boolean atLibraryStart() {
            return start == 0;
        }

        public boolean atLibraryEnd(int queueSize) {
            return endExclusive >= queueSize;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Window)) return false;
            Window window = (Window) o;
            return start == window.start
                    && endExclusive == window.endExclusive
                    && logicalIndex == window.logicalIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, endExclusive, logicalIndex);
        }

        @Override
        public String toString() {
            return "Window{start=" + start + ", end=" + endExclusive
                    + ", logical=" + logicalIndex + ", player=" + playerIndex + "}";
        }
    }

    public static Window compute(int logicalIndex, int queueSize, int radius) {
        if (queueSize <= 0) {
            return new Window(0, 0, 0);
        }
        int safeIndex = clamp(logicalIndex, 0, queueSize - 1);
        int safeRadius = Math.max(0, radius);
        int start = Math.max(0, safeIndex - safeRadius);
        int endExclusive = Math.min(queueSize, safeIndex + safeRadius + 1);
        return new Window(start, endExclusive, safeIndex);
    }

    public static Window compute(int logicalIndex, int queueSize) {
        return compute(logicalIndex, queueSize, DEFAULT_RADIUS);
    }

    /**
     * Re-expand seulement près des bords de la fenêtre, pas à chaque transition.
     * Si la fenêtre touche déjà le début/fin de bibliothèque du côté concerné : no-op.
     */
    public static boolean shouldReExpand(
            Window current,
            int logicalIndex,
            int queueSize,
            int radius,
            int hysteresis
    ) {
        if (current == null || queueSize <= 0) return false;
        int safeIndex = clamp(logicalIndex, 0, queueSize - 1);
        Window desired = compute(safeIndex, queueSize, radius);
        if (current.start == desired.start && current.endExclusive == desired.endExclusive) {
            return false;
        }
        int edge = Math.max(1, hysteresis);
        boolean nearLeft = safeIndex - current.start <= edge;
        boolean nearRight = current.endExclusive - 1 - safeIndex <= edge;
        if (nearLeft && !current.atLibraryStart()) return true;
        if (nearRight && !current.atLibraryEnd(queueSize)) return true;
        // Courant hors fenêtre (désync) : toujours ré-aligner.
        return !current.containsLogical(safeIndex);
    }

    public static boolean shouldReExpand(Window current, int logicalIndex, int queueSize) {
        return shouldReExpand(current, logicalIndex, queueSize, DEFAULT_RADIUS, DEFAULT_HYSTERESIS);
    }

    /**
     * Empreinte O(1) : bornes + premier / dernier / courant + taille.
     * Remplace N appels binder {@code getMediaItemAt}.
     */
    public static String fingerprint(
            int start,
            int endExclusive,
            String firstMediaId,
            String lastMediaId,
            String currentMediaId,
            int size
    ) {
        return start + "|" + endExclusive + "|" + size + "|"
                + nullToEmpty(firstMediaId) + "|"
                + nullToEmpty(lastMediaId) + "|"
                + nullToEmpty(currentMediaId);
    }

    public static String fingerprint(List<String> mediaIds, Window window) {
        if (mediaIds == null || window == null || window.size() == 0) {
            return fingerprint(0, 0, "", "", "", 0);
        }
        int last = window.endExclusive - 1;
        String first = getOrEmpty(mediaIds, window.start);
        String lastId = getOrEmpty(mediaIds, last);
        String current = getOrEmpty(mediaIds, window.logicalIndex);
        return fingerprint(window.start, window.endExclusive, first, lastId, current, window.size());
    }

    /**
     * Le player tient déjà cette fenêtre (taille + premier + dernier) —
     * 2–3 lectures binder, jamais un scan N.
     */
    public static boolean isPlayerHoldingWindow(
            int playerCount,
            String playerFirstId,
            String playerLastId,
            List<String> logicalMediaIds,
            Window window
    ) {
        if (window == null || logicalMediaIds == null || window.size() == 0) return false;
        String expectedFirst = getOrEmpty(logicalMediaIds, window.start);
        String expectedLast = getOrEmpty(logicalMediaIds, window.endExclusive - 1);
        return isPlayerHoldingWindow(
                playerCount,
                playerFirstId,
                playerLastId,
                expectedFirst,
                expectedLast,
                window.size()
        );
    }

    /**
     * Variante O(1) : 2 ids attendus, pas un {@code List} de toute la bibliothèque.
     */
    public static boolean isPlayerHoldingWindow(
            int playerCount,
            String playerFirstId,
            String playerLastId,
            String expectedFirstId,
            String expectedLastId,
            int expectedSize
    ) {
        if (expectedSize <= 0 || playerCount != expectedSize) return false;
        return nullToEmpty(expectedFirstId).equals(nullToEmpty(playerFirstId))
                && nullToEmpty(expectedLastId).equals(nullToEmpty(playerLastId));
    }

    /**
     * Fast-seek si le player tient déjà la fenêtre attendue (3 ids, pas N).
     */
    public static boolean canFastSeekWindow(
            int playerCount,
            String playerFirstId,
            String playerLastId,
            String playerCurrentId,
            int playerCurrentIndex,
            List<String> logicalMediaIds,
            int logicalIndex,
            int radius
    ) {
        if (logicalMediaIds == null || logicalMediaIds.isEmpty()) return false;
        Window window = compute(logicalIndex, logicalMediaIds.size(), radius);
        if (playerCount != window.size()) return false;
        if (playerCurrentIndex != window.playerIndex) return false;
        String expectedFirst = getOrEmpty(logicalMediaIds, window.start);
        String expectedLast = getOrEmpty(logicalMediaIds, window.endExclusive - 1);
        String expectedCurrent = getOrEmpty(logicalMediaIds, window.logicalIndex);
        return Objects.equals(expectedFirst, nullToEmpty(playerFirstId))
                && Objects.equals(expectedLast, nullToEmpty(playerLastId))
                && Objects.equals(expectedCurrent, nullToEmpty(playerCurrentId));
    }

    /** Seek intra-fenêtre : le titre cible est déjà chargé dans le player. */
    public static int playerIndexForLogical(Window window, int logicalIndex) {
        if (window == null) return -1;
        return window.toPlayerIndex(logicalIndex);
    }

    public static int logicalIndexFromPlayer(Window window, int playerIndex) {
        if (window == null) return -1;
        return window.toLogicalIndex(playerIndex);
    }

    /**
     * Résout l'index logique à partir du mediaId courant, en privilégiant
     * la fenêtre (O(fenêtre)) puis un scan borné — jamais un N binder.
     */
    public static int resolveLogicalIndex(
            List<String> logicalMediaIds,
            String currentMediaId,
            int hintLogicalIndex
    ) {
        if (logicalMediaIds == null || logicalMediaIds.isEmpty()) return 0;
        String id = nullToEmpty(currentMediaId);
        if (id.isEmpty()) {
            return clamp(hintLogicalIndex, 0, logicalMediaIds.size() - 1);
        }
        int hint = clamp(hintLogicalIndex, 0, logicalMediaIds.size() - 1);
        if (id.equals(logicalMediaIds.get(hint))) return hint;
        Window window = compute(hint, logicalMediaIds.size(), DEFAULT_RADIUS);
        for (int i = window.start; i < window.endExclusive; i++) {
            if (id.equals(logicalMediaIds.get(i))) return i;
        }
        // Doublons possibles : scan complet en dernier recours (liste mémoire, pas binder).
        for (int i = 0; i < logicalMediaIds.size(); i++) {
            if (id.equals(logicalMediaIds.get(i))) return i;
        }
        return hint;
    }

    public static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String getOrEmpty(List<String> ids, int index) {
        if (ids == null || index < 0 || index >= ids.size()) return "";
        return nullToEmpty(ids.get(index));
    }
}
