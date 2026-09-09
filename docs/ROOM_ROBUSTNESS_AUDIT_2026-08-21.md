# Audit robustesse Room — SoundGroove (2026-08-21)

> **Scope :** module Android `app/` uniquement. Complète `docs/DATABASE_ARCHITECTURE_AUDIT_2026-08-21.md` (v5, CASCADE, anti-N+1, WAL).

---

## Phase 1 — Recherche web Room 2026

### Versions stables

| Artefact | Version | Date / source |
|---|---|---|
| `androidx.room:room-*` (ligne 2.x) | **2.8.4** | 19 nov. 2025 — [Room release notes](https://developer.android.com/jetpack/androidx/releases/room) |
| `androidx.room3:room3-*` (ligne 3.0) | **3.0.0** | 1 juil. 2026 — [Room 3.0](https://developer.android.com/jetpack/androidx/releases/room3) |

**Choix SoundGroove :** rester sur **Room 2.8.4** (déjà dans `gradle/libs.versions.toml`). Room 3.0 est un changement de package (`androidx.room3`), KSP-only, migrations `suspend` + `SQLiteConnection`, InvalidationTracker Flow-only. Migration 3.0 = chantier séparé ; moderniser d’abord DAOs `suspend`/`Flow` (déjà le cas).

Guide migration 2→3 : https://developer.android.com/training/data-storage/room/migration-2-to-3

### Flow / suspend — pas de blocking sur Main

| Source | Points clés |
|---|---|
| [Async queries](https://developer.android.com/training/data-storage/room/async-queries) | `Flow` / `LiveData` / `suspend` : Room exécute hors Main ; ne pas utiliser `allowMainThreadQueries` en prod |
| Room 3.0 notes | DAO non-réactifs **doivent** être `suspend` ; APIs `useReaderConnection` / `useWriterConnection` suspend |

**Reco 2026 :**
- Lectures observées → `Flow` (InvalidationTracker)
- Écritures / one-shot → `suspend`
- Interdit : `runBlocking` autour des DAO depuis Compose / Main
- `viewModelScope.launch` + Room suspend = OK (Room bascule sur son executor) ; préférer `Dispatchers.IO` pour I/O fichier + multi-DAO applicatif

### Migrations explicites vs destructive

| Source | Points clés |
|---|---|
| [Migrate Room DB](https://developer.android.com/training/data-storage/room/migrating-db-versions) | Chemins `Migration` explicites ; `fallbackToDestructiveMigration()` = **perte de données** |
| Alternatives | `fallbackToDestructiveMigrationFrom(...)`, `fallbackToDestructiveMigrationOnDowngrade()` |
| Tests | `MigrationTestHelper` + `exportSchema = true` ([ref](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper)) |

**Reco :** jamais de destructive en **release**. Debug-only gated OK pour chemins manquants pendant le développement.

### `@Transaction`, indexes, Auto Backup

| Sujet | Source / reco |
|---|---|
| `@Transaction` | Opérations multi-requêtes / multi-tables atomiques ; ou `RoomDatabase.withTransaction { }` |
| Indexes | `@Index` sur colonnes de filtre / tri / JOIN (titre, artiste, uri, playedAt…) |
| WAL | `JournalMode.WRITE_AHEAD_LOGGING` — meilleures lectures concurrentes |
| Auto Backup | `allowBackup` + `fullBackupContent` / `dataExtractionRules` ; exclure secrets Keystore — [Auto Backup](https://developer.android.com/guide/topics/data/autobackup) |

---

## Phase 2 — Checklist (état après correctifs)

### 1. Migrations

| Item | Avant | Après |
|---|---|---|
| Chemins explicites | 1→2…4→5 | + **5→6** (`SoundGrooveMigrations`) |
| `fallbackToDestructiveMigration` | Absent | **Debug-only** (`BuildConfig.DEBUG`) |
| `exportSchema` | `false` | Tenté `true` + `room.schemaLocation` — **bloqué** par `AbstractMethodError` KSP/kotlinx.serialization (`FieldBundle$$serializer`) sous Room 2.8.4. Remis `false` ; validation migrations via tests SQL JVM. Réessayer avec plugin Room Gradle / Room 3.0. |
| Tests | Aucun | `RoomMigrationAndVolumeTest` (4→5, 5→6 + bench 5k) via **sqlite-jdbc** JVM (évite hang Robolectric/Rive) |

### 2. Flow / suspend / Main

| Check | Résultat |
|---|---|
| `allowMainThreadQueries` | Absent (prod) ; autorisé **uniquement** dans tests in-memory |
| `runBlocking` sur DAO app | Absent |
| DAOs | `Flow` + `suspend` uniquement |
| Backup import/export VM | `viewModelScope.launch(Dispatchers.IO)` |
| Catalogue MediaStore | Déjà `withContext(Dispatchers.IO)` |

### 3. Indexes (v6)

| Table | Nouveaux indexes |
|---|---|
| `favorites` | `title`, `uri` (+ `artist` v5) |
| `playlist_songs` | `title`, `uri` (+ playlistId/songId/position/artist v5) |
| `recently_played` | `title`, `artist`, `uri` (+ `playedAt` / `playCount` v5) |
| `metadata_overrides` | `updatedAt` |

**Note :** le catalogue 5000+ morceaux vit dans **MediaStore** (`dateAdded`, path fichier). Room ne stocke que favoris / playlists / historique / overrides — indexes Room ciblent ces tables.

### 4. Volume 5000+

Test JVM `RoomMigrationAndVolumeTest` (sqlite-jdbc, même SQL que Room) :
- Insert 5000 favoris + load / search / count
- Insert 5000 `playlist_songs` + load / search / count  
Timings mesurés (JVM sqlite-jdbc, 2026-08-21) :

| Scénario | insert | load | search | count |
|---|---|---|---|---|
| 5000 favorites | ~157 ms | ~12 ms | ~9 ms | ~2 ms |
| 5000 playlist_songs | ~195 ms | ~9 ms | ~8 ms | ~7 ms |

Pas de scandale perf ; indexes titre/uri/artiste en place.

### 5. `@Transaction`

| Opération | Mécanisme |
|---|---|
| Delete playlist | `@Transaction deletePlaylistWithSongs` (+ CASCADE) |
| Recently played | `@Transaction upsertAndTrim` |
| Toggle favori | `db.withTransaction` |
| Add songs playlist | `db.withTransaction` |
| Replace library (import backup) | `db.withTransaction` dans repository |
| Purge orphelines multi-tables | `db.withTransaction` |
| Metadata upsert | `db.withTransaction` |

### 6. Cas limites

| Cas | Handler |
|---|---|
| DB corrompue / cant open / disk IO / full | `openWithRecovery` : log, `deleteDatabase`, recreate, message UI via `consumeOpenWarning()` |
| Process kill mid-write | WAL + transactions → cohérence SQLite |
| Stockage plein à l’open | Message dédié + reset DB |
| Échec migration release | Exception Room (pas de destructive) — forcer migration testée |

Classifier testé : `DatabaseRecoveryClassifierTest`.

### 7. Backup / export

| Canal | Stratégie |
|---|---|
| Auto Backup / device transfer | `allowBackup=true` ; Room DB **incluse** ; exclure `soundgroove_secure_prefs.xml` |
| Export manuel | `BackupManager` JSON (favoris, playlists, thème, accent, settings) — **recommandé** pour changement de téléphone (URIs MediaStore peuvent différer) |
| Import | Transaction Room `replaceLibraryData` |

---

## Fichiers touchés

- `app/.../Database.kt` — schéma v6, indexes, open recovery, DAO search/count/transaction
- `app/.../data/db/SoundGrooveMigrations.kt` — migrations 1→6 exposées
- `app/.../data/repository/DatabaseRepository.kt` — `withTransaction` multi-table
- `app/.../viewmodel/SoundGrooveViewModel.kt` — warning open + IO backup
- `app/.../auto/AutoLibraryCatalog.kt` — ctor repository
- `app/build.gradle.kts` — schema KSP, sqlite-jdbc, room-testing
- `app/src/test/.../RoomMigrationAndVolumeTest.kt`
- `app/src/test/.../DatabaseRecoveryClassifierTest.kt`
- `res/xml/backup_rules.xml`, `data_extraction_rules.xml`

## Build

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.credo.soundgroove.data.db.*" :app:assembleDebug
```

APK attendu : `app/build/outputs/apk/debug/app-debug.apk`