# Checklist QA device — SoundGroove

Audit code (2026-07-17). Cases à cocher **sur appareil réel**.  
Critères : **PASS** = conforme ; **FAIL** = régression / écart visible.

---

## 0. Prérequis

- [ ] APK debug installé, permission lecture audio accordée
- [ ] Bibliothèque locale avec ≥ 1 album (pochette), ≥ 1 artiste, titres `<unknown>` / filename
- [ ] Session « Continuer l’écoute » possible (lancer un titre, pause, revenir Accueil)

---

## 1. Critiques UI (5) + shared

### 1.1 CTA Accueil — un seul CTA

| | |
|---|---|
| **Code** | `HomeTab.kt` → `ContinueListeningSection` / `ResumeButton` (`filled = true` unique) |
| **Geste** | Accueil → carte « CONTINUER L’ÉCOUTE » |
| **PASS** | Un seul bouton primaire (Reprendre / Rejouer / Ouvrir le lecteur) ; pas de CTA ghost / secondaire |
| **FAIL** | Deux boutons côte à côte, ou label nu cliquable en plus du fill |

- [ ] PASS / FAIL

### 1.2 Bouton File — contraste

| | |
|---|---|
| **Code** | `PlayerScreen.kt` (~L976–1004) : `SurfaceElevated` + `TextPrimary` + border 0.28 |
| **Geste** | Ouvrir Player plein écran → bouton « File » |
| **PASS** | Icône + label lisibles sur thème Noir / Clair / Graphite (cible ≥ 4.5:1) |
| **FAIL** | Texte/icône lavés, confondus avec le scrim pochette |

- [ ] PASS / FAIL — Noir Absolu
- [ ] PASS / FAIL — Clair Argent
- [ ] PASS / FAIL — Graphite

### 1.3 Albums — scrim

| | |
|---|---|
| **Code** | `LibraryTab.kt` grille Albums : scrim bas `fillMaxHeight(0.52f)`, noir α≈0.48 |
| **Geste** | Bibliothèque → onglet Albums ; titres sur pochettes claires et sombres |
| **PASS** | Titre album + compteur lisibles ; dégradé bas visible |
| **FAIL** | Texte illisible sur pochette claire, ou scrim absent |

- [ ] PASS / FAIL

### 1.4 Mini-player unique

| | |
|---|---|
| **Code** | `MainScreen.kt` (HOME) + overlay `AppNavigation.kt` (autres routes, **pas** PLAYER) |
| **Geste** | Lancer un titre → Accueil / Bibliothèque / Recherche / Player |
| **PASS** | Au plus un mini-player visible ; **aucun** mini sur Player plein écran |
| **FAIL** | Double mini, ou mini + Player simultanés |

- [ ] PASS Accueil (mini intégré)
- [ ] PASS Recherche / Album / Artiste (overlay)
- [ ] PASS Player (pas de mini)
- [ ] PASS transition Accueil ↔ Recherche (pas de flash double)

### 1.5 Inset bas 152

| | |
|---|---|
| **Code** | `Design.kt` → `SgSpacing.contentInsetBottom = 152.dp` |
| **Appliqué** | `HomeTab`, `LibraryTab` (listes), `ProfileTab`, `SettingsScreen` |
| **Geste** | Scroller jusqu’en bas de chaque liste avec mini + nav visibles |
| **PASS** | Dernier item entièrement visible au-dessus du chrome bas |
| **FAIL** | Item masqué sous mini/nav |

- [ ] PASS / FAIL — Accueil
- [ ] PASS / FAIL — Bibliothèque (Chansons / Albums / Artistes / Playlists / Dossiers)
- [ ] PASS / FAIL — Profil
- [ ] PASS / FAIL — Paramètres
- [ ] Note Recherche : `SearchScreen` **n’utilise pas** `contentInsetBottom` (overlay mini seul) — vérifier si le dernier résultat est accessible

### 1.6 Shared elements

Réf. : `docs/FEATURES_C_SHARED_ELEMENT.md`, helpers `Motion.kt`.

| Transition | Clé | Chemins | Geste device |
|---|---|---|---|
| Mini → Player (pochette) | `album_art_<id>` | `MiniPlayer.kt` ↔ `PlayerScreen.kt` | Tap mini / swipe up |
| Mini → Player (métas) | `track_meta_<id>` | idem | idem |
| Mini → Player (play) | `play_control_<id>` | idem | idem |
| Album → détail | `album_cover_<encode>` | `LibraryTab` / `SearchScreen` → `AlbumDetailScreen` | Tap album |
| Artiste → détail | `artist_avatar_<encode>` | `LibraryTab` / `SearchScreen` → `ArtistDetailScreen` | Tap artiste |

- [ ] PASS / FAIL — morph pochette mini ↔ Player (fluide, pas de jump)
- [ ] PASS / FAIL — métas + play_control morphent / restent cohérents
- [ ] PASS / FAIL — Bibliothèque album → hero détail
- [ ] PASS / FAIL — Bibliothèque artiste → avatar détail
- [ ] PASS / FAIL — Recherche album / artiste → même morph
- [ ] PASS / FAIL — Mode performance / reduced motion : snap, pas de crash
- [ ] Note : tap chanson Recherche = play sans ouvrir Player → **pas** de shared `album_art_` / `play_control_` (attendu)

---

## 2. ThemePicker + edge-fade (état audit — re-check mid-run)

| Élément | État code (re-check) | Où regarder | Device |
|---|---|---|---|
| **ThemePicker** | **Partiel** : composant créé (`ui/components/ThemePicker.kt`) mais **aucun call site** encore (grep `ThemePicker(` = définition seule) | Attendre câblage Profil / Paramètres / onboarding | Après câblage : 3 thèmes, reveal origin, persistance |
| Sélecteur actuel | Toujours via `ProfileThemeChip` + `ThemeOptionRow` + `ThemeSelectionScreen` | Profil / Paramètres | Changer thème, vérifier reveal + persistance |
| **edge-fade** | **Absent** (aucune API / modifier nommé) | `HomeTab` LazyRow sans fade latéral | Scroll horizontal : coupure nette tant qu’absent |

- [ ] Re-check câblage ThemePicker (Profil / Paramètres / onboarding)
- [ ] Re-check edge-fade si agent UI l’ajoute
- [ ] PASS / FAIL — changement thème Profil ↔ Paramètres cohérent

---

## 3. Stats + métadonnées

### 3.1 Stats — source unique

| | |
|---|---|
| **Source** | `ListeningStatsRepository` → `ListeningStats` via `SoundGrooveViewModel.listeningStats` |
| **UI** | `ProfileTab` → `ProfileStatsSection` (semaine / mois / total / streak / jalon jour) |
| **PASS code** | Une seule source runtime pour les compteurs d’écoute Profil |

**Cas vraie lib :**

- [ ] Écouter ≥ 1 min → streak / « aujourd’hui » évoluent après retour Profil
- [ ] Semaine ≤ mois ≤ total (affichage cohérent)
- [ ] Compteurs Titres / Favoris = taille réelle lib (pas stats d’écoute)

### 3.2 SongDisplay — couverture

| | |
|---|---|
| **Utilitaire** | `util/SongDisplay.kt` (+ `Song.displayTitle/Artist/Album`) |
| **OK** | `SongItem`, `SongListItem`, `MiniPlayer`, `PlayerScreen`, Accueil continue, Search artistes, sheet infos (`BottomSheets`), Library artistes |
| **Écarts (raw `song.title` / `artist`)** | `AlbumDetailScreen`, `ArtistDetailScreen` (titres), `LibraryTab` (certaines rows), `QueueScreen`, `PlaylistDetailScreen`, `LyricsScreen`, infos legacy `MainScreen` |

**Cas vraie lib :**

- [ ] Titre `<unknown>` / vide → « Titre inconnu » ou filename nettoyé (listes via `SongItem`/`SongListItem`)
- [ ] Artiste unknown → « Artiste inconnu »
- [ ] Durée &lt; 1 s → pas de `0:00` trompeur sur listes couvertes
- [ ] FAIL attendu éventuel : mêmes titres sales encore visibles dans File / détail album / paroles (dépend agent data)

---

## 4. Smoke compile (optionnel)

```text
./gradlew :app:compileDebugKotlin
```

- [ ] Compile OK / KO

---

## Légende dépendances agents

| Zone | Owner |
|---|---|
| ThemePicker (câblage) + edge-fade | Agent UI (restes) |
| Raw metadata hors SongDisplay | Agent data |
| Checklist + tour device | Agent QA (ce doc) |
