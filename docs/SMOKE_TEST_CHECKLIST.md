# Smoke tests — navigation & paroles

Checklist automatisable (CI locale ou manuelle rapide). Complète les tests unitaires
`NavigationRoutesTest`, `ScrobbleRepositoryTest`, `AutoMediaIdsTest`.

## Prérequis

- App installée (`assembleDebug` OK)
- Au moins 3 fichiers audio locaux + 1 fichier `.lrc` voisin d’un morceau

## Navigation (≈ 5 min)

| # | Action | Résultat attendu |
|---|--------|------------------|
| N1 | Lancer l’app → onglet Accueil | Pas de crash, mini-player ou état vide cohérent |
| N2 | Bibliothèque → Album → retour | Fade retour, pas de double mini-player |
| N3 | Recherche → dossier → liste morceaux | Route `folder/{path}` affiche les titres du dossier |
| N4 | Tap morceau → Player plein écran → retour | Mini-player synchronisé titre/pochette |
| N5 | Paramètres → Retour | Overlay réglages se ferme sans perdre l’onglet |
| N6 | File d’attente depuis Player | QueueScreen s’ouvre, réordonnancement visible |
| N7 | Paramètres / Profil → Mode voiture | Écran sombre plein écran, gros contrôles, listes courtes |

## Paroles (≈ 3 min)

| # | Action | Résultat attendu |
|---|--------|------------------|
| L1 | Morceau avec `.lrc` local → Paroles | Texte synchronisé ou statique affiché |
| L2 | Morceau sans paroles → Recherche web | WebView / résultats sans ANR |
| L3 | Modifier offset sync → relire | Décalage persisté après rotation |
| L4 | Vider cache (Réglages) → rouvrir paroles locales | Rechargement depuis fichier voisin OK |

## Commandes Gradle

### Unitaires (toujours en CI)

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

### Instrumentation Compose smoke (émulateur / appareil)

```bash
./gradlew :app:connectedDebugAndroidTest
```

Test clé : `HomePlayerLyricsSmokeTest` — parcours Home → Player → Lyrics via
`SmokeNavGraph` (`createComposeRule` + `testTag`). Stable sans bibliothèque audio ni
MediaController (évite les flakiness CI).

Autres instrumentés :

- `NavigationRoutesInstrumentedTest` — encodage route dossier
- `ExampleInstrumentedTest` — `packageName`

## CI recommandée

1. Job unit : `assembleDebug` + `testDebugUnitTest`
2. Job instrumenté (émulateur API 30+) : `connectedDebugAndroidTest`  
   Filtrer si besoin : `--tests com.credo.soundgroove.ui.navigation.HomePlayerLyricsSmokeTest`
