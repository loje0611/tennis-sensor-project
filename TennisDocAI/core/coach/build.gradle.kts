plugins {
    id("tennisdoc.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:fusion"))
    implementation("org.json:json:20240303")
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}
