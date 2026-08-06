# ── JNI native methods ────────────────────────────────────────
-keep class io.github.loje0611.tennisdoc.inference.EdgeImpulseNative { *; }
-keepclassmembers class * {
    native <methods>;
}

# ── Room entities & DAOs ──────────────────────────────────────
-keep class io.github.loje0611.tennisdoc.data.db.entity.** { *; }
-keep class io.github.loje0611.tennisdoc.data.db.dao.** { *; }
-keep class io.github.loje0611.tennisdoc.data.db.TennisDocDatabase { *; }

# ── Kotlin metadata for reflection (Room) ─────────────────────
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable

# ── Coroutines ────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
