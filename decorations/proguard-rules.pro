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

-dontwarn app.simple.felicity.shared.utils.ColorUtils
-dontwarn app.simple.felicity.shared.utils.ViewUtils
-dontwarn app.simple.felicity.preferences.AccessibilityPreferences
-dontwarn app.simple.felicity.preferences.AppearancePreferences
-dontwarn app.simple.felicity.preferences.BehaviourPreferences
-dontwarn app.simple.felicity.manager.SharedPreferences
-dontwarn app.simple.felicity.shared.utils.ConditionUtils
-dontwarn app.simple.felicity.theme.interfaces.ThemeChangedListener
-dontwarn app.simple.felicity.theme.managers.ThemeManager
-dontwarn app.simple.felicity.theme.models.Accent
-dontwarn app.simple.felicity.theme.models.IconTheme
-dontwarn app.simple.felicity.theme.models.TextViewTheme
-dontwarn app.simple.felicity.theme.models.ViewGroupTheme
-dontwarn app.simple.felicity.theme.themes.Theme
-dontwarn java.lang.invoke.StringConcatFactory

-keeppackagenames
-dontobfuscate