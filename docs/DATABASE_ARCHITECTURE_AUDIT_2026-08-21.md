# Audit architecture données — SoundGroove (2026-08-21)

> **Scope :** `app/` Android Room/SQLite uniquement. `website/` et `desktop-web/` hors périmètre (aucune modification).

## Surfaces inventoriées

| Surface | Techno | Rôle | Utilisateurs cloud |
|---|---|---|---|
| `SoundGrooveDatabase` (`soundgroove.db`) | Room / SQLite | Favoris, playlists, historique d'écoute, overrides métadonnées | **Non** — app locale |
| Catalogue morceaux | MediaStore (pas Room) | Songs / albums / artists / folders via `MusicRepository` + `FolderLibraryRepository` | N/A |
| Stats écoute / streak | SharedPreferences JSON (`daily_listening_json`) | Semaine / mois / streak / total | N/A |
| Scrobble local | SharedPreferences JSON | Stats scrobble 100 % local | N/A |
| EQ global + per-track | SharedPreferences (`track_eq_presets_json`) | Presets égaliseur | N/A |
| Pochettes custom | Fichiers `filesDir/cover_overrides/<songId>.jpg` + URI dans Room `metadata_overrides` | Cover overrides | N/A |
| Session / playback prefs | SharedPreferences + DataStore (gesture hints) | Réglages UI / reprise | N/A |
| Hors scope (`website/`, `desktop-web/`) | — | Non audité / non modifié | — |

**Conclusion (Android) :** une seule BDD relationnelle = Room SQLite locale. Pas d’utilisateurs cloud, pas de Postgres.

Voir aussi : `docs/ROOM_ROBUSTNESS_AUDIT_2026-08-21.md` (v6 indexes, recovery, bench).

## Schéma Room (v5)

### Entités
- `favorites` (PK `songId`) — snapshot dénormalisé
- `recently_played` (PK `songId`, `playedAt`, `playCount`)
- `playlists` (PK `id`, `name`)
- `playlist_songs` (PK `(playlistId, songId)`, FK → `playlists.id` **ON DELETE CASCADE**)
- `metadata_overrides` (PK `songId`)

Les IDs `songId` pointent vers MediaStore, **pas** vers une table Room `songs`. Donc pas de FK SQL song→favoris : purge applicative après scan.

### Indexes ajoutés (v5 / `@Index`)
| Table | Colonnes | Motif |
|---|---|---|
| `playlist_songs` | `playlistId` | Jointures / filtre playlist |
| `playlist_songs` | `songId` | Purge orphelines / retrait piste |
| `playlist_songs` | `(playlistId, position)` | Ordre des pistes |
| `playlist_songs` | `artist` | Filtre / tri |
| `recently_played` | `playedAt` | Tri « récemment » |
| `recently_played` | `playCount` | Tri « souvent écouté » |
| `favorites` | `artist` | Filtre / tri |

### Cascades / intégrité
| Action | Comportement |
|---|---|
| DELETE playlist | **CASCADE** → `playlist_songs` (FK Room + migration 4→5) |
| DELETE song (MediaStore) | Purge app : favoris, recently_played, playlist_songs, metadata_overrides, EQ per-track prefs, fichiers cover |
| Suppression utilisateur cloud | **N/A** — pas de compte |
| EQ per-track / covers | Pas en SQL ; prune via prefs + filesystem au reload bibliothèque |

Migration `MIGRATION_4_5` : recrée `playlist_songs` avec FK, copie uniquement les lignes dont la playlist parente existe (nettoie orphelines), crée les indexes.

## N+1 corrigés

### Avant
`DatabaseRepository.getAllPlaylists()` faisait `flatMapLatest` + `combine(entities.map { getSongsForPlaylist(id) })` → **1 Flow SQL par playlist** (N+1 d'observateurs).

`addSongsToPlaylist` chargeait **toutes** les `playlist_songs` puis filtrait.

`replaceLibraryData` insertait favoris / pistes **un par un**.

### Après
- `getAllPlaylists()` : `combine(getAllPlaylists, getAllPlaylistSongs)` — **2 requêtes**, groupBy en mémoire.
- `addSongsToPlaylist` : `getSongIdsForPlaylist(playlistId)` + `insertSongs` batch.
- `replaceLibraryData` : `insertAll` / `insertPlaylists` / `insertSongs` batch ; clear playlists s'appuie sur CASCADE.
- `getPlaylistsSnapshot` : groupBy au lieu de `filter` répété (O(n) vs O(n×m)).
- `loadMusic()` : purge orphelines en `Dispatchers.IO` après scan non vide.

### Catalogue / profil
- Catalogue : MediaStore single query (`MusicRepository.getSongs`) — pas de N+1 Room.
- Stats profil : SharedPreferences map journalière — pas de N+1 SQL.

## Pooling / reconnexion

### Room / SQLite
- **Pas de pool réseau** (fichier local). Documenté ici.
- **Singleton** `SoundGrooveDatabase.getInstance()` (`@Volatile` + `synchronized`) — une instance app.
- **WAL** activé : `.setJournalMode(WRITE_AHEAD_LOGGING)`.
- Flows Room = InvalidationTracker (pas de fuite de connexions manuelles).
- Accès DAO via repository / ViewModel coroutines (`Dispatchers.IO` pour purge).

### Postgres / serveur
- **N/A** — aucune BDD serveur dans ce repo pour l'app.

## Fichiers touchés
- `app/.../Database.kt` — schéma v5, FK, indexes, WAL, DAOs batch / purge
- `app/.../data/repository/DatabaseRepository.kt` — anti-N+1, batch, purge
- `app/.../viewmodel/SoundGrooveViewModel.kt` — purge au reload
- `app/.../util/PlaybackPreferences.kt` — prune EQ orphelins
- `app/.../util/CoverArtStorage.kt` — deleteOrphans fichiers

## Risques / garde-fous
- Purge **désactivée** si le scan renvoie 0 morceaux (permissions / échec) pour ne pas vider la bibliothèque utilisateur.
- Deletes orphelines chunkées (400) pour rester sous la limite de variables SQLite.

## Build
Voir statut compile dans le message de livraison (gradle `:app:compileDebugKotlin` / `assembleDebug`).
