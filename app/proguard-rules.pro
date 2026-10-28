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

# Compose and Kotlin serialization rely on generated/metadata-driven code.
-keepattributes KotlinMetadata,InnerClasses,EnclosingMethod,Signature
-keep class kotlinx.serialization.** { *; }
-keep class **$$serializer { *; }

# Preserve Android Log calls in release builds for remote troubleshooting.
-keep class android.util.Log { *; }

# JLaTeXMath relies on reflection for environment/macro dispatch (matrix, bmatrix, etc.).
-keep class org.scilab.forge.jlatexmath.** { *; }
-keep class ru.noties.jlatexmath.** { *; }

# Shizuku user service is instantiated out-of-process and must keep stable names.
-keep class fun.kirari.hanako.platform.capture.shizuku.ShizukuShellService {
    public <init>();
    public *;
}
-keep class fun.kirari.hanako.platform.capture.shizuku.IShizukuShellService { *; }
-keep class fun.kirari.hanako.platform.capture.shizuku.IShizukuShellService$Stub { *; }
-keep class fun.kirari.hanako.platform.capture.shizuku.IShizukuShellService$Stub$Proxy { *; }

# LSPosed HiddenApiBypass - relies on Unsafe and reflection, must not be obfuscated
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**
