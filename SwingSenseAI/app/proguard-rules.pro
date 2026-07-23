# ── JNI native methods ────────────────────────────────────────
-keep class com.example.swingsenseai.inference.EdgeImpulseNative { *; }
-keepclassmembers class * {
    native <methods>;
}

# ── Room entities & DAOs ──────────────────────────────────────
-keep class com.example.swingsenseai.data.db.entity.** { *; }
-keep class com.example.swingsenseai.data.db.dao.** { *; }
-keep class com.example.swingsenseai.data.db.SwingSenseDatabase { *; }

# ── Kotlin metadata for reflection (Room) ─────────────────────
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable

# ── Coroutines ────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
