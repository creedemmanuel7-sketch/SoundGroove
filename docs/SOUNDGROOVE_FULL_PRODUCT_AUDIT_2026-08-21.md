# SoundGroove — Audit produit complet (PM + Archi + UX)
**Date :** 2026-08-21  
**Scope :** app Android uniquement (`app/`).  
**Contrainte session :** pipeline ExoPlayer / `isBuffering` / spinner play — **non modifié** (autre agent).

---

## Résumé exécutif (1 page)

SoundGroove est un **lecteur musique locale premium** (MediaStore + SAF, pas de compte cloud). Le cœur produit — bibliothèque, player Media3, mini-player morph, paroles, file, EQ, Auto, widgets — est **largement livré**. Le problème n’est plus « manque de features », c’est **sur-surface + God ViewModel + fantômes UX** qui diluent la promesse Glass / Phonk.

**Verdict produit :** app crédible face à Musicolet sur le local, en retard vs Poweramp sur audio pro / organisation, et vs Spotify/YT Music sur **découverte et continuité émotionnelle**. La différenciation réelle n’est pas l’EQ ni le remote LAN : c’est le **rituel d’écoute glass + morph pochette + paroles**, à condition de **couper le gras** et de **boucher les boutons morts**.

**3 quick wins à ajouter :** (1) sélecteur playlist unifié partout où `onAddToPlaylist` est fantôme ; (2) crossfade pochette / titre au skip (continuité) ; (3) « Mix Phonk / ambiance » intelligent depuis scrobble local sur Accueil.  
**À retirer / simplifier :** mode vinyle décoratif, remote host LAN (outil niche), Profil redondant avec Paramètres.  
**Archi :** monolith `:app` + `SoundGrooveViewModel` omnipotent — **modularisation Gradle prématurée** ; prioriser extraction de domaines (Library / PlayerSession / Settings) en packages + UI state.

---

## 1. Fonctionnalités

### 1.1 Inventaire par groupe

#### Essentielles (lecteur local)

| Feature | Statut | Notes |
|---------|--------|-------|
| Scan MediaStore + lecture locale | **Complète** | Catalogue hors Room |
| Dossiers SAF (sources) | **Complète** | Réglages → ajouter dossier |
| Lecture / pause / skip / seek | **Complète** | Media3 + service FGS |
| File d’attente (queue UI + reorder) | **Complète** | Overlay split Player |
| Shuffle / repeat | **Complète** | |
| Favoris | **Complète** | Room |
| Playlists manuelles CRUD | **Complète** | Détail playlist OK ; **picker fantôme** ailleurs |
| Recherche (titres / albums / artistes / dossiers / paroles) | **Complète** | Route dédiée |
| Mini-player persistant | **Complète** | Overlay global + prefs |
| Player plein écran + gestes | **Complète** | Swipe down/up, peek paroles |
| Notifications media / session | **Complète** | |
| Permissions audio empty states | **Complète** | |
| Reprise de session | **Complète** | `PlaybackSessionStore` |

#### Confort

| Feature | Statut | Notes |
|---------|--------|-------|
| Paroles sync + offset + web paste | **Complète** | Overlay + LRCLIB |
| EQ presets + per-track | **Complète** | |
| Gapless + crossfade | **Complète** | |
| Vitesse / pitch | **Complète** | |
| Sleep timer | **Complète** | |
| Édition métadonnées / cover override | **Complète** | |
| Smart playlists | **Complète** | Bibliothèque |
| Continuer l’écoute / récents | **Complète** | Accueil |
| Scrobble / stats locales | **À moitié** | Repo OK ; Profil partiel vs Settings |
| Backup import/export | **Complète** | |
| Widgets | **Complète** | Compact + large |
| Android Auto browse + search | **Complète** | |
| Mode voiture | **Complète** | Route `CAR_MODE` |
| Thèmes / accents / accent pochette | **Complète** | Dynamic wallpaper **volontairement off** |
| Mode performance / reduced motion | **Complète** | |
| Partage texte + carte PNG | **Complète** | |
| Masquer dossiers | **Complète** | |
| Ringtone | **Complète** | Via sheets |

#### Gadget / différenciation

| Feature | Statut | Notes |
|---------|--------|-------|
| Glassmorphism / blur contrôlé | **Complète** | Tokens + gate perf |
| Shared element mini ↔ player | **Complète** | |
| Vinyl mode | **Esquissée / gadget** | Toggle décoratif |
| Lottie EQ bars / Rive pulse | **À moitié** | Présents, impact produit faible |
| Remote host LAN (WebSocket) | **À moitié** | Niche ; complexité sécu/support |
| Mix du jour Accueil | **Esquissée** | Heuristique locale, pas « killer » |
| Gesture hints one-shot | **Complète** | |
| Profile avatar / pseudo local | **Complète** | Pas de compte |

### 1.2 Fantômes (bouton / callback sans action réelle)

Critiques — **dette UX #1** :

| Emplacement | Callback | Effet |
|-------------|----------|-------|
| `AppNavigation` → Search | `onMenuClick = { }` | Menu morceau no-op |
| Search / Album / Artist / Playlist détail | `onAddToPlaylist = { /* … */ }` | « Ajouter à une playlist » **mort** hors Bibliothèque |
| Folder detail | `onShowSongInfo` / `onShowPlaylistPicker` | Stubs commentés |
| Defaults Compose (`= {}`) | Nombreux | OK pour previews ; **dangereux** quand branchés vides en prod |

### 1.3 Redondances à fusionner

1. **Profil ↔ Paramètres** : thème, perf, notifs, backup, car mode, stats — double porte.
2. **SongItem vs SongListItem** + `formatDuration` wrapper — deux listes item.
3. **Sheets Player** (options → EQ / speed / crossfade / sleep) **et** mêmes entrées Settings — acceptable si hub unique ; aujourd’hui **deux hiérarchies**.
4. **Recherche** : tab bottom bar **et** route push — le tab 2 ne sélectionne pas un onglet, il navigue (OK) mais l’état selectedTab peut **désynchroniser** le highlight.
5. **Library sous-vues** (album/artiste/folder inline) **et** routes NavHost détail — deux modèles de drill-down.

### 1.4 Benchmark concurrent (local / offline UX 2026)

| Capacité 2026 | Poweramp | Musicolet | YT Music (local-like) | Spotify offline | SoundGroove |
|---------------|----------|-----------|------------------------|-----------------|-------------|
| Organisation dossiers / tags pro | ★★★★★ | ★★★★ | ★★ | ★ | ★★★ |
| Qualité audio / DSP / hi-res UI | ★★★★★ | ★★ | ★★ | ★★ | ★★★ (EQ ok) |
| Simplicité / vitesse scan | ★★★ | ★★★★★ | ★★★ | ★★★ | ★★★★ |
| Découverte / mixes | ★ | ★★ | ★★★★ | ★★★★★ | ★★ (Mix du jour faible) |
| Paroles | ★★ | ★★ | ★★★★ | ★★★★ | ★★★★ |
| Identité visuelle / motion | ★★ | ★ | ★★★★ | ★★★★★ | ★★★★ (glass) |
| Multi-device / sync | ★★ | ★ | ★★★★★ | ★★★★★ | ★ (remote LAN niche) |
| Offline-first trust | ★★★★★ | ★★★★★ | ★★★ | ★★★★ | ★★★★★ |

**Manques 2026 les plus coûteux pour SoundGroove :**
- **Ajout playlist contextuel partout** (table stakes Musicolet/Poweramp).
- **Tri / filtres bibliothèque avancés** (année, bitrate, durée, « non joué »).
- **Découverte locale** digne (mood / energy / similarité légère via tags ou historique) — sans cloud.
- **Continuité visuelle au skip** (pochette/titre) — Spotify-level polish attendu en 2026.
- **Pas** de sync cloud (acceptable) mais alors **backup** doit être *évident* (déjà là, mal mis en avant).

### 1.5 3 quick wins à AJOUTER

1. **PlaylistPickerSheet global** branché sur tous les `onAddToPlaylist` / `onShowPlaylistPicker` fantômes (1 composant, 1 ViewModel API déjà existante `addSongsToPlaylist`).
2. **Crossfade titre/pochette au changement de piste** (`AnimatedContent` keyed by `song.id`) sur Player + MiniPlayer — gain « premium » immédiat.
3. **Carte Accueil « Pulse local »** : 1 mix généré depuis scrobble + favoris + récents (pas un 5ᵉ carousel générique).

### 1.6 2–3 à RETIRER / simplifier

1. **Vinyl mode** — gadget ; coût cognitive dans Options Player.
2. **Remote host LAN** — sortir du chemin critique (feature flag / Advanced only) ou retirer jusqu’à productisation.
3. **Profil comme 4ᵉ tab** — fusionner en « Compte local » dans Settings + raccourcis Accueil ; récupérer un slot bottom bar (ex. Playlists) **ou** réduire à 3 tabs.

### 1.7 Killer feature vs générique

**Aujourd’hui générique :** EQ + gapless + playlists + search = Musicolet+.

**Killer possible (axe Glass / Phonk premium) :**
> *« Listening stage »* — Player + paroles peek + morph glass comme **une seule scène immersive** (pas un lecteur + overlays empilés), avec accents pochettes et motion SgMotion cohérente, **sans** features labo.

Différenciation = **rituel d’écoute visuelle locale**, pas « encore un EQ ».

---

## 2. Architecture

### 2.1 Packages / couches

```
com.credo.soundgroove/
  data/          model, db, repository, backup   ← data OK
  viewmodel/     SoundGrooveViewModel (god), Home, Search, Lyrics
  ui/            screens, components, navigation, theme, motion
  util/          prefs, guards, EQ, covers…      ← fourre-tout
  lyrics/, auto/, remote/, widget/               ← features isolées
  PlaybackService, Database, MainActivity        ← racine
```

**Verdict :** couches **esquissées** (data vs ui) mais **pas clean**. Domain absent (use cases). `util/` = dumping ground. UI appelle directement le God VM.

### 2.2 MVVM / God ViewModel

`SoundGrooveViewModel` concentre : catalogue, favoris, playlists, session MediaController, queue, EQ, remote, backup, thème, sort, stats, sleep timer, metadata…  
`HomeViewModel` / `SearchViewModel` / `LyricsViewModel` existent mais **ne déchargent pas** le monolithe.

**Risque :** regressions cross-feature, tests difficiles, conflits multi-agents (déjà visible : buffering vs audit).

### 2.3 StateFlow / UiState

- Nombreux `StateFlow` granulaires exposés → `AppNavigation` **collecte ~30 flows** et les passe en paramètres (prop drilling massif).
- Pas de `PlayerUiState` / `LibraryUiState` sealed unifiés.
- Cohérence **technique** (StateFlow) oui ; **ergonomie archi** non.

### 2.4 Duplication à factoriser

- Headers d’écoute (`ListeningSectionHeader`) partiels — roadmap mentionne `ListeningPageHeader`.
- Détails Album / Artiste / Playlist : structures quasi clones.
- Error UX : Toast depuis `LaunchedEffect(playbackError)` / metadata — pattern ad hoc, pas `SnackbarHost` / canal erreurs unifié.
- Queue list mutations : **à brancher** sur `PlaybackQueueOps` (ajouté 2026-08-21, pur, hors moteur).

### 2.5 Modularisation Gradle

`settings.gradle.kts` : **`:app` seul**.  
**Verdict : prématuré.** Coût AGP / Media3 / Compose partagés > gain. D’abord **packages + extraction VM**, modules `:core:player` / `:feature:library` seulement si équipe > 2 et CI stable.

### 2.6 Error handling

| Zone | Pattern | Critique |
|------|---------|----------|
| Playback | `PlayerGuards.userMessageForPlaybackError` + StateFlow + Toast | OK messages ; Toast = fragile |
| Room | Recovery classifier + migrations | Documenté audits DB |
| Backup | `backupMessage` | OK |
| Remote | `remoteHostError` | OK |
| UI stubs | silence | **Pire que crash** — fantômes |

### 2.7 vs Now in Android / Google Architecture 2026

| Pratique NiA / Guide 2026 | SoundGroove |
|---------------------------|-------------|
| UI layer + UiState par écran | Partiel (Home/Search) ; shell = prop drilling |
| Domain use cases | Absent |
| Data repositories | Présents |
| Hilt / DI | **Absent** (`AndroidViewModel` manuel) |
| Offline-first | Oui (local) |
| Modularization by feature | Non (OK taille actuelle) |
| Single source of truth | Room + MediaStore + prefs **dispersés** |

**Proposition :** Phase A — `LibraryRepository` / `PlayerSessionFacade` / `SettingsRepository` + `PlayerUiState`. Phase B — découper VM. Phase C — modules seulement si besoin.

---

## 3. Navigation UX

### 3.1 Arborescence (texte)

```
MainActivity
└─ AppNavigation (SharedTransitionLayout)
   ├─ NavHost
   │  ├─ HOME → LegacyMainHost → MainScreen
   │  │    ├─ Tab Accueil (HomeTab)
   │  │    ├─ Tab Bibliothèque (LibraryTab + sous-vues)
   │  │    ├─ Tab Recherche → navigate(SEARCH)   [pas un vrai tab]
   │  │    ├─ Tab Profil (ProfileTab)
   │  │    ├─ Overlay Settings
   │  │    └─ Overlay Récemment joué
   │  ├─ SEARCH
   │  ├─ PLAYER (+ overlays Queue / Lyrics / WebSearch / sheets)
   │  ├─ CAR_MODE
   │  ├─ playlist/{id}
   │  ├─ folder/{path}
   │  ├─ album/{name}
   │  └─ artist/{name}
   └─ MiniPlayer (overlay global)
```

### 3.2 Impasses & taps fréquents

| Tâche | Taps aujourd’hui | Cible |
|-------|------------------|-------|
| Play depuis Accueil | 1 | OK |
| Ajouter à playlist depuis Search | **∞ (fantôme)** | 2 (menu → picker) |
| Ouvrir EQ | Player → Options → EQ **ou** Settings | Unifier |
| Mode voiture | Settings **ou** Profil | 1 raccourci Accueil max |
| Paroles | Player → swipe/tap | OK |

### 3.3 Bottom bar

4 items glass pill : Accueil / Bibliothèque / Recherche / Profil.  
**Incohérence :** Recherche = route externe ; highlight tab ne reflète pas toujours la destination. Profil surcharge Settings.

### 3.4 Hiérarchie Accueil

Greeting + empty state + Continuer + Accès rapide + Mix + Récents + Favoris + Nouveaux… → **trop de jobs**.  
Hero budget (règles design) : Accueil ressemble à un **dashboard Spotify lite**, pas une composition unique. À réduire à : Continuer + 1 Pulse + 1 ligne récents.

### 3.5 Mini-player

Bien conçu (instance unique, masqué sur PLAYER, suppression Home overlay). Morph shared element documenté. **Point faible :** peu de feedback skip / changement de piste hors progress.

### 3.6 Écrans fusionnables

- ProfileTab ↔ SettingsScreen (identité + prefs).
- RecentlyPlayed overlay ↔ section Accueil « Voir tout ».
- Album/Artist detail NavHost ↔ Library drill-in (choisir **un** modèle).
- LyricsWebSearch comme sheet du LyricsScreen (pas overlay frère supplémentaire).

---

## 4. Motion

### 4.1 Transitions Navigation

`SgMotion` aligne M3 (Fast/Medium/Slow, emphasized ease).  
HOME→album/artiste : fade (sharedBounds) — **cohérent**.  
SEARCH : fade — OK.  
PLAYER : fade only + morph — **bonne intention**.  
Certaines routes secondaires : slide standard — OK.  
**Qui jure :** overlays locaux MainScreen (`slideUp`) vs NavHost fade ; Queue scale-down Player vs morph mini — deux langages de « réduction ».

### 4.2 Morph mini → player

Implémenté (`SharedTransitionLayout`, clés art / play). Qualité dépend shared scopes — documenté `FEATURES_C_SHARED_ELEMENT`. **Risque :** predictive back force morph manuel.

### 4.3 Micro-interactions

`sgPressScale`, pills nav, progress 500 ms, Lottie bars, Rive pulse (optionnel). Suffisant ; Rive = faible ROI.

### 4.4 M3 Expressive 2026 vs SgMotion

SgMotion = **M3 motion classique** (tokens durée/easing). Expressive 2026 (containment, spatial more playful) **non adopté** — prudent et cohérent avec Compose BOM projet. Ne pas « Expressive-washing » sans bump BOM.

### 4.5 Trous de continuité

- **Changement de morceau :** pochette / métadonnées peuvent **cutter** sans `AnimatedContent`.
- Skip depuis mini : pas de morph.
- Queue open : Player alpha→0 (discret) mais bandeau **nouvelle** UI — rupture narrative.
- Reduced motion : bien géré (snap) — à conserver.

---

## 5. Section PM (demandée)

### 5.1 Features — superflu / manques / innovant 2026

**Superflu :** vinyl, remote LAN en chemin principal, Rive non produit, Profil tab plein.

**Manques :** playlist picker universel ; filtres bibliothèque ; continuité skip ; mise en avant backup ; empty states « première piste Phonk » onboarding local (sans signup).

**1–2 innovantes 2026 (locales) :**
1. **Listening Stage** — player+lyrics+glass comme scène unique (déjà 70 % du code, manque curation UX).
2. **Pulse local** — mix / mood depuis scrobble + folders (zero cloud, zero login).

### 5.2 Archi — risques + proposition

| Risque | Impact | Proposition |
|--------|--------|-------------|
| God ViewModel | Régressions, conflits agents | Découper PlayerSession / Library / Prefs |
| Prop drilling AppNavigation | Fragilité UI | `PlayerUiState` + CompositionLocal limité |
| Fantômes callbacks | Confiance utilisateur | Lint / test : no empty prod stubs |
| Toast errors | Perte message | Snackbar canal unique |
| Modules Gradle trop tôt | Coût | Packages d’abord |

### 5.3 Interfaces & transitions — solutions concrètes

1. Brancher **un** `PlaylistPickerSheet` dans `AppNavigation` (état `songForPlaylistPicker`).
2. Player/Mini : `AnimatedContent(targetState = song.id)` fade 150–220 ms sur art + titres.
3. Accueil : supprimer Mix générique **ou** le remplacer par Pulse ; max 3 sections.
4. Bottom bar : 3 tabs (Accueil, Bibliothèque, Profil) + FAB/search icon header — **ou** garder Recherche mais sync `selectedTab` via route.
5. Harmoniser dismiss : Queue utilise **même** courbe que mini morph (scale vers bas) plutôt que scale-down fantôme.
6. Supprimer / enterrer vinyl + remote dans « Avancé ».

---

## 6. Tests ajoutés (cette livraison)

**Ne pas lancer `gradlew` ici** (contrainte autre agent). Stack = JUnit/Kotlin.

| Fichier | Rôle |
|---------|------|
| `util/PlaybackQueueOps.kt` | Logique pure file (hors ExoPlayer) |
| `test/.../SongDisplayTest.kt` | Durées / titres / artistes |
| `test/.../PlaybackQueueOpsTest.kt` | move / remove / insertNext |
| `test/.../FormatSleepTimerDisplayTest.kt` | Format timer |
| `test/.../PlayerGuardsLogicTest.kt` | Seuil previous (étendu) |
| `test/.../FavoritePlaylistDaoSqlTest.kt` | + duplicate PK playlist |
| `test/.../journey/SearchPlaylistPlayJourneyTest.kt` | search → playlist → play intent **mock** ; inscription **N/A** |
| `androidTest/.../SearchPlaylistPlaySmokeTest.kt` | Compose harness Search → play |
| `androidTest/.../SmokeNavGraph.kt` | Tags `smoke_add_playlist` / `smoke_play_intent` |

**Hors scope volontaire :** tests buffering / play button spinner / MediaController.

---

## 7. Inscription / parcours compte

**N/A.** SoundGroove n’a pas d’auth, signup, ni profil cloud. Le « profil » est un pseudo + avatar locaux. Les tests journey documentent explicitement cette absence et simulent search → playlist → play intent.

---

## 8. Chemins livrables

| Artefact | Chemin |
|----------|--------|
| **Ce rapport** | `docs/SOUNDGROOVE_FULL_PRODUCT_AUDIT_2026-08-21.md` |
| Contrat nav | `docs/NAVIGATION_CONTRACT.md` |
| Motion | `docs/MOTION.md` |
| Audits liés | `docs/ANDROID_QUALITY_PERF_AUDIT_2026-08-21.md`, `UI_UX_FRONTEND_*`, `MEDIA3_*` |

---

## 9. Statut implémentation (session 2026-08-21)

**Boussole Offline-first / competitive mix :** aucune feature cloud (auth, sync, reco serveur). UX inspirée Spotify/Apple (Continue + Pulse + récents, crossfade skip) et Musicolet/Poweramp (picker playlist partout) — **données 100 % locales** (MediaStore / Room / prefs / scrobble local).

| # | Item | Statut |
|---|------|--------|
| A1–A3 | PlaylistPicker (`songForPlaylistPicker`) + stubs Search/Album/Artist/Playlist/Folder + info dossier | **Fait** |
| B4 | `AnimatedContent` keyed by `song.id` Player + MiniPlayer (~180–200 ms, reduced motion) | **Fait** |
| C5 | Pulse local (scrobble + favoris + récents) remplace Mix du jour | **Fait** |
| C6 | Accueil allégé : Continuer + Pulse + Récents | **Fait** |
| D7 | Vinyl retiré Options Player → Paramètres **Avancé** | **Fait** |
| D8 | Remote LAN sous Paramètres **Avancé** | **Fait** |
| E9 | Sync `selectedTab` = 2 pour SEARCH (+ contenu tab 0/2) | **Fait** |
| E10 | SnackbarHost `playbackError` / `playlistMessage` (AppNavigation + LegacyMainHost) | **Fait** |
| E11 | Mutations queue VM → `PlaybackQueueOps.moveItems` / `removeItemAt` | **Fait** |
| F12 | `PlayerUiState` extract léger + usage MiniPlayer | **Fait** (sans rewrite NavHost) |
| F13 | Unifier SongItem / SongListItem | **Reporté** (APIs différentes menu vs list ; quick win non évident) |
| — | Fusion Profil tab / Hilt / multi-modules Gradle | **Reporté** (hors scope) |

---

*Audit critique volontaire — Lead PM / Archi / UX. Prochaine décision produit : tuer les fantômes playlist avant toute nouvelle gadget feature.*
