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

# TagLibMetadata is instantiated directly from JNI (native C++ code) by calling
# its constructor via reflection-like JNI lookup. R8 has no way to detect this
# usage during shrinking, so without this rule it renames or removes the class
# and its constructor, causing a NoSuchMethodError at runtime in release builds.
-keep class app.simple.felicity.repository.metadata.TagLibMetadata { *; }

