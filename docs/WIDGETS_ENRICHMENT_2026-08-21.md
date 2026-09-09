# Widgets SoundGroove — enrichissement 2026-08-21

## Avant

| Widget | Contenu |
|--------|---------|
| Player (resize compact / large) | Pochette URI, titre, artiste, prev / play-pause / next |
| Large seulement | Bouton shuffle (visibilité) |
| État | Prefs : title, artist, art, isPlaying, skin |
| Refresh | `PlaybackService` → `WidgetState.save` + `updateAllWidgets` sur play / metadata / transition |

## Après

### 1. Player compact
- Marque **SoundGroove** (accent violet)
- Pochette (cadre glass), titre, artiste
- **Barre de progression** simplifiée (0–100 %)
- Prev / play-pause / next
- Indicateurs **shuffle** + **repeat** (teint accent si actifs ; icône repeat-one)
- Fond dark violet / glass (stroke violet)
- Tap pochette / root → ouverture app

### 2. Player large / medium
- Tout le compact +
- Toggle **favori** (Room offline via `WidgetOfflineStore`)
- Bloc **À suivre** : 1–2 titres suivants de la file Media3
- Repeat + shuffle toujours visibles

### 3. Nouveau widget **Pulse**
- Ligne **Continuer** (dernière session `PlaybackSessionStore` + métadonnées Room)
- **3 jaquettes** récents (`recently_played`)
- Tap → `ACTION_PLAY_URI` (MediaController) ou ouverture app
- Refresh : transition de piste + `updatePeriodMillis` 30 min + `onEnabled`

### 4. Mises à jour vivantes
- Callbacks Media3 existants + shuffle / repeat
- Tick progression **4 s** pendant `isPlaying` (Handler, pas AlarmManager)
- Favori résolu en coroutine IO puis refresh UI widget

## Fichiers touchés (scope)

- `app/.../widget/*` (state, providers, receiver, offline store)
- `res/layout/widget_*`, `res/drawable/widget_*`, `res/xml/widget_*`
- `AndroidManifest` (receiver Pulse)
- `PlaybackService` (état enrichi + Pulse)
- DAO : requêtes read-only + `isFavoriteByUri` / `getRecentOnce` (pas de migration schema)

## Build

Pas de `assembleDebug` lancé ici (autres builds Java / Gradle actifs). Compiler sérialisé ensuite :

```text
.\gradlew.bat :app:assembleDebug
```
