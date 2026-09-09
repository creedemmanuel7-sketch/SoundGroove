# UI / UX — Nouvel environnement d'écoute

Objectif : que SoundGroove se sente comme un **espace d'écoute** cohérent (pas une pile d'écrans utilitaires).

## Principes

1. **Une atmosphère** — noir + accent (violet ou pochette), motion SgMotion partout, peu de chrome inutile.

2. **Hiérarchie d'écoute** — Player / mini / Accueil « Continuer » = primary ; options / sheets = tertiary.

3. **Gestes d'abord** — boutons secondaires discrets (déjà amorcé S1–S3).

4. **Perf perçue** — listes `key`/`contentType`, Coil court, pas de jank pendant morph.

## Phase 1 — Coque d'écoute (terminée)

| Surface | Intention | Statut |
|---------|-----------|--------|
| Mini-player | Plus immersif (ambiance légère, barre plus nette) | Fait |
| Accueil | « Continuer l'écoute » comme porte d'entrée émotionnelle | Fait |
| Perf listes | `key` / `contentType` sur Home (+ Library) | Fait |
| Motion | Vérifier usage SgMotion (pas de durées ad hoc) | Fait |
| Player chrome | Toujours visible pendant la lecture (pas de masquage idle) | Fait |
| Menu ⋯ | Section Essentiels en premier (Paroles, Infos, EQ) | Fait |

## Phase 2 — Bibliothèque & recherche (terminée)

- Même langage typo / sections que Accueil (`labelMedium` + `TextTertiary`, sous-titre d'écoute)
- Bibliothèque : « Ma Musique » + « Ta collection d'écoute » ; compteurs en `labelMedium` tertiary
- Recherche : `contentType` sur les LazyColumn ; états vides plus « musicaux »
- Transitions liste → détail plus continues (shared déjà partiel)

## Phase 3 — Architecture UI (terminée)

- Sheets déjà centralisées (AppNavigation) — inchangées
- `ListeningSectionHeader` extrait (`ui/components/ListeningSectionHeader.kt`) : titre + sous-titre / action optionnels ; utilisé Accueil, Recherche, Bibliothèque (dossiers masqués), Profil
- États vides : réutilisation de `SgEmptyState` (pas de nouveau composable)
- Audit LazyColumn / LazyRow majeurs : `key` + `contentType` ajoutés ou complétés sur Accueil, File, playlists/album/artiste détail, Récents, Profil, Mode voiture, Paroles, AddSongs sheet ; Library/Search déjà couverts en Phase 1–2

## Phase 4 — Soft-crossfade & EQ per-track (terminée)

- `SgCoverImage` étendu aux listes / détails / sheets (Queue, Library, Home, Profile, Album/Playlist detail, Lyrics, BottomSheets, SongItem)
- EQ : toggle « Pour ce morceau » branché + persistence per-track sans écraser le preset global

## Hors scope immédiat / reporté

- Extraction plus large de rows d'entité (SearchEntityRow, rows détail album/artiste) — faible gain vs duplication locale
- Refonte des en-têtes de page (`ListeningPageHeader`) — Accueil / Bibliothèque / Profil restent spécifiques (risque refactor large)
- Site vitrine (`website/`)
- Asset Rive : `res/raw/sg_pulse.riv` minimal généré ; script `tools/generate_sg_pulse_riv.mjs` pour régénérer (voir `docs/MOTION.md`)

## Motion accents (Lottie / Rive)

- Base : Compose + `SgMotion` (inchangé)
- Accents : Lottie égaliseur (`NowPlayingBars` → `SgLottie` / `sg_eq_bars`) ; Rive optionnel via `SgRive` / `SgRiveAccent` (mini-player)
- Anti-sauts pochette : `SgCoverImage` (Crossfade URI + Coil) sur surfaces d’écoute majeures
