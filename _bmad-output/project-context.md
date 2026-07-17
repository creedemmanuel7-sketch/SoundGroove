---
project_name: 'SoundGroove'
user_name: 'Credo'
date: '2026-07-17'
sections_completed: ['technology_stack']
existing_patterns_found: 12
discovery_complete: true
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

_Documenté après la phase de découverte — règles détaillées à générer à l’étape suivante._

### Application Android (`app/`)

- Kotlin 2.0.21 + AGP 8.13.2 + KSP 2.0.21-1.0.28
- compileSdk / targetSdk 36, minSdk 24, JVM target 11
- Jetpack Compose (BOM 2024.09.00) + Material 3
- Navigation Compose 2.8.3
- Lifecycle / ViewModel Compose
- Media3 ExoPlayer + Session 1.3.1
- Room 2.8.4 (KSP)
- DataStore Preferences 1.1.1 (+ SharedPreferences legacy pour thème)
- Coil Compose 2.6.0
- Palette KTX, Guava Android

### Site marketing (`website/`)

- Next.js ^15.3.3 + React 19 + TypeScript 5.8 (strict)
- Déploiement Netlify (`@netlify/plugin-nextjs`)

## Critical Implementation Rules

_À compléter collaborativement à l’étape de génération des règles._
