import org.gradle.api.artifacts.ProjectDependency
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.hilt.android.plugin) apply false
}
tasks.register("verifyModuleDependencies") {
    val allowedDeps = mapOf(
        ":core:ui" to emptySet<String>(),
        ":core:sensor" to emptySet(),
        ":core:data" to emptySet(),
        ":core:vision" to emptySet(),
        ":core:analysis" to setOf(":core:sensor"),
        ":feature:match" to setOf(":core:ui", ":core:sensor", ":core:data", ":core:analysis"),
        ":feature:history" to setOf(":core:ui", ":core:data"),
        ":feature:lab" to setOf(":core:ui", ":core:vision", ":core:data", ":core:analysis"),
        ":app" to setOf(":core:ui", ":core:sensor", ":core:data", ":core:analysis", ":core:vision", ":feature:match", ":feature:history", ":feature:lab")
    )

    doLast {
        val violations = mutableListOf<String>()
        subprojects.forEach { proj ->
            val projPath = proj.path
            proj.configurations.forEach { conf ->
                conf.dependencies.withType(ProjectDependency::class.java).forEach { dep ->
                    val depPath = dep.path
                    val allowedForProj = allowedDeps[projPath] ?: emptySet()
                    if (depPath != projPath && depPath !in allowedForProj) {
                        violations.add("Module $projPath has forbidden dependency on $depPath")
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Dependency rule violations found:\n" + violations.joinToString("\n"))
        }
    }
}
