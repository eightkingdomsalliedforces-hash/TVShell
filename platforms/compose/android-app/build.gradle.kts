plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.tvshell.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.tvshell.android"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
        }
        create("launcher") {
            dimension = "distribution"
            applicationIdSuffix = ".launcher"
            versionNameSuffix = "-launcher"
        }
    }
}

dependencies {
    implementation(project(":shared-ui"))
    implementation("androidx.activity:activity-compose:1.12.2")
}
