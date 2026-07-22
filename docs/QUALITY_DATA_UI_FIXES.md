# Qualité data / UI — correctifs (lot stats / métadonnées / palette / doublons)

Document de suivi pour le lot qualité produit SoundGroove (juillet 2026). Pas de commit dans ce lot.

## 1. Stats d’écoute — semaine / mois / total distincts

### Cause
`ListeningStatsRepository.computeStats` forçait `mois ≥ semaine` via `coerceAtLeast(weekSeconds)`.
Or une **semaine calendaire** n’est pas un sous-ensemble du **mois calendaire** (ex. début juillet qui inclut fin juin). Le mois était alors gonflé à la valeur de la semaine ; avec un total lifetime encore proche de cette fenêtre récente, **les trois pastilles affichaient la même valeur**.

### Correctif
- Semaine = du premier jour de semaine locale → aujourd’hui.
- Mois = du 1er du mois → aujourd’hui.
- **Total (« Depuis le début »)** :
  - si l’historique `daily_listening_json` est complet (&lt; 90 j, rien de pruné) → somme journalière = vérité (corrige un prefs lifetime gonflé, ex. total 24h vs mois 4h) ;
  - sinon prefs `total_listening_seconds`, floor sur la somme journalière retenue.
- ViewModel resynchronise `total_listening_seconds` sur le total résolu.
- **Plus de coerce** qui aligne mois sur semaine.
- UI : Profil + Paramètres — labels « Ce mois » / « Depuis le début », format compact `4h54` ; source unique `listeningStats`.

### Note produit
Si l’utilisateur n’a écouté que depuis peu (&lt; une semaine calendaire), semaine ≈ mois ≈ total reste **attendu**. Les pastilles divergent dès qu’il y a de l’historique hors de la fenêtre courante.

## 2. Métadonnées d’affichage (`SongDisplay`)

Helper déjà centralisé : `<unknown>` → « Artiste inconnu », filenames → titre lisible, `0:00` / durée &lt; 1 s masqués (`formatDurationOrNull` → `—` ou omission).

### Trous complétés
Branchement `displayTitle` / `displayArtist` / `displayAlbum` (ou `SongDisplay.*`) sur :
- Bibliothèque (favoris custom row)
- Album / Artiste / Playlist détail
- File d’attente
- Paroles (header + mini métas)
- Fiche info MainScreen
- Bottom sheets (ajout playlist / menu contexte)

Édition métadonnées : conserve les valeurs brutes (volontaire).

## 3. Palette pochette — contraste secondary / onSurface

### Correctif
- Nouveau rôle `AlbumArtRolePalette.onSurface` (texte secondaire lisible sur le fond thème).
- `ensureReadableRole` : push vers blanc/noir si secondary trop boueux sur fond sombre.
- Chrome Paroles : secondary/tertiary un cran plus clairs ; inactiveText contraste relevé.
- Lecteur : bouton **File** → `onSurface` (si accent pochette) ; **Paroles** → accent `ensureContrast` sur le fond.
- `DualCtaBar` (Aléatoire) : paramètre optionnel `onSurfaceColor` (défaut `TextPrimary`).

Pas de redesign global ThemePicker / chips / seek / Search.

## 4. Doublons bibliothèque (ex. « 0cpdcl »)

### Cause probable
MediaStore peut exposer **deux `_ID` pour le même fichier** (réindexation, `DATA` vs `RELATIVE_PATH`+`DISPLAY_NAME` sur Android 10+). L’ancien scan ajoutait chaque ligne sans dédup chemin.

### Correctif (sûr)
Dans `MusicRepository.getSongs()` :
- dédup par `_ID` ;
- dédup par chemin absolu normalisé (`DATA`) ;
- sinon clé `RELATIVE_PATH|DISPLAY_NAME|SIZE` ;
- en conflit : garder l’entrée aux métadonnées les plus riches / plus récente.

### Non fusionné (volontaire)
Deux fichiers distincts avec le même titre/artiste (covers, remixes, chemins différents) restent deux entrées — éviter les faux positifs.

### Mitigation affichage
Les compteurs Accueil / Profil / Paramètres utilisent `songs.size` après scan dédupliqué ; rescan Bibliotheque pour appliquer.

---

Fichiers clés : `ListeningStatsRepository.kt`, `MusicRepository.kt`, `SongDisplay.kt`, `AlbumArtPalette.kt`, `EffectiveAccent.kt`, écrans Profil / Settings / Library / Player / Lyrics / listes.
