plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core"))
    implementation(libs.jakarta.mail.api)
    implementation(libs.angus.mail)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
