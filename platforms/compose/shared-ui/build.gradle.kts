import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidLibrary {
        namespace = "dev.tvshell.shared"
        compileSdk = 36
        minSdk = 23
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
            implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
            implementation("org.jetbrains.compose.material:material:1.11.1")
            implementation("org.jetbrains.compose.ui:ui:1.11.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jsoup:jsoup:1.21.2")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("org.jsoup:jsoup:1.21.2")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.tvshell.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "TVShell"
            packageVersion = "1.0.0"
            description = "TVShell for Windows"
            vendor = "TVShell"
            windows {
                iconFile.set(project.file("../../../assets/icons/TVShell.ico"))
                menuGroup = "TVShell"
                upgradeUuid = "45aef48e-4a19-52e5-98e8-8376a35d5bd9"
            }
        }
    }
}
