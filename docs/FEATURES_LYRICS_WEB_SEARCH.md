# Recherche de paroles in-app

## Contexte

L'utilisateur souhaite rechercher des paroles **sans quitter SoundGroove**. La recherche est initiée manuellement ; l'app n'agrège ni ne ré-héberge le contenu des sites tiers.

## Options évaluées

| Option | Avantages | Inconvénients | Clé API |
|--------|-----------|---------------|---------|
| **WebView Google Search** | Intégration native, navigation arrière WebView, copier/coller Android standard, aucune clé | Google peut afficher un consentement cookies / CAPTCHA ; dépend du rendu mobile | Non |
| **Chrome Custom Tabs (plein écran)** | Rendu Chrome fidèle, barre d'adresse familière | Quitte visuellement l'UI Compose (overlay système), retour moins contrôlé | Non |
| **Google Custom Search JSON API** | Résultats structurés, UI entièrement custom | Clé API + quota, coût, CGU restrictives pour réutilisation de snippets | Oui |

## Choix retenu

**WebView** avec URL `https://www.google.com/search?q={titre}+{artiste}+lyrics` :

- Pas de clé API.
- L'utilisateur navigue librement (résultats Google → Genius, AZLyrics, etc.).
- Copie depuis la page WebView → presse-papiers Android → bouton « Coller depuis le presse-papiers » → éditeur SoundGroove → enregistrement local.

## Implémentation

- `LyricsWebSearchScreen` : WebView + barre supérieure (retour) + barre inférieure (coller).
- User-Agent mobile Chrome Android raisonnable.
- `BackHandler` : historique WebView puis fermeture de l'écran.
- Depuis `LyricsScreen` (état vide) : bouton « Chercher en ligne » → ouverture in-app (pas `ACTION_VIEW`).

## Note CGU

Recherche **initiée par l'utilisateur** via une WebView standard. SoundGroove ne scrape pas automatiquement les paroles depuis Google ; l'utilisateur copie manuellement le texte qu'il choisit d'enregistrer.

## Pourquoi pas de synchronisation ligne par ligne avec Google

**Verdict : non, pas de synchro temps réel ligne par ligne possible sur la recherche Google, et ce n'est pas un manque d'implémentation — c'est une limite structurelle.**

- Une page de résultats Google (ou les sites tiers qu'elle référence : Genius, AZLyrics, etc.) est du **HTML libre**, sans format de timestamps garanti ni stable. Il n'y a rien d'équivalent à un fichier `.lrc` à parser dans le DOM.
- Scraper la page pour tenter d'en extraire des horodatages serait :
  - **fragile** — la mise en page Google change sans préavis, casserait le parsing à tout moment ;
  - **contraire aux CGU de Google** — SoundGroove ne scrape pas et ne ré-héberge pas le contenu de pages tierces (cf. section précédente).
- Une vraie synchronisation ligne par ligne nécessite des **paroles horodatées (format LRC, `[mm:ss.xx]`)** rapprochées de la position de lecture Media3 — c'est exactement ce que fait déjà `LyricsRepository` / `LrcParser` avec LRCLIB ou un fichier `.lrc` local.

### Ce qui a été implémenté à la place

1. **Repère de lecture temps réel superposé à la WebView** (`PlaybackPositionOverlay` dans `LyricsWebSearchScreen`) : mini-lecteur (titre, position `mm:ss / mm:ss`, barre de progression, play/pause) visible en permanence pendant la recherche Google, sans revenir au Player. Ce n'est pas une synchro ligne par ligne mais ça donne un contexte temporel utile pendant la lecture.
2. **Bandeau explicatif** au-dessus du bouton « Coller » rappelant que Google n'est pas synchronisé et qu'un texte au format LRC collé sera automatiquement détecté et synchronisé (le pipeline `LyricsRepository.parseRaw` → `LrcParser.isLikelySynced` gère déjà ce cas, y compris quand le texte vient d'un copier-coller manuel).

### Alternatives concrètes pour l'utilisateur

- **Préférer une source LRC** : chercher `"<titre> <artiste> lrc"` plutôt que `lyrics` simple, sur des sites qui proposent des paroles synchronisées, puis coller le résultat — SoundGroove détecte le format et affiche l'écran Paroles synchronisé (auto-scroll, mise en surbrillance ligne par ligne) exactement comme avec LRCLIB.
- **Laisser LRCLIB faire le travail automatiquement** : c'est déjà la recherche par défaut (`LyricsRepository.fetchOnlineLyrics`) avant même de proposer la recherche Google — Google reste un filet de secours pour les morceaux introuvables sur LRCLIB.
- **Fichier `.lrc` local** : déposer un fichier `.lrc` du même nom que le fichier audio dans le même dossier (`LyricsFileResolver`) pour une sync automatique persistante, sans passer par la recherche en ligne.
