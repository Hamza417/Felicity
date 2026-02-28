# Keep JAudioTagger intact (Uses reflection for audio format parsing)
-keep class org.jaudiotagger.** { *; }

# Also keep anything that implements them, just to be safe
-keepclassmembers class org.jaudiotagger.** { *; }