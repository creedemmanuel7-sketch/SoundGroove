# Audit sécurité backend / surfaces API — SoundGroove
**Date :** 2026-08-21  
**Périmètre :** inventaire + durcissement défensif (pas de PoC offensif)

## 1. Inventaire des surfaces

| Surface | Type | Backend / API ? | Verdict |
|---|---|---|---|
| `app/` (Android) | App locale (Compose) | **Non** — pas de serveur SoundGroove. Appels clients vers API publique LRCLIB (`https://lrclib.net`) et URLs de recherche Google/Genius (WebView). | Hors périmètre « backend » ; pas de secrets hardcodés critiques. |
| `website/` | Next.js 15 + Netlify (`@netlify/plugin-nextjs`) | **Pas de Route Handlers `/api`**, pas de Netlify Functions, pas d’auth, pas d’upload, pas de DB. Landing + téléchargement APK. | Surface web à durcir (headers, env, rate-limit préventif). |
| `desktop-web/` | Stub Tauri (`src-tauri/gen` uniquement) | **Aucun serveur** ni UI web packagée. | RAS backend. |
| `scripts/`, `tools/` | Scripts build / assets | Pas d’API runtime. | Hors périmètre. |

**Conclusion :** SoundGroove n’a **pas** de backend applicatif (pas d’API auth, upload, recherche musicale hébergée). L’audit et les correctifs portent sur le **site vitrine Netlify/Next** et la posture défensive pour d’éventuelles futures routes `/api/*`.

## 2. Findings

### F-01 — Absence d’en-têtes HTTP de sécurité (sévérité : **Moyenne**)
- **Avant :** `netlify.toml` et `next.config.ts` ne définissaient ni CSP, HSTS, X-Frame-Options, etc.
- **Risque :** clickjacking, MIME sniffing, absence HSTS.
- **Correctif :** headers dans `website/netlify.toml`, `website/next.config.ts`, et `website/middleware.ts`.

### F-02 — `.env` non ignoré de façon exhaustive (sévérité : **Basse**)
- **Avant :** seuls `.env*.local` étaient ignorés ; un fichier `.env` pouvait être versionné par erreur.
- **Correctif :** `.gitignore` racine + `website/.gitignore` ignorent `.env` / `.env.*` (sauf `.env.example`) ; ajout de `.netlify`.

### F-03 — URL APK non validée (sévérité : **Basse**)
- **Avant :** `NEXT_PUBLIC_APK_URL` injectée telle quelle dans `href` (risque `javascript:` / schéma non https si mal configurée).
- **Correctif :** `resolveSafeApkUrl()` (Zod) — https ou chemin relatif sûr, sinon fallback local.

### F-04 — Pas de rate-limiting (sévérité : **Info** → corrigé préventivement)
- **Avant :** aucune limite (pas de routes sensibles existantes).
- **Correctif :** middleware rate-limit in-memory par IP sur `/api/*` (auth/upload/search/api) et `/downloads/*`. Message 429 générique, sans fuite d’info. Placeholders Upstash documentés dans `.env.example`.

### F-05 — Pas de validation/sanitization serveur (sévérité : **Info** → lib prête)
- **Avant :** N/A (pas d’API).
- **Correctif :** `lib/security/validate.ts` (Zod) + `sanitize.ts` (XSS / strip opérateurs NoSQL) pour futures routes.

### F-06 — Secrets hardcodés (sévérité : **OK**)
- Aucun token / clé API / credential DB trouvé dans le site.
- Android : URL publique LRCLIB sans clé — acceptable ; pas de secret critique hardcodé.
- `local.properties` déjà dans `.gitignore`.

## 3. Correctifs livrés

| Fichier | Rôle |
|---|---|
| `website/middleware.ts` | Rate-limit chemins sensibles + headers sécurité |
| `website/lib/security/rate-limit.ts` | Compteurs IP / catégories |
| `website/lib/security/sanitize.ts` | Escape HTML, strip NoSQL |
| `website/lib/security/validate.ts` | Schémas Zod + URL APK sûre |
| `website/next.config.ts` | Headers + `poweredByHeader: false` |
| `website/netlify.toml` | Headers CDN Netlify |
| `website/components/DownloadButton.tsx` | URL APK validée |
| `website/.env.example` | Placeholders sans secrets |
| `website/.gitignore` / `.gitignore` | `.env`, `.netlify` |

## 4. Ce qui n’existe pas (documenté)

- Pas d’API d’authentification / connexion
- Pas d’endpoint d’upload
- Pas d’API de recherche musicale côté serveur SoundGroove
- Pas de Netlify Functions / Edge Functions custom
- Pas de base de données applicative
- App Android : musique **locale** ; paroles via client LRCLIB (tiers)

## 5. Recommandations futures

1. Si une API auth/upload/search est ajoutée : réutiliser `parseJsonBody` + rate-limit existants ; brancher Upstash pour un plafond multi-instance.
2. Préférer des nonces CSP plutôt que `'unsafe-inline'` pour les scripts quand le design le permettra.
3. Ne pas committer de secrets ; configurer `NEXT_PUBLIC_*` uniquement dans le dashboard Netlify.

## 6. Vérification

- Build Next.js attendu après changements (middleware + libs TypeScript).
- Pas de commit/push dans le cadre de cet audit.
