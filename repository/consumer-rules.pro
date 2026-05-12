# Keep JAudioTagger intact (Uses reflection for audio format parsing)
-keep class org.jaudiotagger.** { *; }

# Also keep anything that implements them, just to be safe
-keepclassmembers class org.jaudiotagger.** { *; }

# TagLibMetadata is instantiated directly from JNI (native C++ code) by calling
# its constructor via reflection-like JNI lookup. R8 has no way to detect this
# usage during shrinking, so without this rule it renames or removes the class
# and its constructor, causing a NoSuchMethodError at runtime in release builds.
-keep class app.simple.felicity.repository.metadata.TagLibMetadata { *; }