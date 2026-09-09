import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localProp(key: String, default: String): String =
    (localProperties.getProperty(key) ?: default).replace("\"", "\\\"")

android {
    namespace = "com.credo.soundgroove"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.credo.soundgroove"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Secrets / endpoints optionnels via local.properties (gitignoré).
        // Ex. : lrclib.base.url=https://lrclib.net
        buildConfigField(
            "String",
            "LRCLIB_BASE_URL",
            "\"${localProp("lrclib.base.url", "https://lrclib.net")}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        lintConfig = file("lint.xml")
        checkReleaseBuilds = true
        abortOnError = false
        warningsAsErrors = false
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        jniLibs {
            // Rive (et d'autres natives) embarquent libc++_shared.so — pickFirst évite
            // le conflit mergeDebugNativeLibs.
            pickFirsts += listOf(
                "lib/x86/libc++_shared.so",
                "lib/x86_64/libc++_shared.so",
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/arm64-v8a/libc++_shared.so",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-extractor:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation(libs.lottie.compose)
    implementation(libs.rive.android)
    implementation(libs.androidx.startup.runtime)
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:${libs.versions.room.get()}")
    implementation("androidx.room:room-ktx:${libs.versions.room.get()}")
    ksp("androidx.room:room-compiler:${libs.versions.room.get()}")
    testImplementation("androidx.room:room-testing:${libs.versions.room.get()}")
    
    
    testImplementation("org.xerial:sqlite-jdbc:3.45.3.0")
    
    implementation("androidx.media3:media3-session:1.10.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("com.google.guava:guava:32.1.2-android")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
}