pluginManagement {
    val flutterSdkPath = run {
        val properties = java.util.Properties()
        val local = file("local.properties")
        if (local.exists()) {
            local.inputStream().use { properties.load(it) }
        }
        properties.getProperty("flutter.sdk")
    }
    if (!flutterSdkPath.isNullOrBlank()) {
        val flutterGradle = file("$flutterSdkPath/packages/flutter_tools/gradle")
        if (flutterGradle.exists()) {
            includeBuild(flutterGradle)
        }
    }
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS : Flutter add-to-app déclare parfois des repos projet ;
    // on centralise google / mavenCentral / Flutter engine ici.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://storage.googleapis.com/download.flutter.io") }
    }
}

rootProject.name = "SoundGroove"
include(":app")

val flutterSdk: String? = run {
    val properties = java.util.Properties()
    val local = file("local.properties")
    if (local.exists()) {
        local.inputStream().use { properties.load(it) }
    }
    properties.getProperty("flutter.sdk")
}
val flutterModule = file("flutter_queue")
val flutterAndroid = file("flutter_queue/.android/Flutter")
val includeFlutterQueue = flutterModule.resolve("pubspec.yaml").exists() &&
    flutterAndroid.exists() &&
    !flutterSdk.isNullOrBlank() &&
    file("$flutterSdk/bin/flutter").exists()

extra["soundgrooveIncludeFlutter"] = includeFlutterQueue
if (includeFlutterQueue) {
    include(":flutter")
    project(":flutter").projectDir = flutterAndroid
    val loader = file("$flutterSdk/packages/flutter_tools/gradle/module_plugin_loader.gradle")
    if (loader.exists()) {
        apply(from = loader)
    }
}
