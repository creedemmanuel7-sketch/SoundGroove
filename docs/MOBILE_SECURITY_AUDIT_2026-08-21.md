# Audit sécurité mobile Android — SoundGroove

**Date :** 2026-08-21  
**Périmètre :** module `app/` + Gradle root uniquement (`website/`, `desktop-web/` exclus).  
**Méthode :** recherche web benchmarks 2025–2026 → écarts code → correctifs défensifs (pas d’exploits).

---

## 1. Synthèse recherche web (Phase 1)

### 1.1 Stockage local — Security Crypto / EncryptedSharedPreferences

| Source | URL | Points clés |
|--------|-----|-------------|
| API EncryptedSharedPreferences | https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences | **Deprecated** (1.1.0) — message API : « Use SharedPreferences instead » (peu utile) |
| Jetpack Security release notes | https://developer.android.com/jetpack/androidx/releases/security | 1.1.0 : dépréciation de toutes les APIs au profit des APIs plateforme + Android Keystore |
| DataStore + Tink | https://developer.android.com/jetpack/androidx/releases/datastore | Artifact officiel **`androidx.datastore:datastore-tink`** + `AeadSerializer` (chiffrement AEAD Tink, MasterKey via Keystore) — voie recommandée 2025–2026 pour le stockage chiffré moderne |
| AeadSerializer | https://developer.android.com/reference/androidx/datastore/tink/AeadSerializer | Wrapper Serializer + AEAD ; `associatedData` unique (ex. nom de fichier) |

**Verdict 2026 :** ne pas démarrer de *nouveau* code sur EncryptedSharedPreferences. Préférer DataStore (+ `datastore-tink` quand stable en prod) ou Keystore AES-GCM maison pour un fichier prefs dédié. ESP reste « encore OK » en legacy mais déprécié.

### 1.2 Network Security Config / cleartext / pinning

| Source | URL | Points clés |
|--------|-----|-------------|
| Network security configuration | https://developer.android.com/privacy-and-security/security-config | Cleartext opt-out, trust anchors, **certificate pinning** via `<pin-set>`, debug-overrides |
| Cleartext communications | https://developer.android.com/privacy-and-security/risks/cleartext-communications | Depuis API 28, cleartext off par défaut ; NSC pour verrouiller / exceptions ciblées |
| OWASP MASTG pinning | https://mas.owasp.org/MASTG/knowledge/android/MASVS-NETWORK/MASTG-KNOW-0015/ | NSC préféré (déclaratif) ; OkHttp `CertificatePinner` si stack OkHttp ; backup pin + expiration |
| Ostorlab SSL pinning 2026 | https://blog.ostorlab.co/android-ssl-pinning.html | Même ligne : NSC ou CertificatePinner ; backup + expiration |

### 1.3 Media3 / ExoPlayer

| Source | URL | Points clés |
|--------|-----|-------------|
| Media3 troubleshooting cleartext | https://developer.android.com/media/media3/exoplayer/troubleshooting | `CleartextNotPermittedException` si HTTP sans NSC ; demo Media3 reste HTTPS-only (bon modèle) |
| HttpDataSource.CleartextNotPermittedException | https://developer.android.com/reference/androidx/media3/datasource/HttpDataSource.CleartextNotPermittedException | Exception documentée côté datasource |
| Library R8 / consumer rules | https://developer.android.com/topic/performance/app-optimization/library-optimization | Libs doivent shipper consumer ProGuard ; R8 **full mode** défaut depuis AGP 8.0 |

### 1.4 Room / SQL injection

| Source | URL | Points clés |
|--------|-----|-------------|
| Android SQL injection | https://developer.android.com/privacy-and-security/risks/sql-injection | Préférer Room ; requêtes paramétrées ; éviter concat user input |
| `@RawQuery` | https://developer.android.com/reference/kotlin/androidx/room/RawQuery | Escape hatch ; préférer `@Query` ; bind paramètres (SimpleSQLiteQuery / RoomRawQuery) |

### 1.5 OWASP Mobile Top 10

| Source | URL | Version |
|--------|-----|---------|
| OWASP Mobile Top 10 | https://owasp.org/www-project-mobile-top-10/ | **2024 Final** (pas de liste 2025/2026 distincte au 2026-08-21) |
| Risques 2024 | https://owasp.org/www-project-mobile-top-10/2023-risks/ | M1–M10 listés ci-dessous |

**OWASP Mobile Top 10 — 2024 (version trouvée) :**

1. **M1** Improper Credential Usage  
2. **M2** Inadequate Supply Chain Security  
3. **M3** Insecure Authentication / Authorization  
4. **M4** Insufficient Input / Output Validation  
5. **M5** Insecure Communication  
6. **M6** Inadequate Privacy Controls  
7. **M7** Insufficient Binary Protections  
8. **M8** Security Misconfiguration  
9. **M9** Insecure Data Storage  
10. **M10** Insufficient Cryptography  

Compléter avec MASVS / MASTG : https://mas.owasp.org/

### 1.6 R8 full mode / minify

| Source | URL | Points clés |
|--------|-----|-------------|
| Enable app optimization | https://developer.android.com/topic/performance/app-optimization/enable-app-optimization | `isMinifyEnabled` + `isShrinkResources` ; ne pas désactiver `android.enableR8.fullMode` (défaut AGP 8+) |
| AGP 8.13.x (projet) | `gradle/libs.versions.toml` → `agp = 8.13.2` | Full mode déjà actif sauf `=false` dans gradle.properties |

### 1.7 Composants exportés / FGS

| Source | URL | Points clés |
|--------|-----|-------------|
| Access control exported components | https://developer.android.com/privacy-and-security/risks/access-control-to-exported-components | `exported=false` sauf besoin ; permissions signature si exporté sensible |
| Android 14 behavior | https://developer.android.com/about/versions/14/behavior-changes-14 | **foregroundServiceType** obligatoire ; intents implicites restreints |
| Launch FGS | https://developer.android.com/develop/background-work/services/fgs/launch | Type + permission `FOREGROUND_SERVICE_*` |

### 1.8 SAST — Lint / MobSF / CI

| Source | URL | Points clés |
|--------|-----|-------------|
| MobSF | https://github.com/MobSF/Mobile-Security-Framework-MobSF | Analyse statique/dynamique APK ; Docker + REST ; optionnel en CI (lourd) |
| Android Lint | intégration AGP `:app:lintDebug` | Checks cleartext, exported, world-readable, etc. |

---

## 2. Comparaison code SoundGroove (Phase 2)

### 2.1 Stockage local

| Élément | État avant audit | Écart |
|---------|------------------|-------|
| `soundgroove_prefs` (SharedPreferences clair) | Thème, EQ, session lecture (IDs médias), stats, historique recherche | **Pas de tokens Firebase / session auth** → écart M9 faible pour secrets |
| Token remote WebSocket | Mémoire seule (`RemoteHostServer.sessionToken`) | OK — pas persisté en clair |
| DataStore | `GestureHintsStore` (hints UI) non chiffré | Acceptable (non sensible) |
| Security Crypto / EncryptedSharedPreferences | Absent | N/A (déprécié) |
| SecurePrefs Keystore | Absent → **ajouté** | Infrastructure pour futurs secrets |

**Reco appliquée :** ne pas migrer massivement les prefs UX vers chiffrement (coût / Keystore brittle). Fichier dédié `SecurePrefs` + exclusion backup. Migration DataStore+Tink documentée pour plus tard.

### 2.2 Firebase (Firestore / RTDB / Storage)

| Fichier | Présent ? |
|---------|-----------|
| `firestore.rules` | **Non** |
| `database.rules.json` | **Non** |
| `storage.rules` | **Non** |
| `google-services.json` | **Non** |
| Dépendances Firebase / plugin google-services | **Non** |

**Conclusion : pas de backend Firebase dans ce projet Android.** Aucune règle `allow read, write: if true` à corriger. N/A.

### 2.3 OWASP Mobile Top 10 — mapping code

| ID | Risque | Constat SoundGroove | Action |
|----|--------|---------------------|--------|
| M1 | Credentials | Pas de clés API hardcodées ; LRCLIB sans clé ; remote PIN/token en RAM | BuildConfig + local.properties pour endpoints ; SecurePrefs prêt |
| M2 | Supply chain | Dépendances Gradle/Maven ; pas de lock SBOM formalisé | Reporté (Dependabot / OSVscan) |
| M3 | AuthZ | Remote : PIN + token UUID ; Auto : MediaLibrarySession exporté (requis) | Documenté ; PIN TTL + rate-limit déjà en place |
| M4 | Validation | Room `@Query` paramétré ; `execSQL` seulement en migrations ; remote JSON minimal | OK |
| M5 | Communication | LRCLIB en HTTPS ; pas de NSC avant audit ; cleartext non forcé | **NSC + usesCleartextTraffic=false** |
| M6 | Privacy | allowBackup=true ; bibliothèque locale | Exclusions SecurePrefs ; backup playlists volontaire |
| M7 | Binary | `isMinifyEnabled=false` avant | **R8 minify + shrink + proguard-rules** |
| M8 | Misconfig | FGS `mediaPlayback` OK ; exported service Auto OK ; widget exported OK | NSC + lint |
| M9 | Storage | Prefs UX en clair (non secrets) | SecurePrefs + doc |
| M10 | Crypto | Pas de crypto custom faible ; remote token UUID | SecurePrefs AES-GCM Keystore |

### 2.4 Réseau

- **HTTPS :** `LrcLibClient` → `https://lrclib.net` (désormais via `BuildConfig.LRCLIB_BASE_URL`).
- **usesCleartextTraffic :** non déclaré avant (défaut API 28+ = false) → **explicitement `false` + NSC**.
- **Pinning :** **non implémenté (choix documenté)** — API publique non authentifiée, pas de PII/tokens ; pinning casserait à la rotation cert sans bénéfice net. Réévaluer si API privée / auth.
- **WebSocket LAN :** serveur `ws://` local (host téléphone) — hors scope NSC client ; surface LAN volontaire (opt-in Settings).

### 2.5 AndroidManifest

| Item | État | Note |
|------|------|------|
| Permissions média / Internet / FGS mediaPlayback / notifications | Justifiées | Lecture bibliothèque + lecture audio + notif |
| `MainActivity` exported | true | Launcher — requis |
| `PlaybackService` exported + FGS `mediaPlayback` | true | **Requis Android Auto / Media3 MediaLibraryService** |
| Widget receiver exported | true | Requis AppWidget |
| `WidgetActionReceiver` | exported=false | OK |
| FileProvider | exported=false | OK |

### 2.6 R8 / ProGuard

- Avant : `isMinifyEnabled = false`.
- Après : release minify + shrinkResources + `proguard-rules.pro` Room/Media3/Rive/WebSocket.
- `gradle.properties` : pas de `android.enableR8.fullMode=false` → full mode AGP 8.13 OK.

### 2.7 Clés API

- Aucune clé Firebase / Stripe / etc. hardcodée trouvée.
- `google-services.json` : N/A.
- `local.properties` déjà dans `.gitignore`.
- Pattern BuildConfig pour `LRCLIB_BASE_URL` ajouté.

### 2.8 SAST

- Avant : pas de workflow sécurité.
- Après : `app/lint.xml` + `.github/workflows/android-security.yml` (`lintDebug`) + section MobSF optionnelle.

---

## 3. Correctifs appliqués (Phase 3)

1. `app/src/main/res/xml/network_security_config.xml` — cleartext interdit, trust system CAs.  
2. `AndroidManifest.xml` — `networkSecurityConfig` + `usesCleartextTraffic="false"`.  
3. `SecurePrefs.kt` — AES-256-GCM + Android Keystore (alternative post-ESP).  
4. `backup_rules.xml` / `data_extraction_rules.xml` — exclude `soundgroove_secure_prefs.xml`.  
5. Release : `isMinifyEnabled=true`, `isShrinkResources=true`, `proguard-rules.pro` enrichi.  
6. `buildConfigField` `LRCLIB_BASE_URL` depuis `local.properties` ; `LrcLibClient` mis à jour.  
7. `app/lint.xml` + lint block Gradle.  
8. CI : `.github/workflows/android-security.yml`.  
9. `.gitignore` : `*.jks`, `*.keystore`, `keystore.properties`.

---

## 4. Reportés / N/A

| Sujet | Statut |
|-------|--------|
| Firebase rules | **N/A** — pas de Firebase |
| Migration mass prefs → DataStore+Tink | Reporté (alpha / non urgent sans secrets) |
| Certificate pinning LRCLIB | **Non** — choix volontaire (API publique) |
| MobSF en CI Docker | Documenté, non branché par défaut (lourd) |
| SBOM / Dependabot | Reporté |
| Restriction callers Auto (package allowlist) | Reporté — Media3/Auto nécessite browse large ; à durcir si surface abuse |

---

## 5. Checklist SAST / MobSF (manuel)

```bash
# Lint sécurité (local ou CI)
.\gradlew.bat :app:lintDebug

# APK debug
.\gradlew.bat assembleDebug

# APK release (vérifie R8)
.\gradlew.bat assembleRelease

# MobSF optionnel
docker pull opensecurity/mobile-security-framework-mobsf:latest
docker run -it --rm -p 8000:8000 opensecurity/mobile-security-framework-mobsf:latest
# UI http://localhost:8000 — upload APK release
```

---

## 6. Artefacts & builds

| Artefact | Chemin |
|----------|--------|
| Doc audit | `docs/MOBILE_SECURITY_AUDIT_2026-08-21.md` |
| APK debug | `app/build/outputs/apk/debug/app-debug.apk` |
| APK release | `app/build/outputs/apk/release/` (à régénérer) |
| Rapport lint | `app/build/reports/lint-results-debug.html` |

**Builds (2026-08-21) :**

- `assembleDebug` : **SUCCESS** (JAVA_HOME → Android Studio JBR 21 ; le `JAVA_HOME=...\jdk-21` système est invalide — dossier réel `jdk-21.0.12`).
- `assembleRelease` : **échoué** sur verrou Windows de `R.jar` (`processReleaseResources`, fichier utilisé) — **pas** une erreur R8/ProGuard. Relancer hors IDE si besoin pour valider minify.

---

*Fin d’audit — aucun commit/push effectué dans le cadre de cette session.*
