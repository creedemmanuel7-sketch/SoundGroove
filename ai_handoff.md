# Document de Transition et Spécifications pour l'IA Suivante (SoundGroove Project)

Ce document contient toutes les informations, l'architecture du projet, l'état actuel de la base de code, les préférences de l'utilisateur, et les morceaux de code nécessaires pour continuer directement le développement de l'application **SoundGroove** sans perte de contexte.

---

## 📌 1. Vue d'Ensemble & Architecture Actuelle

L'application a été migrée d'un design monolithique vers une architecture **MVVM propre avec Jetpack Compose** :

*   **MainActivity.kt** : Configure le service de lecture et héberge l'arbre Jetpack Compose principal. Elle héberge actuellement l'écran du lecteur (`PlayerScreen`), la barre de navigation du bas (`BottomNavBar`) et le lecteur réduit local (`MiniPlayer`).
*   **viewmodel/SoundGrooveViewModel.kt** : Unique source de vérité de l'application (gère l'état d' ExoPlayer, la liste des morceaux, les favoris persistants, les playlists et le mode de tri).
*   **ui/navigation/AppNavigation.kt** : Gère la navigation Jetpack de l'application avec des animations fluides de transition.
*   **ui/navigation/LegacyMainHost.kt** : Sert de pont transitoire entre le ViewModel et l'ancien conteneur d'onglets `MainScreen` dans `MainActivity.kt`.
*   **ui/screens/** : Contient les nouveaux écrans autonomes migrés (`PlaylistDetailScreen`, `AlbumDetailScreen`, `ArtistDetailScreen`, `SearchScreen`, `ThemeSelectionScreen`).
*   **ui/components/** : Contient des composants réutilisables extraits (`BottomSheets.kt`, `SongListItem.kt`, `SoundGrooveLogo.kt`, ainsi qu'un composant autonome inutilisé `MiniPlayer.kt`).
*   **Base de Données (Room)** : Persistance gérée via `Database.kt` et injectée dans le ViewModel pour sauvegarder les favoris, les playlists (avec relations de chansons) et l'historique d'écoute.

---

## 🎨 2. Préférences Graphiques & Contraintes Utilisateur (À Respecter Absolument)

1.  **Ergonomie du PlayerScreen** : La pochette de l'album doit être abaissée et ne pas dépasser une taille de **270dp à 300dp** pour rester facilement manipulable à une main.
2.  **Effet de Verre (Glassmorphism)** : Utiliser le flou (`Modifier.blur()`) et les bordures semi-transparentes (`GlassBorder` dans `Glass.kt`) de façon prononcée sur le lecteur et les cartes.
3.  **Choix des Couleurs au Démarrage (Onboarding)** : Demander le choix de la couleur d'accentuation principale à l'utilisateur parmi 3 thèmes au premier lancement (sauvegardé dans `SharedPreferences`) :
    *   `CLASSIC_DARK` : Couleur d'accent verte émeraude (`ClassicAccent`, style Spotify).
    *   `ORIGINAL_PURPLE` : Couleur d'accent violette d'origine (`LightPurple`).
    *   `CORAL_VIBRANT` : Couleur d'accent corail/orange chaud (`CoralAccent`).
4.  **Zéro Emojis dans l'interface** : Remplacer systématiquement tous les emojis textuels par des icônes vectorielles standard ou personnalisées.
5.  **Pas de gestes vidéo** : Ne pas ajouter de contrôles par glissement vertical pour la luminosité/volume sur l'affichage audio. Conserver uniquement les gestes audio (glissement horizontal sur la pochette pour zapper, vertical pour afficher/masquer la file d'attente).

---

## ⚠️ 3. Tâches Urgentes : Icônes Personnalisées & Couleur Dynamique

L'utilisateur a importé des icônes SVG personnalisées (converties en XML vectoriels dans `app/src/main/res/drawable/`). Cependant, par suite de fusions de branches, `MainActivity.kt` utilise actuellement des icônes Material Design standards (`Icons.Filled.*`) au lieu de ces ressources personnalisées. De plus, la couleur dynamique s'adaptant à la pochette n'est pas encore pleinement câblée.

Voici les modifications de code exactes à appliquer pour corriger ces deux points :

### A. Intégration des Icônes Personnalisées dans la barre du bas
Dans `MainActivity.kt`, modifier `BottomNavBar` pour utiliser les icônes du dossier `res/drawable` :

```kotlin
@Composable
fun BottomNavBar(selectedTab: Int, accentColor: Color, onTabSelected: (Int) -> Unit) {
    data class NavItem(
        val label: String,
        val iconRes: Int
    )

    val tabs = listOf(
        NavItem("Accueil", R.drawable.ic_home),
        NavItem("Bibliothèque", R.drawable.ic_playlists),
        NavItem("Recherche", R.drawable.ic_search),
        NavItem("Profil", R.drawable.ic_profile)
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        cornerRadius = 24.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0x33000000), Color(0x1A000000))))
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onTabSelected(index) }
                        .padding(8.dp)
                ) {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = item.label,
                        tint = if (selectedTab == index) accentColor else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        color = if (selectedTab == index) accentColor else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                    )
                    if (selectedTab == index) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(accentColor, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
```

### B. Helper d'Extraction de Couleur Dynamique (Coil + Palette API)
Ajouter cette fonction composable dans `MainActivity.kt` (ou sous forme d'utilitaire) pour extraire la couleur de la pochette active de manière asynchrone :

```kotlin
import androidx.palette.graphics.Palette
import android.graphics.drawable.BitmapDrawable
import coil.request.SuccessResult
import coil.Coil

@Composable
fun rememberAlbumArtAccentColor(albumArtUri: Uri?, defaultColor: Color): Color {
    val context = LocalContext.current
    var extractedColor by remember(albumArtUri) { mutableStateOf(defaultColor) }

    LaunchedEffect(albumArtUri) {
        if (albumArtUri == null) {
            extractedColor = defaultColor
            return@LaunchedEffect
        }
        try {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(albumArtUri)
                .allowHardware(false) // Nécessaire pour extraire les pixels du Bitmap
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    val bitmap = drawable.bitmap
                    Palette.from(bitmap).generate { palette ->
                        val swatch = palette?.vibrantSwatch 
                            ?: palette?.dominantSwatch 
                            ?: palette?.lightVibrantSwatch
                        swatch?.rgb?.let { colorInt ->
                            extractedColor = Color(colorInt)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            extractedColor = defaultColor
        }
    }

    return extractedColor
}
```

### C. Application de la Couleur Dynamique et des Icônes Personnalisées au `PlayerScreen`
Mettre à jour `PlayerScreen` dans `MainActivity.kt` pour consommer `accentColor: Color`, appeler le helper ci-dessus, et appliquer la couleur et les icônes personnalisées :

```kotlin
fun PlayerScreen(
    song: Song,
    isPlaying: Boolean,
    accentColor: Color, // Couleur du thème utilisateur par défaut
    isFavorite: Boolean,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    onSwipeDown: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    player: androidx.media3.common.Player
) {
    // 1. Extraire la couleur de la pochette
    val dynamicAccentColor = rememberAlbumArtAccentColor(albumArtUri = song.albumArtUri, defaultColor = accentColor)
    
    // ... [Reste des LaunchedEffect et logique d'état] ...

    // 2. Dans le Header :
    // Remplacer l'icône de fermeture :
    Icon(
        painter = painterResource(R.drawable.ic_close_down),
        contentDescription = "Fermer",
        tint = TextPrimary,
        modifier = Modifier.size(28.dp)
    )

    // Remplacer l'icône options :
    Icon(
        painter = painterResource(R.drawable.ic_options),
        contentDescription = "Options",
        tint = TextPrimary,
        modifier = Modifier.size(24.dp)
    )

    // 3. Dans les informations du morceau :
    // Appliquer la couleur dynamique au nom de l'artiste :
    Text(
        text = song.artist,
        color = dynamicAccentColor, // <-- COULEUR DYNAMIQUE
        fontSize = 14.sp
    )

    // Remplacer l'icône favoris :
    Icon(
        painter = painterResource(if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline),
        contentDescription = "Favori",
        tint = if (isFavorite) Color(0xFFFF6B9D) else TextSecondary,
        modifier = Modifier.size(28.dp).clickable { onToggleFavorite() }
    )

    // 4. Dans le Slider :
    Slider(
        value = if (isSeeking) seekPosition else progress.coerceIn(0f, 1f),
        // ...,
        colors = SliderDefaults.colors(
            thumbColor = dynamicAccentColor,        // <-- COULEUR DYNAMIQUE
            activeTrackColor = dynamicAccentColor,  // <-- COULEUR DYNAMIQUE
            inactiveTrackColor = CardSurface
        )
    )

    // 5. Dans les Contrôles de lecture :
    // Shuffle actif :
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(if (isShuffled) dynamicAccentColor else GlassSurface, CircleShape) // <-- COULEUR DYNAMIQUE
            .clickable {
                isShuffled = !isShuffled
                player.shuffleModeEnabled = isShuffled
            }
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shuffle),
            contentDescription = "Shuffle",
            tint = if (isShuffled) Color.White else TextSecondary
        )
    }

    // Previous :
    Icon(
        painter = painterResource(R.drawable.ic_previous),
        contentDescription = "Précédent",
        tint = TextPrimary
    )

    // Play/Pause :
    Box(
        modifier = Modifier
            .size(68.dp)
            .background(
                Brush.radialGradient(listOf(dynamicAccentColor, dynamicAccentColor.copy(alpha = 0.5f))), // <-- COULEUR DYNAMIQUE
                CircleShape
            )
    ) {
        Icon(
            painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            tint = Color.White
        )
    }

    // Next :
    Icon(
        painter = painterResource(R.drawable.ic_next),
        tint = TextPrimary
    )

    // Repeat actif :
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(if (repeatMode > 0) dynamicAccentColor else GlassSurface, CircleShape) // <-- COULEUR DYNAMIQUE
    ) {
        Icon(
            painter = painterResource(if (repeatMode == 2) R.drawable.ic_repeat_one else R.drawable.ic_repeat),
            tint = if (repeatMode > 0) Color.White else TextSecondary
        )
    }
}
```

---

## 🚀 4. Feuille de Route pour la Suite (Lark Player Parity)

Une fois l'interface et la couleur dynamique rétablies, les phases suivantes doivent être entreprises pour atteindre la parité totale avec Lark Player :

1.  **Onglet "Dossiers" (Folders Tab)** : Explorer la musique locale par arborescence physique de dossiers en utilisant le chemin du fichier (`folderPath`) stocké dans l'objet `Song`.
2.  **Tri Avancé** : Brancher les options de tri de la base de données ou de la liste globale (A-Z, Z-A, Par Artiste, Par Date d'ajout).
3.  **Sleep Timer** : Implémenter le déclenchement de la minuterie de sommeil (déjà partiellement commencée dans `SoundGrooveViewModel.kt` avec `setSleepTimer`). Ajouter un BottomSheet dans le lecteur pour que l'utilisateur choisisse la durée.
4.  **Écran de Paramètres dédiés** : Créer un bel écran moderne et épuré avec effet de verre, accessible depuis l'onglet Profil, affichant des statistiques de lecture (temps d'écoute) et des options pour changer le thème.
