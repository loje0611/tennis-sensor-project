plugins {
    id("tennisdoc.android.library.compose")
}

android {
    namespace = "io.github.loje0611.tennisdoc.core.ui"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
