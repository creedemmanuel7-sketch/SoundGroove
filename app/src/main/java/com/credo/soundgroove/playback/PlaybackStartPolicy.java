package com.credo.soundgroove.playback;

/**
 * Politique UI / expand au tap — pur Java, testable JVM.
 * UI honnête : icône pause seulement si le player joue vraiment ; spinner court
 * pendant le prepare. Fast-start 1 MediaItem ; expand après le premier sample
 * (addMediaItems, jamais un reset playlist). Pas d'expand à un délai fixe.
 */
public final class PlaybackStartPolicy {

    /** Spinner honnête pendant le prepare local — assez court pour ne pas masquer un échec. */
    public static final long BUFFERING_SPINNER_MAX_MS = 2_500L;

    private PlaybackStartPolicy() {}

    /** Icône pause uniquement si ExoPlayer joue. Pas de sticky UI. */
    public static boolean showPlayingIcon(boolean playerIsPlaying) {
        return playerIsPlaying;
    }

    /**
     * Spinner si on a demandé play et que le player n'a pas encore de rendu.
     * {@code playWhenReady} sans {@code isPlaying} = chargement, pas une pause.
     */
    public static boolean showBufferingSpinner(
            boolean playerIsPlaying,
            boolean wantsPlay,
            boolean stateBuffering,
            boolean controllerConnecting,
            boolean timedOut
    ) {
        if (playerIsPlaying || timedOut) return false;
        if (!wantsPlay && !controllerConnecting) return false;
        return stateBuffering || controllerConnecting || wantsPlay;
    }

    /**
     * Toggle : pause seulement si ça joue, ou si le spinner de chargement est visible
     * (annulation honnête). Jamais pause juste parce qu'on a tapé une piste.
     */
    public static boolean shouldPauseOnToggle(
            boolean playerIsPlaying,
            boolean playWhenReady,
            boolean bufferingVisible
    ) {
        if (playerIsPlaying) return true;
        return playWhenReady && bufferingVisible;
    }

    /** Premier sample : {@code isPlaying} ou position &gt; 0. STATE_READY seul ne suffit pas. */
    public static boolean isFirstAudioHeard(boolean playerIsPlaying, long positionMs) {
        return playerIsPlaying || positionMs > 0L;
    }

    /**
     * Expand après le premier sample — jamais au {@code play()} ni après un délai
     * arbitraire. {@code addMediaItems} trop tôt retarde / casse le premier rendu.
     * Skip utilise la file logique ({@code seekLogicalIndex} / reshape), pas cette fenêtre.
     */
    public static boolean shouldExpandAfterFirstAudio(
            boolean pendingExpand,
            boolean firstAudioHeard,
            boolean generationCurrent,
            boolean mediaIdMatches
    ) {
        return pendingExpand && firstAudioHeard && generationCurrent && mediaIdMatches;
    }

    public static boolean canExpandWithAddOnly(int mediaItemCount) {
        return mediaItemCount == 1;
    }

    public static boolean shouldResetPlaylistToExpand(int mediaItemCount) {
        return false;
    }

    public static boolean preferInProcessPlayer(boolean localPlayerAvailable) {
        return localPlayerAvailable;
    }

    /**
     * Tap hors file : garder la bibliothèque comme file logique (skip),
     * sans tout pousser sur le binder (fast-start 1 MediaItem).
     */
    public static boolean useCatalogAsLogicalQueue(boolean alreadyInQueue, int catalogSize) {
        return !alreadyInQueue && catalogSize > 1;
    }
}
