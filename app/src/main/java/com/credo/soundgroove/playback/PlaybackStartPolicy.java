package com.credo.soundgroove.playback;

/**
 * Politique UI / expand au tap — pur Java, testable JVM.
 * Le tap doit afficher l'état « en lecture » tout de suite ; le spinner ne
 * masque pas l'icône pause-en-lecture ; l'expand fenêtre n'a lieu qu'après
 * le premier audio, et seulement via addMediaItems (jamais un reset playlist).
 */
public final class PlaybackStartPolicy {

    public static final long BUFFERING_SPINNER_MAX_MS = 800L;

    private PlaybackStartPolicy() {}

    /**
     * Icône lecteur : play sticky dès le tap, jusqu'à pause utilisateur / erreur.
     * {@code true} = afficher l'icône pause (en lecture), jamais l'icône play « pausé ».
     */
    public static boolean showPlayingIcon(
            boolean stickyPlay,
            boolean playerIsPlaying,
            boolean userPaused
    ) {
        if (userPaused) return false;
        return stickyPlay || playerIsPlaying;
    }

    /**
     * Spinner : uniquement connexion / buffering réel, jamais si sticky play
     * (le tap ne doit plus remplacer l'icône par un loader puis une pause).
     */
    public static boolean showBufferingSpinner(
            boolean stickyPlay,
            boolean playerIsPlaying,
            boolean wantsPlay,
            boolean stateBuffering,
            boolean timedOut
    ) {
        if (stickyPlay || playerIsPlaying || timedOut) return false;
        return wantsPlay && stateBuffering;
    }

    public static boolean shouldClearStickyOnPause(boolean userRequestedPause) {
        return userRequestedPause;
    }

    public static boolean shouldExpandAfterFirstAudio(
            boolean pendingExpand,
            boolean firstAudioHeard,
            boolean generationCurrent,
            boolean mediaIdMatches
    ) {
        return pendingExpand && firstAudioHeard && generationCurrent && mediaIdMatches;
    }

    /**
     * Expand post-audio : seulement {@code addMediaItems} tant que le player
     * ne tient qu'un seul MediaItem (fast-start). Un {@code setMediaItems}
     * resetterait playWhenReady / le buffer.
     */
    public static boolean canExpandWithAddOnly(int mediaItemCount) {
        return mediaItemCount == 1;
    }

    public static boolean shouldResetPlaylistToExpand(int mediaItemCount) {
        return false;
    }

    /** Le tap d'un titre hors file ne doit pas dumper toute la bibliothèque. */
    public static int coldStartQueueSize(boolean alreadyInQueue, int currentQueueSize) {
        if (alreadyInQueue) return Math.max(0, currentQueueSize);
        return 1;
    }
}
