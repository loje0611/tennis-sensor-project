plugins {
    id("tennisdoc.android.library.compose")
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.hilt.android.plugin)
}

android {
    namespace = "io.github.loje0611.tennisdoc.feature.lab"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:vision"))
    implementation(project(":core:fusion"))
    implementation(project(":core:coach"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
