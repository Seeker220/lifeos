import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(k: String, default: String = "") = (localProps.getProperty(k) ?: default).trim()

android {
    namespace = "com.lifeos.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.lifeos.app"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "AZURE_LLM_ENDPOINT", "\"${prop("AZURE_LLM_ENDPOINT")}\"")
        buildConfigField("String", "AZURE_LLM_DEPLOYMENT", "\"${prop("AZURE_LLM_DEPLOYMENT")}\"")
        buildConfigField("String", "AZURE_LLM_API_KEY", "\"${prop("AZURE_LLM_API_KEY")}\"")
        buildConfigField("String", "AZURE_LLM_API_VERSION", "\"${prop("AZURE_LLM_API_VERSION", "2024-10-21")}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":agent"))
    implementation(project(":email"))
    implementation(project(":data"))
    implementation(project(":enforce"))
    implementation(project(":ui"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
