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
