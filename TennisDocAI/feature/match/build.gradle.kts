plugins {
    id("tennisdoc.android.library.compose")
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.hilt.android.plugin)
}

android {
    namespace = "io.github.loje0611.tennisdoc.feature.match"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:sensor"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
