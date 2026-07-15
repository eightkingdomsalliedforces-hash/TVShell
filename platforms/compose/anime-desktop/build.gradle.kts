import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val tvShellBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.coerceIn(1, 65_535) ?: 1
val tvShellPackageVersion = System.getenv("GITHUB_REF_NAME")
    ?.removePrefix("v")
    ?.takeIf { it.matches(Regex("\\d+\\.\\d+\\.\\d+")) }
    ?: if (System.getenv("GITHUB_RUN_NUMBER") != null) "1.0.$tvShellBuildNumber" else "1.0.0"

dependencies {
    implementation(project(":shared-ui"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "dev.tvshell.anime.desktop.MainKt"
        nativeDistributions {
            appResourcesRootDir.set(rootProject.layout.projectDirectory.dir("package-resources"))
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "TVShell Anime"
            packageVersion = tvShellPackageVersion
            description = "TVShell Anime for Windows"
            vendor = "TVShell"
            windows {
                iconFile.set(project.file("../../../assets/icons/TVShell-Anime.ico"))
                menuGroup = "TVShell"
                upgradeUuid = "0475a205-1fbd-51f3-979b-b3be88f4491a"
            }
        }
    }
}
