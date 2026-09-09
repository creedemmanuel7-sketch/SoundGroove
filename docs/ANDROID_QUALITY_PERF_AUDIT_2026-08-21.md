# Android Quality & Perf Audit — 2026-08-21

Scope : app Android uniquement (`app/`). Pas website / desktop. Croisement code SoundGroove + reco 2026.

## A. Recherche web (synthèse)

### A1. Media3 SimpleCache — pertinent pour SoundGroove ?

| Cas | Reco | SoundGroove |
|-----|------|-------------|
| Streaming HTTP (HLS/DASH/progressive URL) | `SimpleCache` + `LeastRecentlyUsedCacheEvictor` (baseline mobile **200–500 Mo**, tablette/OTT **1–2 Go**) ; singleton + `StandaloneDatabaseProvider` | **N/A** — catalogue = fichiers locaux / MediaStore / SAF |
| Offline downloads utilisateur | `DownloadManager` (distinct du cache on-the-fly) | **N/A** (pas de catalogue stream) |
| Lecture locale `file://` / `content://` | Buffer RAM `LoadControl` suffit ; cache disque Media3 **n’accélère pas** l’accès local déjà sur stockage appareil | **Déjà traité** : `targetBufferBytes` 24 Mo, first-play ~350 ms (voir `MEDIA3_PLAYER_AUDIT_2026-08-21.md`) |

**Verdict** : `SimpleCache` = **N/A** pour SoundGroove (app musique locale). L’introduire serait coût I/O + index SQLite sans gain VST local. Sources : [Media3 NetworkStacks sample](https://github.com/androidx/media), guides cache OTT (Webeyez / TO THE NEW) — tous orientés **upstream HTTP**.

### A2. Matrice versions stables (août 2026) vs projet

| Lib | Projet | Stable / reco 2026 | Action |
|-----|--------|--------------------|--------|
| Kotlin | **2.0.21** | 2.1.x / 2.2.x disponibles | **Conserver** (prudent ; KSP aligné) |
| AGP | **8.13.2** | 8.x OK Baseline Profiles | Conserver |
| Compose BOM | **2024.09.00** | **2026.06.00** (Compose ~1.11) | **Doc-only** — bump différé (risque UI/motion) |
| Media3 | **1.10.1** | 1.10.1 stable ; RC 1.11.0 | **Conserver** (contrainte) |
| Room | **2.8.4** | 2.8.4 (Room3 3.0.0 existe) | **Conserver** Room v2 / schéma v6 |
| Hilt | — | 1.4.0 | **N/A** — projet sans Hilt (`AndroidViewModel` manuel) |
| profileinstaller | — → **ajouté** | 1.4.1 | **Fait** |
| Navigation Compose | 2.8.3 | ≥2.8 pour predictive back | OK |
| Activity Compose | 1.13.0 | ≥1.6 OnBackPressed | OK |
| Firebase / Crashlytics | absent | — | **N/A documenté** (pas d’install opportuniste) |

Sources : [Jetpack stable channel](https://developer.android.com/jetpack/androidx/versions/stable-channel), [Media3 releases](https://developer.android.com/jetpack/androidx/releases/media3).

### A3. Android 15 / 16 — impact app musique

| Sujet | Impact SoundGroove |
|-------|-------------------|
| FGS `mediaPlayback` | Déjà déclaré ; timeout Media3 configuré |
| BOOT_COMPLETED → FGS media | Android 15+ bloqué — **OK** (pas de auto-start boot) |
| Doze / standby | FGS + notif ongoing pendant lecture ; pause longue ≠ survie garantie (OEM) |
| Permissions audio | `READ_MEDIA_AUDIO` (33+) / legacy storage — empty states renforcés |
| `POST_NOTIFICATIONS` | Demandée si smart notifs ; refus = silence, pas crash |
| Predictive back | `enableOnBackInvokedCallback=true` + `SgPredictiveBackHandler` / Navigation 2.8 — **OK** ; targetSdk 36 = animations système par défaut |
| Edge-to-edge | Déjà `enableEdgeToEdge` |

Sources : [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16), [Predictive back](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture).

### A4. Material 3 Expressive + contraste glass / blur

- M3 Expressive = direction design (motion, containment, touch targets ≥48 dp) ; APIs Expressives encore partiellement expérimentales — SoundGroove reste sur **M3 stable** + identité violet.
- Dynamic color wallpaper Material You : **opt-out volontaire** pour préserver la marque ; accents **pochette** = dynamic opt-in existant.
- Glass / blur dark violet : texte doit reposer sur **scrim opaque** (pas sur blur nu). Tokens `textPrimary` / `textSecondary` déjà calibrés AA sur fonds sombres ; glass surfaces renforcées (alpha ↑) pour lisibilité TalkBack + vision basse.
- WCAG AA texte normal ≈ **4.5:1** ; gros texte / UI ≥ **3:1**.

Sources : [M3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3), [Expressive research](https://design.google/library/expressive-material-design-google-research).

### A5. Baseline Profiles + Macrobenchmark — objectifs cold start

| Objectif | Réaliste musique locale | « Idéal » marketing |
|----------|-------------------------|---------------------|
| Cold start Time to Initial Display (TTID) | **800–1500 ms** mid-range | <500 ms (très agressif hors flagship + BP + ART) |
| Time to Full Display (TTFD) + bibliothèque | **1.5–3 s** (scan MediaStore) | <1 s rarement réaliste |
| Play latency tap → `isPlaying` | **<300 ms** local warm controller ; **0.5–2 s** cold MediaController | <100 ms rarement (disk + decode) |

**Baseline Profiles** : réduisent typiquement 20–40 % du code startup interprété → gain TTID mesurable, pas magique sous 500 ms.

Méthode mesure :
```text
adb shell am start-activity -W -n com.credo.soundgroove/.MainActivity
# ou Macrobenchmark StartupTimingMetric + CompilationMode.Partial(BaselineProfile)
logcat -s SG_AUDIO   # play latency timestamps
```

Sources : [Create Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile).

---

## B. Livrables implémentés / statut

Voir tableau final dans la réponse utilisateur. Détails techniques :

### StrictMode (debug)
`SoundGrooveApplication` : `detectDiskReads/Writes`, `detectNetwork`, `penaltyLog` — **jamais** `penaltyDeath` en release (classe no-op hors DEBUG).

### Baseline Profile
- `app/src/main/baseline-prof.txt` (règles startup / player / Room)
- Dépendance `androidx.profileinstaller:profileinstaller:1.4.1`
- Génération future : module Macrobenchmark + `:app:generateBaselineProfile` (doc ci-dessous)

### Macrobenchmark
Module `:benchmark` **non ajouté** (complexité AGP managed devices / CI sans device dédié). À la place :
- Script checklist + commandes adb (section C)
- Smoke Compose existants + Search smoke

### Play latency
`PlayLatencyTracker` + logs `SG_AUDIO` : `tap` → `isPlaying=true` (ms).

### WindowSizeClass
`Adaptive.kt` : helpers medium/expanded (list density, search columns, dual-pane hint).

### A11y / thème / permissions / rotation
Voir diff code + checklist manuelle.

### Firebase Crashlytics
**Absent** du projet → écart documenté, **pas** d’ajout Firebase dans ce lot.

---

## C. Checklist manuelle (device / Perfetto)

### Automatisé (CI / Gradle)
- [x] Unit tests JVM (`:app:testDebugUnitTest`)
- [x] Compose smoke harness (`HomePlayerLyricsSmokeTest`, `SearchSmokeTest`) — device/émulateur
- [x] StrictMode penaltyLog (debug builds)
- [x] ProfileInstaller + baseline-prof embarqué

### Manuel — perf
1. **Cold start** : forcer-stop → `am start -W` ×5 → médiane TTID
2. **Warm start** : Home → background → relance
3. **Play latency** : `adb logcat -s SG_AUDIO` ; taper une piste locale
4. **Jank** : Layout Inspector / GPU rendering bars pendant scroll Library
5. **Mémoire** : Profiler Memory pendant scan 5k pistes + lecture FLAC
6. **Battery Historian / Perfetto** : session 10 min lecture écran off ; vérifier wake locks Media3 uniquement
7. **Rotation / multi-fenêtre** : lecture en cours → rotate / split-screen → pas de reset piste
8. **Permissions refusées** : dénier stockage → empty state + CTA ; dénier notifs → pas de crash
9. **Predictive back** : gesture depuis Player / détails (Android 15+)
10. **TalkBack** : mini-player, play/pause, favori, options

### Génération Baseline Profile (quand device rooted ou API 33+)
```bash
# Après ajout futur du module :baselineprofile
./gradlew :app:generateReleaseBaselineProfile
# Copie typique : app/src/release/generated/baselineProfiles/
```

### Mesure cold start indicative
```bash
adb shell am force-stop com.credo.soundgroove
adb shell am start-activity -W -n com.credo.soundgroove/.MainActivity
```

---

## D. Chemins livrables

| Artefact | Chemin |
|----------|--------|
| Ce doc | `docs/ANDROID_QUALITY_PERF_AUDIT_2026-08-21.md` |
| Audit Media3 lié | `docs/MEDIA3_PLAYER_AUDIT_2026-08-21.md` |
| Baseline rules | `app/src/main/baseline-prof.txt` |
| Application StrictMode | `app/src/main/java/.../SoundGrooveApplication.kt` |
| APK debug | `app/build/outputs/apk/debug/app-debug.apk` |

## Build (2026-08-21)

```text
./gradlew :app:testDebugUnitTest :app:assembleDebug
# JAVA_HOME = Android Studio JBR
BUILD SUCCESSFUL — 38 unit tests OK
```

Mesures cold start / play latency device : **non exécutées** (pas d’émulateur connecté dans cette session). Méthode : section C + `adb logcat -s SG_AUDIO`.

## E. Risques / non-faits volontairement

- Pas de bump Compose BOM 2024→2026 dans ce lot (régression UI)
- Pas de module Macrobenchmark complet (doc + adb à la place)
- Pas Firebase Crashlytics
- Pas SimpleCache
- `<500 ms` cold start non promis
- Focus D-pad TV : partiel (cibles 48 dp existantes) — pas de navigation Leanback dédiée
