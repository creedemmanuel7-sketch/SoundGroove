# Build Android (Windows / PowerShell)

Cible du dépôt : **AGP 8.13.2**, **Gradle 8.14.3** (wrapper), bytecode **Java 17**. Un JDK **17** ou **21** convient. `local.properties` est gitignoré : ne pas le committer.

## 1. `JAVA_HOME` invalide

`.\gradlew.bat` ne lance Gradle que si `%JAVA_HOME%\bin\java.exe` existe. L’erreur suivante signifie que la variable est définie, mais que ce fichier n’est pas là (dossier absent, mauvais nom, JDK désinstallé, quotes, ou JRE sans `bin\java.exe`) :

```text
ERROR: JAVA_HOME is set to an invalid directory: C:\Program Files\Java\jdk-21
```

Ne suppose pas que `C:\Program Files\Java\jdk-21` existe. Un cas déjà vu sur cette machine : `JAVA_HOME` pointait vers `jdk-21` alors que le dossier réel était `jdk-21.0.12`.

### Vérifier

Dans PowerShell, depuis n’importe quel répertoire :

```powershell
Write-Output "JAVA_HOME=$env:JAVA_HOME"
Test-Path $env:JAVA_HOME
Test-Path "$env:JAVA_HOME\bin\java.exe"
Test-Path "$env:JAVA_HOME\bin\javac.exe"
Get-ChildItem "C:\Program Files\Java" -ErrorAction SilentlyContinue
Get-Command java -ErrorAction SilentlyContinue | Format-List
```

Chercher le JBR d’Android Studio (souvent plus fiable qu’un JDK système) :

```powershell
@(
  "C:\Program Files\Android\Android Studio\jbr",
  "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
) | ForEach-Object { "$_  java=$(Test-Path (Join-Path $_ 'bin\java.exe'))" }
```

Autres emplacements fréquents : `C:\Program Files\Eclipse Adoptium\`, `C:\Program Files\Microsoft\jdk-*`, `C:\Program Files\Java\jdk-21.0.*`.

### Corriger pour la session courante

Remplace le chemin par un dossier **qui a** `bin\java.exe` (JBR Android Studio ou JDK 17/21 réel) :

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
# ou, si le dossier existe vraiment :
# $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
# $env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
& "$env:JAVA_HOME\bin\java.exe" -version
cd $env:USERPROFILE\StudioProjects\SoundGroove
.\gradlew.bat :app:assembleDebug
```

### Persister (utilisateur Windows)

`setx` tronque les valeurs longues. Préférer :

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")
```

Ferme **toutes** les fenêtres PowerShell / Android Studio, puis rouvre un terminal. Vérifier : `$env:JAVA_HOME`.

UI : Paramètres → Système → À propos → Paramètres système avancés → Variables d’environnement → `JAVA_HOME` (utilisateur) = dossier du JDK/JBR, **sans** `\bin` et **sans** guillemets.

### Aucun JDK

Installer **Eclipse Temurin 17 ou 21**, **ou** pointer `JAVA_HOME` vers le JBR d’Android Studio (`...\Android Studio\jbr`). Inutile de forcer un JDK dans le dépôt.

## 2. SDK Android + Flutter (si Gradle s’en plaint)

`local.properties` à la racine (créé par Android Studio ; gitignoré) :

```properties
sdk.dir=C:\\Users\\credo\\AppData\\Local\\Android\\Sdk
flutter.sdk=C:\\chemin\\vers\\flutter
```

Le module `flutter_queue/` est **optionnel** : sans `flutter.sdk` valide, `:app:assembleDebug` peut quand même produire l’APK (file d’attente Flutter désactivée).

Si le build échoue ensuite sur Flutter :

1. Installer le [Flutter SDK](https://docs.flutter.dev/get-started/install/windows)
2. Ajouter `flutter.sdk=...` à côté de `sdk.dir` (doubler les `\` ou utiliser des `/`)
3. Dans le dépôt : `cd flutter_queue ; flutter pub get` (génère `.android/`, gitignoré)

## 3. APK debug

```powershell
.\gradlew.bat :app:assembleDebug
```

Fichier : `app\build\outputs\apk\debug\app-debug.apk`
