# SoundGroove — R8 / ProGuard (release minify + full mode AGP 8+)
# Media3, Room et la plupart des libs AndroidX livrent déjà des consumer rules ;
# ici : keeps ciblés + attributs utiles aux crash reports.

-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-renamesourcefileattribute SourceFile

# ── Room ────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# ── Media3 / session (consumer rules + filets) ──────────────────────────────
-dontwarn androidx.media3.**
-dontwarn com.google.android.exoplayer2.**

# ── Rive (JNI / reflection) ─────────────────────────────────────────────────
-keep class app.rive.** { *; }
-keep class com.facebook.jni.** { *; }
-keep class com.facebook.soloader.** { *; }

# ── Java-WebSocket (remote LAN host) ────────────────────────────────────────
-keep class org.java_websocket.** { *; }

# ── Coil ────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── Kotlin / coroutines ─────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Parcelable / enums app ──────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
