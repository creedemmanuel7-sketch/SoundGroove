# SoundGroove — Site vitrine

Landing page de téléchargement pour l'application Android SoundGroove.

## Prérequis

- Node.js 20+
- npm

## Développement local

```bash
cd website
npm install
npm run dev
```

Ouvrir [http://localhost:3000](http://localhost:3000).

## Build de production

```bash
cd website
npm run build
npm start   # prévisualiser le build
```

## Téléchargement APK

Le bouton **Télécharger l'APK** utilise, par ordre de priorité :

1. **`NEXT_PUBLIC_APK_URL`** — URL publique (CDN, GitHub Releases, etc.)
2. **`public/downloads/soundgroove.apk`** — fichier local servi par le site

### Option A — Variable d'environnement (recommandé en prod)

Créer `.env.local` :

```env
NEXT_PUBLIC_APK_URL=https://exemple.com/releases/soundgroove-v1.0.apk
```

### Option B — Fichier local

Copier l'APK compilé dans :

```
website/public/downloads/soundgroove.apk
```

> **Important :** l'APK n'est **pas versionné** dans Git (`.gitignore` : `website/public/downloads/*.apk`, ~28 Mo).
> Il doit être **présent localement** pour que le téléchargement fonctionne en dev.
> Copiez-le avant `npm run dev` ou configurez `NEXT_PUBLIC_APK_URL` en production.

Exemple depuis la racine du repo Android :

```powershell
# Windows
Copy-Item app\build\outputs\apk\debug\app-debug.apk website\public\downloads\soundgroove.apk

# macOS / Linux
cp app/build/outputs/apk/debug/app-debug.apk website/public/downloads/soundgroove.apk
```

Si l'APK n'existe pas encore, compilez-le d'abord :

```powershell
.\gradlew.bat assembleDebug
```

## Déploiement Netlify

1. Créer un site Netlify lié au repo GitHub
2. **Base directory** : `website`
3. **Build command** : `npm run build` (défini dans `netlify.toml`)
4. **Publish directory** : `.next` (géré par `@netlify/plugin-nextjs`)

Variables d'environnement à configurer dans le dashboard Netlify :

| Variable | Description |
|---|---|
| `NEXT_PUBLIC_APK_URL` | URL publique de l'APK (optionnel) |

Le plugin `@netlify/plugin-nextjs` est référencé dans `netlify.toml` et installé automatiquement par Netlify lors du build.

### Déploiement manuel (CLI)

```bash
cd website
npm install
npx netlify deploy --prod
```

## Structure

```
website/
├── app/
│   ├── globals.css      # Styles + tokens brand (#A855F7)
│   ├── layout.tsx       # Metadata + layout racine
│   └── page.tsx         # Landing complète (hero, features, aperçu, FAQ…)
├── components/
│   ├── AppMockup.tsx    # Mockup téléphone hero
│   ├── AppScreens.tsx   # Galerie écrans CSS
│   ├── DownloadButton.tsx
│   ├── FAQ.tsx
│   ├── Features.tsx
│   ├── Footer.tsx
│   ├── InstallGuide.tsx
│   └── Privacy.tsx
├── public/
│   ├── icon.png         # Icône brand (waveform ring)
│   └── downloads/       # APK local (non versionné, .gitkeep)
├── netlify.toml
├── next.config.ts
└── package.json
```

## Brand

- Fond : noir `#050505`
- Accent : violet `#A855F7`
- Typographies : **Syne** (titres) + **Instrument Sans** (corps)
