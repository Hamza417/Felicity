# ----------------------------------------------------------------------------
# Core Module Dependencies
# ----------------------------------------------------------------------------

# Keep LZ4/XXHash intact (It uses reflection to switch between JNI, Unsafe, and JavaSafe)
-keep class net.jpountz.** { *; }

# Also keep anything that implements them, just to be safe
-keepclassmembers class net.jpountz.** { *; }