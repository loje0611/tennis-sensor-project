plugins {
    id("tennisdoc.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:vision"))
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
