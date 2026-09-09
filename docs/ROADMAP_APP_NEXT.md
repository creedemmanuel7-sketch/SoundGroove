# Roadmap app — suite (août 2026)

Statut après backlog EQ / Soft-crossfade / Auto search / Rive. Priorité : **Fait** = utilisable en prod locale ; **Partiel** = fondation code sans UX complète ; **À faire** = non commencé ou documenté seulement.

| # | Fonctionnalité | Statut | Notes techniques |
|---|----------------|--------|------------------|
| 1 | Scrobbling / stats riches (local) | **Fait** | `ScrobbleRepository` — seuil 50 % / 4 min, prefs JSON `soundgroove_scrobble`, compteur dans Réglages. Top titres/artistes exposés via `scrobbleStats` (UI Profil à brancher / déjà partiel). Export JSON `exportEntriesJson()`. |
| 2 | Dossiers SAF comme source bibliothèque | **Fait** | `FolderLibraryRepository` + `CombinedMusicRepository`, picker SAF dans Réglages → « Ajouter un dossier », IDs négatifs pour URI SAF. |
| 3 | EQ presets + per-track | **Fait** | Toggle « Pour ce morceau » dans `EqualizerBottomSheet` ; prefs `track_eq_presets_json` ; `EqualizerManager.applyPreset(..., persistGlobal=false)` pour ne pas écraser le global au skip ; `currentTrackEqPinned` StateFlow. |
| 4 | Android Auto plus poussé | **Fait** | Browse + **recherche** (`onSearch` / `onGetSearchResult`) + smart playlists dans l’arbre Playlists (`AutoSearchMatcher`, `AutoLibraryCatalog`). |
| 5 | Widgets (2e taille / contrôles) | **Fait** | Layout compact vs large (seuils dp), bouton shuffle sur widget large, actions `WidgetActionReceiver`. |
| 6 | Import/export playlists + backup réglages | **Fait** | `BackupSnapshot` v2 + `PlaybackSettingsBackup` (gapless, crossfade, EQ, dossiers SAF, dossiers masqués, mode perf…). |
| 7 | Mode voiture / sombre auto | **Fait** | `CarModeScreen` plein écran, gros boutons, listes courtes (favoris/récents ≤ 6), sombre forcé, keep-screen-on. Entrée : Réglages (Confort) + Profil (raccourci). Route `Routes.CAR_MODE`. |
| 8 | Partage lien site + carte | **Fait** | `AppLinks.WEBSITE_URL` (`https://soundgroove.app`) injecté dans `PlayerActions.shareSong()`. Cartes PNG inchangées (`ShareCardGenerator`). |
| 9 | Tests smoke navigation + paroles | **Fait** | Unit : `AutoMediaIdsTest`, `AutoSearchMatcherTest`, `ScrobbleRepositoryTest`. Instrumenté Compose : `HomePlayerLyricsSmokeTest`. Doc CI : `docs/SMOKE_TEST_CHECKLIST.md`. |
| 10 | Perf listes (LazyColumn, Coil) | **Fait** | `key`/`contentType` + `SgCoverImage` sur listes majeures (soft-crossfade Coil / URI). |
| 11 | Taille app / cache — suivi | **Fait** | `StorageMaintenance.CacheBreakdown` étendu : Room, prefs, Coil + libellé « Données app » dans Réglages. |

## Fichiers clés (cette livraison)

```
app/src/main/java/com/credo/soundgroove/
  util/EqualizerManager.kt              (persistGlobal)
  ui/components/BottomSheets.kt         (EQ « Pour ce morceau »)
  ui/motion/SgCoverImage.kt / SgRiveAccent.kt
  auto/AutoLibraryCatalog.kt            (search + smart playlists)
  auto/AutoLibrarySessionCallback.kt    (onSearch / onGetSearchResult)
  auto/AutoSearchMatcher.kt
tools/generate_sg_pulse_riv.mjs         (générateur .riv optionnel)
docs/MOTION.md
```

## Prochaines étapes recommandées

1. Extraction `ListeningPageHeader` si Accueil / Bibliothèque / Profil convergent (reporté : refactor large).
2. Site vitrine (`website/`) — hors scope app.
3. Affiner l’asset Rive produit (`sg_pulse.riv` minimal généré ; design editor optionnel).

## Build

```bash
./gradlew.bat assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew.bat :app:testDebugUnitTest
```

Pas de commit website (consigne sprint).
