# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep MongoDB driver classes to prevent NoClassDefFoundError
-keep class org.bson.** { *; }
-keep class com.mongodb.** { *; }
-dontwarn org.bson.**
-dontwarn com.mongodb.**
-dontwarn com.sun.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,Exceptions

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Database related classes
-keep class io.celox.querycore.database.** { *; }
-keep class io.celox.querycore.models.** { *; }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}