package com.credo.soundgroove.playback;

/**
 * Seuils de fermeture de la file (drag / fling) — pur Java, testable JVM.
 */
public final class QueueSheetMotion {

    public static final float DEFAULT_THRESHOLD_DP = 64f;
    public static final float FLING_VELOCITY_PX_PER_S = 900f;

    private QueueSheetMotion() {}

    public static boolean shouldDismiss(float dragPx, float thresholdPx, float velocityYPxPerS) {
        if (dragPx >= thresholdPx) return true;
        return velocityYPxPerS >= FLING_VELOCITY_PX_PER_S && dragPx > thresholdPx * 0.28f;
    }

    /** Translation visuelle pendant le drag (jamais négative : on ne tire pas vers le haut). */
    public static float dragTranslationY(float dragPx) {
        return Math.max(0f, dragPx);
    }

    /**
     * Progrès 0–1 pour interpoler le morph bandeau↔file pendant un drag de fermeture.
     */
    public static float closeProgress(float dragPx, float sheetHeightPx) {
        if (sheetHeightPx <= 1f) return 0f;
        float p = dragPx / sheetHeightPx;
        if (p < 0f) return 0f;
        if (p > 1f) return 1f;
        return p;
    }
}
