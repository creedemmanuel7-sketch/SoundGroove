# Widget click crash fix — 2026-08-21

## Symptôme

Sur certains téléphones (OEM Transsion / Infinix notamment), un tap sur **n’importe quel contrôle widget** déclenche un message système du type « application bloquée / non groupée / attendre la mise à jour ». L’app devient inutilisable depuis le widget.

## Causes racines (vérifiées)

1. **`WidgetActionReceiver` avec `android:exported="false"`**  
   Même avec un `Intent` à composant explicite, certains launchers / OEM échouent à délivrer le `PendingIntent` broadcast. Le processus peut être tué ou l’action ignorée de façon opaque.

2. **Démarrage FGS via `MediaController` depuis un `BroadcastReceiver`**  
   Connecter un `MediaController` (SessionToken → `PlaybackService`) sans démarrer le service explicitement peut provoquer `ForegroundServiceStartNotAllowedException` (Android 12–15) si le bind démarre le FGS hors fenêtre autorisée.  
   Ancien code : `MoreExecutors.directExecutor()` sur le callback binder + `startActivity` dans le `catch` (BAL / crash).

3. **Updates RemoteViews fragiles**  
   `setColorFilter` via `RemoteViews.setInt`, ou bind d’IDs absents sur un layout, peut faire planter `updateAppWidget` et laisser le widget dans un état mort.

4. **`toggleFavorite` / Pulse `PLAY_URI`**  
   Même chemin MediaController / Room sans filet → exceptions non catchées + `pendingResult` non fini proprement.

## Correctifs

| Zone | Changement |
|------|------------|
| Manifest | `WidgetActionReceiver` **`exported="true"`** + `intent-filter` des actions custom uniquement (pas de `LAUNCHER`) |
| `PlaybackService.ensureStartedForWidget` | `startForegroundService` (try) → fallback `startService` |
| `WidgetActionReceiver` | `goAsync` + `finish()` toujours ; executor dédié (pas `directExecutor`) ; dispatch player sur main ; pas de `startActivity` dans le catch |
| `MusicAppWidgetProvider` | `try/catch` global + `runCatching` par bind ; `setColorFilter` gardé ; `FLAG_ACTIVITY_NEW_TASK` sur open-app |
| `PulseRecentWidgetProvider` | même durcissement `applyViews` / slots |

## Fichiers touchés (`app/` only)

- `AndroidManifest.xml`
- `widget/WidgetActionReceiver.kt`
- `widget/MusicAppWidgetProvider.kt`
- `widget/PulseRecentWidgetProvider.kt`
- `PlaybackService.kt` (`ensureStartedForWidget`)

## Vérif manuelle

1. Ajouter les widgets Music + Pulse sur l’écran d’accueil.
2. Tap play / pause / prev / next / shuffle / repeat / favori (large).
3. Pulse : tap « continuer » et une jaquette récente.
4. App en arrière-plan froid (process kill) puis retaper play depuis le widget.
5. Confirmer qu’aucun dialogue OEM « app bloquée » n’apparaît et que la lecture démarre / bascule.
