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
        ":core:model" to emptySet<String>(),
        ":core:ui" to setOf(":core:model"),
        ":core:sensor" to emptySet(),
        ":core:data" to setOf(":core:model"),
        ":core:vision" to emptySet(),
        ":core:analysis" to setOf(":core:model", ":core:sensor"),
        ":core:fusion" to setOf(":core:model", ":core:vision", ":core:analysis"),
        ":core:coach" to setOf(":core:model", ":core:fusion"),
        ":feature:match" to setOf(":core:model", ":core:ui", ":core:sensor", ":core:data", ":core:analysis"),
        ":feature:history" to setOf(":core:model", ":core:ui", ":core:data", ":core:fusion"),
        ":feature:lab" to setOf(":core:model", ":core:ui", ":core:vision", ":core:data", ":core:analysis", ":core:fusion"),
        ":app" to setOf(":core:model", ":core:ui", ":core:sensor", ":core:data", ":core:analysis", ":core:vision", ":core:fusion", ":core:coach", ":feature:match", ":feature:history", ":feature:lab")
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

tasks.register("verifyJniBindings") {
    dependsOn(":app:mergeDebugNativeLibs")

    doLast {
        val requiredAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

        val ktFile = fileTree(rootDir).matching { include("**/EdgeImpulseNative.kt") }.singleOrNull()
            ?: throw GradleException("verifyJniBindings failed: EdgeImpulseNative.kt not found")

        val packageName = ktFile.useLines { lines ->
            lines.firstOrNull { it.trim().startsWith("package ") }
                ?.substringAfter("package ")
                ?.trim()
                ?.removeSuffix(";")
        } ?: throw GradleException("verifyJniBindings failed: package declaration not found in EdgeImpulseNative.kt")

        val expectedClassDescriptor = "${packageName.replace('.', '/')}/EdgeImpulseNative"

        val foundSoFiles = mutableMapOf<String, File>()

        val searchDirs = listOf(
            file("app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib"),
            file("app/build/intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out/lib")
        )

        for (dir in searchDirs) {
            if (dir.exists()) {
                requiredAbis.forEach { abi ->
                    if (!foundSoFiles.containsKey(abi)) {
                        val soFile = File(dir, "$abi/libswingsense_ei.so")
                        if (soFile.exists()) {
                            foundSoFiles[abi] = soFile
                        }
                    }
                }
            }
        }

        val missingAbis = requiredAbis - foundSoFiles.keys
        if (missingAbis.isNotEmpty()) {
            throw GradleException("verifyJniBindings failed: libswingsense_ei.so missing for ABIs: $missingAbis")
        }

        val errors = mutableListOf<String>()

        foundSoFiles.forEach { (abi, file) ->
            val content = file.readBytes().toString(Charsets.ISO_8859_1)

            if (!content.contains("JNI_OnLoad")) {
                errors.add("ABI $abi ($file): missing JNI_OnLoad symbol")
            }

            if (content.contains("Java_com_example_swingsenseai")) {
                errors.add("ABI $abi ($file): contains legacy symbol 'Java_com_example_swingsenseai'")
            }

            if (!content.contains(expectedClassDescriptor)) {
                errors.add("ABI $abi ($file): missing expected class descriptor '$expectedClassDescriptor'")
            }
        }

        if (errors.isNotEmpty()) {
            throw GradleException("verifyJniBindings failed with errors:\n" + errors.joinToString("\n"))
        }

        println("verifyJniBindings PASSED: Verified 4 ABIs (${requiredAbis.joinToString()}) with class descriptor '$expectedClassDescriptor'")
    }
}
