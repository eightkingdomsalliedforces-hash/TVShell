plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val tvShellBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.coerceIn(1, 65_535) ?: 1
val tvShellVersionName = System.getenv("GITHUB_REF_NAME")
    ?.removePrefix("v")
    ?.takeIf { it.matches(Regex("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?")) }
    ?: if (System.getenv("GITHUB_RUN_NUMBER") != null) "1.0.$tvShellBuildNumber" else "1.0.0"

android {
    namespace = "dev.tvshell.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.tvshell.android"
        minSdk = 26
        targetSdk = 36
        versionCode = tvShellBuildNumber
        versionName = tvShellVersionName
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

    sourceSets["main"].assets.directories.add(
        rootProject.layout.projectDirectory.dir("package-resources/common").asFile.absolutePath,
    )
}

dependencies {
    implementation(project(":shared-ui"))
    implementation("androidx.activity:activity-compose:1.12.2")
}
