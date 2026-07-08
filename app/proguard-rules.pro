# Add project specific ProGuard rules here.
# Media3, Hilt, Coil and Ktor ship their own consumer ProGuard rules — do not
# add blanket -keep rules for them here; that disables shrinking/inlining for
# the app's largest dependencies.

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Ktor
-dontwarn io.ktor.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# jaudiotagger — desktop-Java library; its artwork classes reference AWT/ImageIO
# which don't exist on Android. The tag editor never touches those paths.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-keep class org.jaudiotagger.** { *; }

# Readable release crash traces: keep line numbers, hide original file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip debug and verbose logs in release builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
