plugins {
    id("tennisdoc.android.library.compose")
}

android {
    namespace = "io.github.loje0611.tennisdoc.feature.lab"
}

dependencies {
    implementation(project(":core:vision"))
    implementation(libs.mediapipe.tasks.vision)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
