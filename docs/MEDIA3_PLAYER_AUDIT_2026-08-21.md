# Media3 player audit — 2026-08-21

Audit Android app uniquement (`app/`). Croisement code SoundGroove vs reco Media3 / Android 14–16 (août 2026).

## Phase 1 — Recherche web (URLs)

| Sujet | Source |
|-------|--------|
| Versions Media3 | https://developer.android.com/jetpack/androidx/releases/media3 |
| Background playback / MediaSessionService | https://developer.android.com/media/media3/session/background-playback |
| MediaLibraryService (Auto / browse) | https://developer.android.com/media/media3/session/serve-content |
| API MediaSessionService | https://developer.android.com/reference/androidx/media3/session/MediaSessionService |
| API MediaLibraryService | https://developer.android.com/reference/androidx/media3/session/MediaLibraryService |
| Player de base (focus / noisy) | https://developer.android.com/media/implement/playback-app |
| Types FGS `mediaPlayback` | https://developer.android.com/develop/background-work/services/fgs/service-types |
| FGS types obligatoires (API 34+) | https://developer.android.com/about/versions/14/changes/fgs-types-required |
| Changements FGS Android 15 | https://developer.android.com/about/versions/15/changes/foreground-service-types |
| MediaButtonReceiver (sources) | https://github.com/androidx/media/blob/release/libraries/session/src/main/java/androidx/media3/session/MediaButtonReceiver.java |
| Playback resumption (issue/docs) | https://github.com/androidx/media/issues/27 |

### Reco 2026 (synthèse)

- **Stable Media3** : `1.10.1` (22 juil. 2026) ; RC `1.11.0-rc01`.
- **Service** : `MediaSessionService` pour la lecture seule ; **`MediaLibraryService`** si browse (Android Auto). Déclarer `foregroundServiceType="mediaPlayback"` + permissions `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
- **Intent-filters** : `androidx.media3.session.MediaLibraryService` (+ `MediaSessionService` ok) **et** `android.media.browse.MediaBrowserService` pour clients legacy / Auto.
- **Notification** : `DefaultMediaNotificationProvider` ; le service passe en FGS pendant la lecture engagée, redevient background quand tout est stoppé. Timeout FGS configurable (`setForegroundServiceTimeoutMs`, défaut ~10 min après pause).
- **Audio focus** : `ExoPlayer.Builder.setAudioAttributes(..., handleAudioFocus=true)` avec `USAGE_MEDIA`.
- **Casque / BT débranché** : `setHandleAudioBecomingNoisy(true)` (pas besoin d’un `BroadcastReceiver` manuel `AUDIO_BECOMING_NOISY` si ExoPlayer le gère).
- **Wake** : `WAKE_MODE_LOCAL` + permission `WAKE_LOCK` pour lecture locale.
- **Gapless** : natif ExoPlayer sur timeline concat (encoder gapless tags) ; crossfade « vrai » = deux décodeurs (hors scope actuel = fondu volume).
- **Reprise après kill** : déclarer `MediaButtonReceiver` + implémenter `onPlaybackResumption` ; sinon boutons média / System UI ne relancent pas le service mort.
- **Doze / Standby** : un FGS `mediaPlayback` **actif** (lecture + notif ongoing) maintient le process ; **ne garantit pas** la survie indéfinie en pause profonde, ni contre OEM agressifs. Android 15+ : pas de démarrage FGS media depuis `BOOT_COMPLETED`.

## Phase 2 — Checklist vs code

| # | Sujet | Avant | Action |
|---|--------|-------|--------|
| 1 | FGS + MediaSession | `MediaLibraryService` + `mediaPlayback` OK ; manque `MediaBrowserService` ; Media3 **1.3.1** (très en retard) | Bump **1.10.1** ; ajouter intent `MediaBrowserService` |
| 2 | Audio focus / noisy | Déjà `handleAudioFocus` + `setHandleAudioBecomingNoisy` | Conserver ; documenter |
| 3 | Gapless / crossfade | Timeline Media3 + `CrossfadeController` (volume) ; buffers 350 / 20–60 s | Garder first-play ; plafonner bytes buffer |
| 4 | Mémoire | Listeners détachés (service + VM) ; pas de `SimpleCache` disque | Cap `targetBufferBytes` ; MBR sans fuite |
| 5 | Formats MP3/FLAC/AAC/OGG | `DefaultExtractorsFactory` | CBR seeking ; messages erreur + skip ; tests |
| 6 | Doze / arrière-plan | FGS + wake + notif | Timeout FGS explicite ; doc limites |
| 7 | Contrôles externes | Notif Media3 + Auto (`AutoLibrary*`) | + `MediaButtonReceiver` / `onPlaybackResumption` ; **Wear OS = N/A** |
| 8 | Reprise kill | `PlaybackSessionStore` + restore pause (pas play avant prêt) | Renforcer store + resumption système |

## Correctifs livrés (Phase 3)

| Correctif | Détail |
|-----------|--------|
| Media3 **1.3.1 → 1.10.1** | exoplayer / extractor / ui / session alignés |
| Manifest | + `android.media.browse.MediaBrowserService` ; + `MediaButtonReceiver` |
| `onPlaybackResumption` | Restore file via `PlaybackSessionStore` + catalogue Auto |
| Extractors | `setConstantBitrateSeekingEnabled(true)` (MP3/AAC seek) |
| LoadControl | `maxBuffer` 45 s ; `targetBufferBytes` 24 Mo ; first-play 350 ms inchangé |
| FGS timeout | `setForegroundServiceTimeoutMs(15 min)` |
| Erreurs | skip en ignorant `invalidMediaIds` ; message « corrompu » élargi |
| Session store | file plafonnée (256), position bornée, parse testable |
| Restore UI | `playWhenReady=false` + flags UI non-playing (pas de régression) |

## N/A

- **Wear OS** : aucun module / code Wear dans `app/` — hors scope.
- **Vrai crossfade dual-ExoPlayer** : non demandé ; mono-player volume conservé.
- **Exemption Doze totale** : impossible pour une app store classique.

## Risques restants

- OEM (Xiaomi, Huawei…) peuvent tuer le process malgré FGS.
- Doze après longue pause (FGS timeout écoulé) : reprise via notif / MBR / cold start app.
- Cold-start `MediaController` (~quelques secondes) inchangé.
- Gapless dépend des tags encoder ; crossfade volume ≠ overlap décodeurs.
- `onPlayerCommandRequest` déprécié Media3 1.10 — conservé pour le seuil « previous > 3 s ».

## Build

- Tests filtrés : `PlayerGuardsErrorMessageTest`, `PlaybackSessionStoreTest` — **OK**
- `assembleDebug` — **OK**
- APK : `app/build/outputs/apk/debug/app-debug.apk`
