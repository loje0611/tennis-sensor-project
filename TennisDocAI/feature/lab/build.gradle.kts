plugins {
    id("tennisdoc.android.library.compose")
}

android {
    namespace = "io.github.loje0611.tennisdoc.feature.lab"
}

dependencies {
    implementation(project(":core:vision"))
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.accompanist.permissions)
    
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
