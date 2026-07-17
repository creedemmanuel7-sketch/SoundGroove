# Stockage local & gestion du cache

## Contexte

Signalement utilisateur : APK/app ~25 Mo à l'installation, ~55 Mo après avoir consulté
les paroles de 3-4 morceaux (recherche en ligne incluse). Ce document cartographie tout
ce que SoundGroove écrit sur le stockage interne de l'app et explique les limites mises
en place pour éviter une croissance non bornée.

## Cartographie complète

| Donnée | Emplacement | Nature | Taille typique | Géré par |
|---|---|---|---|---|
| Cache texte des paroles | `filesDir/lyrics/<songId>.txt` | Cache (re-téléchargeable/re-lisible) | Quelques Ko/morceau | `LyricsCacheStore` |
| Pochettes personnalisées | `filesDir/cover_overrides/<songId>.jpg` | **Import volontaire** | ~50–300 Ko/pochette (après compression) | `CoverArtStorage` |
| Cartes de partage | `cacheDir/share/*.png` | Cache temporaire (durée de l'Intent de partage) | ~1–3 Mo/carte (1080×1920 PNG) | `ShareCardGenerator` |
| Cache WebView (recherche Google) | `app_webview/` (dossier racine app) + `cacheDir/WebView` ou `org.chromium.android_webview` | Cache navigateur (HTML/CSS/JS/images des pages visitées) | **Plusieurs Mo par session de recherche** | Géré par le système Android WebView, purgé par `LyricsWebSearchScreen` + `StorageMaintenance` |
| Base Room (favoris, playlists, historique, overrides métadonnées) | `databases/*.db` | Données utilisateur | Quelques centaines de Ko | `Database.kt` |
| SharedPreferences (thème, réglages lecture) | `shared_prefs/*.xml` | Réglages | Quelques Ko | `PlaybackPreferences`, `SoundGrooveViewModel` |
| Fichiers `.lrc`/`.txt` voisins de l'audio | Dossier de l'utilisateur (stockage partagé, hors bac à sable de l'app) | **Fichiers de l'utilisateur** | Variable | `LyricsFileResolver` — jamais touché par l'app |

## Diagnostic : d'où vient la hausse 25 → 55 Mo

Le texte brut d'une parole (LRC/plain text) ne dépasse quasiment jamais quelques Ko :
un cache de 3-4 morceaux ne peut pas expliquer +30 Mo à lui seul. Les vrais suspects,
par ordre de probabilité :

1. **Cache WebView de la recherche Google de paroles** (`LyricsWebSearchScreen`) — de
   très loin le plus gros contributeur probable. Chaque recherche Google charge une page
   HTML complète avec ses assets (JS, CSS, images, polices) ; la WebView Android persiste
   ce cache sur disque par défaut (`app_webview/`), **sans jamais le vider seule**. 3-4
   sessions de recherche peuvent facilement représenter plusieurs dizaines de Mo.
2. **Pochettes personnalisées non compressées** (`CoverArtStorage`, avant correctif) —
   l'image de galerie était copiée telle quelle (`copyTo`), sans redimensionnement. Une
   photo moderne pèse 3–8 Mo ; 2-3 pochettes changées suffisent à expliquer une grosse
   partie de la hausse.
3. **Cartes de partage PNG accumulées** (`ShareCardGenerator`, avant correctif) — chaque
   partage laissait un PNG 1080×1920 (~1-3 Mo) dans le cache sans jamais le nettoyer.

Le cache texte des paroles lui-même (`LyricsCacheStore`) n'est **pas** la cause : il
reste de l'ordre de quelques dizaines de Ko même pour une bibliothèque entière.

## Ce qui a été implémenté

### 1. Cache paroles borné + éviction LRU (`LyricsCacheStore`)
- Plafond : 150 fichiers **et** 5 Mo au total.
- La date de dernière modification du fichier sert d'horodatage d'usage, mise à jour à
  chaque lecture (`read`), pas seulement à l'écriture : éviction LRU réelle (moins
  récemment consulté supprimé en premier).

### 2. Compression des pochettes personnalisées (`CoverArtStorage`)
- Décodage avec sous-échantillonnage (`inSampleSize`) pour éviter tout pic mémoire.
- Redimensionnement à 1024 px de côté maximum + recompression JPEG qualité 85.
- Reste un **import volontaire** : jamais supprimé automatiquement, jamais par éviction.

### 3. Cache de partage auto-nettoyé (`ShareCardGenerator`)
- Purge du dossier `cacheDir/share/` avant chaque nouvelle carte générée (un seul fichier
  temporaire à la fois, plus d'accumulation).

### 4. WebView : cache désactivé + purge à la fermeture (`LyricsWebSearchScreen`)
- `WebSettings.cacheMode = LOAD_NO_CACHE` : plus de cache disque persistant entre deux
  sessions de recherche.
- `DisposableEffect` : `clearHistory()` + `clearCache(true)` + `clearFormData()` +
  `destroy()` systématiques à la fermeture de l'écran.

### 5. Utilitaire central `StorageMaintenance`
- `computeBreakdown(context)` : taille de chaque catégorie (paroles, partage, WebView,
  pochettes — à titre informatif).
- `clearClearableCaches(context)` : vide paroles + partage + WebView (jamais les
  pochettes personnalisées, jamais les `.lrc`/`.txt` voisins de l'audio).

### 6. Réglages → section « Stockage »
- Taille approximative du cache affichée (« Vider le cache · X Mo »).
- Bouton avec confirmation explicite rappelant ce qui est/n'est pas supprimé.

## Ce qui n'est jamais supprimé automatiquement

- Pochettes personnalisées (`cover_overrides/`) : choix explicite de l'utilisateur.
- Fichiers `.lrc`/`.txt` posés par l'utilisateur à côté de ses fichiers audio : ce sont
  ses fichiers, sur son stockage partagé, jamais dans le bac à sable de l'app.
- Base Room et SharedPreferences (favoris, playlists, réglages) : données utilisateur,
  gérées par les actions dédiées existantes (Réglages → Bibliothèque/Sauvegarde).
