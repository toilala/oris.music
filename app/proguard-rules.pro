# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Room entities
-keep class com.orismusic.client.db.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class com.orismusic.client.Hilt_*.class { *; }

# Keep Media3 classes
-keep class androidx.media3.** { *; }

# Keep Coil classes
-keep class coil3.** { *; }

# Keep Kotlinx serialization
-keep class kotlinx.serialization.** { *; }

# Keep Ktor classes
-keep class io.ktor.** { *; }

# Keep NewPipeExtractor
-keep class com.github.teamnewpipe.** { *; }

# Keep Material3
-keep class androidx.compose.material3.** { *; }

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep Protobuf
-keep class com.google.protobuf.** { *; }

# Keep Timber
-keep class timber.log.** { *; }
# Release build: desktop-only classes that Android never has
-dontwarn java.lang.management.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }
-keep class org.schabi.newpipe.** { *; }
-keep class com.orismusic.client.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class * { @kotlinx.serialization.Serializable *; }
