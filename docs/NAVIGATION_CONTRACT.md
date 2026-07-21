# Contrat de navigation — SoundGroove (Android)

Document unique décrivant le graphe NavHost, les overlays Player et la visibilité du mini-player.  
Implémentation : `ui/navigation/AppNavigation.kt`, `MiniPlayerVisibility.kt`, `Routes`.

---

## 1. Routes NavHost (primaires)

| Route | Constante | Rôle |
|-------|-----------|------|
| Accueil + shell onglets | `Routes.HOME` | `LegacyMainHost` → `MainScreen` (Accueil / Bibliothèque / Profil + bottom nav) |
| Recherche | `Routes.SEARCH` | `SearchScreen` (push depuis bottom nav) |
| Lecteur plein écran | `Routes.PLAYER` | `PlayerScreen` — **aucun mini-player** |
| Détail playlist | `Routes.PLAYLIST_DETAIL` | `PlaylistDetailScreen` |
| Détail dossier | `Routes.FOLDER_DETAIL` | `FolderDetailContent` |
| Détail album | `Routes.ALBUM_DETAIL` | `AlbumDetailScreen` (+ shared element grille → hero) |
| Détail artiste | `Routes.ARTIST_DETAIL` | `ArtistDetailScreen` (+ shared element) |

**Primaires produit** : Home/Library vivent sous `HOME` (tabs internes). Recherche est une route séparée. Player est une destination plein écran push/pop.

`startDestination` = `HOME`.

---

## 2. Overlays (hors NavHost)

Gérés dans `AppNavigationContent`, **au-dessus** du `NavHost`, dans le même `SharedTransitionLayout`.

### 2.1 Mini-player (global)

- **Une seule instance** : overlay bas dans `AppNavigation` (plus d’instance dans `MainScreen`).
- Visibilité : `MiniPlayerVisibility.shouldShow(...)`.
- Jamais visible sur `Routes.PLAYER` (Jakob : un seul lecteur à la fois).
- Réglage Settings « Mini-player persistant » : `persistentMiniPlayerEnabled` (ViewModel).
- Sur `HOME` : masqué si overlay « Récemment joué » ouvert (`homeMiniPlayerSuppressed`).
- Position : au-dessus du bottom nav sur `HOME` (`MiniPlayerVisibility.bottomPadding`), inset standard ailleurs.
- Shared element pochette ↔ Player : `LocalSharedTransitionScope` + `AnimatedVisibility` sans fade conteneur.

### 2.2 Paroles (peek Player ↔ Lyrics)

- **Propriétaire** : `AppNavigation` (`lyricsPeekProgress`, `lyricsMounted`).
- Pas une route NavHost : overlay plein écran pair du Player.
- `0` = Player visible ; `1` = Paroles plein écran ; drag intermédiaire = peek annulable.
- Montage paresseux au premier tap « Paroles » ou premier drag.
- Reset automatique quand `currentRoute != PLAYER`.
- **S1 (paroles)** : contrôles limités à **play/pause** sur `LyricsScreen` et `LyricsWebSearchScreen` — pas de prev/next (réservés Player + mini-player).

### 2.3 File d’attente (Queue)

- **Propriétaire** : `AppNavigation` (`showQueue`, `queueBannerProgress`).
- Pas un bottom sheet qui cache le Player : split **bandeau Player ~25 %** + **Queue ~75 %**.
- Ouverture : swipe up / bouton File sur `PlayerScreen`.
- Fermeture : `closeQueue()` — animation légère puis démontage.
- Reset quand `currentRoute != PLAYER`.

### 2.4 Sheets Player (options, EQ, vitesse, etc.)

- Visibles uniquement si `currentRoute == Routes.PLAYER`.
- État local booléen dans `AppNavigationContent`.

---

## 3. Gestes

| Geste | Écran | Effet |
|-------|-------|-------|
| Swipe down | Player | `popBackStack()` → mini-player |
| Swipe up | Player | Ouvre Queue (split bandeau) |
| Drag horizontal | Player ↔ Paroles | Peek / commit paroles (`lyricsPeekCommitFraction` ≈ 0,38) |
| Swipe down / droite | Paroles (peek > 0) | Ferme paroles → Player |
| Tap pochette / barre | Mini-player | `navigate(PLAYER)` |
| Tap bottom nav Recherche | MainScreen | `navigate(SEARCH)` |

Peek paroles : `onLyricsPeekDragStart` / `onLyricsPeekDrag` / `onLyricsPeekDragEnd` remontés depuis `PlayerScreen` et `LyricsScreen` vers `AppNavigation`.

---

## 4. BackHandler et predictive back (priorité composant → système)

Ordre typique (du plus spécifique au plus général) :

| Contexte | `enabled` | Action |
|----------|-----------|--------|
| Paroles peek actif | `peekProgress > 0` | Ferme paroles (`onClose`) |
| Player plein écran | toujours | `popBackStack()` / dismiss (swipe bas, bouton, back système) |
| Web search paroles | ouvert | Ferme web search |
| MainScreen : Paramètres | `showSettings` | Ferme settings |
| MainScreen : Récemment joué | `showRecentlyPlayed` | Ferme overlay |
| MainScreen : onglet ≠ Accueil | `selectedTab != 0` | Retour onglet Accueil |
| LibraryTab : sous-vues | album/artiste/dossier sélectionné | Ferme sous-vue |
| Écrans détail NavHost | — | `popBackStack()` via toolbar |

Sur Player : si paroles en peek, le `BackHandler` Paroles intercepte avant celui du Player.

### 4.1 Predictive back (POC S3 — route `PLAYER`)

- **Manifest** : `android:enableOnBackInvokedCallback="true"` sur `MainActivity`.
- **Compose** : `PredictiveBackHandler` dans `PlayerScreen` — branche le geste back système (Android 13+, bord gauche) sur le **même morph interactif** que le swipe bas (`applyDismissDrag` / `cancelDismissDrag`).
- **Commit** : à la fin du geste, `BackHandler` appelle `dismissPlayer(fromInteractiveDrag = …)` puis `popBackStack()` via `onClose`.
- **Annulation** : `CancellationException` → `cancelDismissDrag()` (spring-back identique au swipe bas).
- **Désactivé si** :
  - `rememberSgReducedMotion()` → back instantané (pas de suivi progressif) ;
  - peek paroles actif (`lyricsPeekProgress > 0`) — le back reste géré par `LyricsScreen` ;
  - file d'attente ouverte (`queueOpen`) — pas de conflit avec le split bandeau/Queue ;
  - Player en cours de sortie (`isExiting`).
- **Limites** :
  - API 33+ (OnBackInvokedCallback) pour le back prédictif natif ; API 24–32 : `BackHandler` classique sans animation système ;
  - le morph pendant le back prédictif repasse en mode manuel (`dismissMorphProgress > 0`) même avec shared element — même stratégie que le swipe bas interactif ;
  - pas de predictive back sur Paroles/Queue (hors périmètre POC).

---

## 5. Accessibilité gestes (TalkBack — S3)

Labels **sémantiques uniquement** (aucun texte visuel ajouté) :

| Zone | Fichier | Sémantique |
|------|---------|------------|
| Player plein écran | `PlayerScreen` | `contentDescription` : « Glisser vers le bas pour réduire… » + actions custom : Réduire, File, Paroles |
| Bouton réduire (header) | `PlayerScreen` | « Réduire au mini-player » |
| Paroles plein / peek | `LyricsScreen` | « Glisser vers le bas ou la droite… » + action « Revenir au lecteur » |
| Bouton retour paroles | `LyricsScreen` | « Revenir au lecteur » (déjà présent S1) |

TalkBack expose les **actions personnalisées** dans le menu contextuel ; les gestes swipe restent utilisables en parallèle.

---

## 6. Transitions motion

- Nav forward/back : `SgMotion.navForward*` / `navPop*` (ou snap si mode perf / reduced motion).
- Player enter/exit : `SgMotion.playerEnter()` etc. — fade only, morph pochette porte le mouvement.
- Album/Artiste : fade only depuis HOME/SEARCH (évite double slide + sharedBounds).
- SharedTransitionLayout enveloppe NavHost + mini-player + overlays Player.

Références : `docs/UX_MOTION_GUIDELINES.md`, `docs/FEATURES_C_SHARED_ELEMENT.md`.

---

## 7. Fichiers clés

| Fichier | Responsabilité |
|---------|----------------|
| `AppNavigation.kt` | NavHost, overlays, état peek paroles / queue, mini-player global |
| `MiniPlayerVisibility.kt` | Règle unique visibilité + padding bas HOME |
| `LegacyMainHost.kt` | Pont ViewModel → `MainScreen` |
| `MainScreen.kt` | Tabs, bottom nav, overlays locaux (settings, récemment joué) |
| `PlayerScreen.kt` | Player UI, gestes dismiss/queue/peek paroles, predictive back POC |
| `LyricsScreen.kt` | Paroles + peek + BackHandler S1 + sémantique gestes |
| `Motion.kt` | Durées, shared element, géométrie mini-player |

---

## 8. Non couvert (post-S3)

- Predictive back sur Paroles, Queue, routes NavHost secondaires.
- Player interne legacy dans d’autres écrans : voir audit composants pour routes secondaires.
