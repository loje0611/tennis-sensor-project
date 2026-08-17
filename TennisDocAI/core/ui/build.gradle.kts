plugins {
    id("tennisdoc.android.library.compose")
}

android {
    namespace = "io.github.loje0611.tennisdoc.core.ui"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.6.4")
    testImplementation("androidx.test.ext:junit:1.1.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.4")
    debugImplementation("androidx.activity:activity-compose:1.8.2")
}
