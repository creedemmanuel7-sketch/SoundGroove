# Roadmap app — suite (juillet 2026)

Statut après livraison Android Auto browse + Mode voiture + smoke Compose. Priorité : **Fait** = utilisable en prod locale ; **Partiel** = fondation code sans UX complète ; **À faire** = non commencé ou documenté seulement.

| # | Fonctionnalité | Statut | Notes techniques |
|---|----------------|--------|------------------|
| 1 | Scrobbling / stats riches (local) | **Fait** | `ScrobbleRepository` — seuil 50 % / 4 min, prefs JSON `soundgroove_scrobble`, compteur dans Réglages. Top titres/artistes exposés via `scrobbleStats` (UI Profil à brancher / déjà partiel). Export JSON `exportEntriesJson()`. |
| 2 | Dossiers SAF comme source bibliothèque | **Fait** | `FolderLibraryRepository` + `CombinedMusicRepository`, picker SAF dans Réglages → « Ajouter un dossier », IDs négatifs pour URI SAF. |
| 3 | EQ presets + per-track | **Partiel** | `PlaybackPreferences` map `track_eq_presets_json`, `EqualizerManager.applyForTrack()`, `setEqualizerPreset(preset, forCurrentTrack)`. **Manque** : toggle UI « Épingler pour ce morceau » dans `EqualizerBottomSheet`. |
| 4 | Android Auto plus poussé | **Fait** | `PlaybackService` = `MediaLibraryService` Media3. Racines browse : Playlists / Dossiers / Favoris (`AutoMediaIds`, `AutoLibraryCatalog`, `AutoLibrarySessionCallback`). `AutoSessionExtras` (métadonnées + shuffle) conservé. |
| 5 | Widgets (2e taille / contrôles) | **Fait** | Layout compact vs large (seuils dp), bouton shuffle sur widget large, actions `WidgetActionReceiver`. |
| 6 | Import/export playlists + backup réglages | **Fait** | `BackupSnapshot` v2 + `PlaybackSettingsBackup` (gapless, crossfade, EQ, dossiers SAF, dossiers masqués, mode perf…). |
| 7 | Mode voiture / sombre auto | **Fait** | `CarModeScreen` plein écran, gros boutons, listes courtes (favoris/récents ≤ 6), sombre forcé, keep-screen-on. Entrée : Réglages (Confort) + Profil (raccourci). Route `Routes.CAR_MODE`. |
| 8 | Partage lien site + carte | **Fait** | `AppLinks.WEBSITE_URL` (`https://soundgroove.app`) injecté dans `PlayerActions.shareSong()`. Cartes PNG inchangées (`ShareCardGenerator`). |
| 9 | Tests smoke navigation + paroles | **Fait** | Unit : `AutoMediaIdsTest`, `ScrobbleRepositoryTest`. Instrumenté Compose : `HomePlayerLyricsSmokeTest` (Home → Player → Lyrics via `SmokeNavGraph`). Doc CI : `docs/SMOKE_TEST_CHECKLIST.md`. |
| 10 | Perf listes (LazyColumn, Coil) | **Partiel** | `CoilImageConfig` — cache mémoire 20 %, disque 48 Mo, init dans `MainActivity`. LazyColumn déjà en place. **Manque** : `key`/`contentType` audit sur toutes les listes. |
| 11 | Taille app / cache — suivi | **Fait** | `StorageMaintenance.CacheBreakdown` étendu : Room, prefs, Coil + libellé « Données app » dans Réglages. |

## Fichiers clés (cette livraison)

```
app/src/main/java/com/credo/soundgroove/
  PlaybackService.kt                    (MediaLibraryService)
  auto/AutoMediaIds.kt
  auto/AutoLibraryCatalog.kt
  auto/AutoLibrarySessionCallback.kt
  auto/AutoSessionExtras.kt
  ui/screens/CarModeScreen.kt
  ui/navigation/AppNavigation.kt        (Routes.CAR_MODE)
app/src/androidTest/.../HomePlayerLyricsSmokeTest.kt
app/src/androidTest/.../SmokeNavGraph.kt
app/src/test/.../auto/AutoMediaIdsTest.kt
docs/SMOKE_TEST_CHECKLIST.md
```

## Prochaines étapes recommandées

1. **EQ per-track UX** — case « Pour ce morceau » dans `EqualizerBottomSheet` → `setEqualizerPreset(preset, forCurrentTrack = true)`.
2. **Perf listes** — audit `key` / `contentType` sur LazyColumn restantes.
3. **Android Auto** — recherche (`onGetSearchResult`) + playlists smart dans l’arbre si besoin.

## Build

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
# optionnel (émulateur) :
./gradlew :app:connectedDebugAndroidTest
```

Pas de commit (consigne sprint).
