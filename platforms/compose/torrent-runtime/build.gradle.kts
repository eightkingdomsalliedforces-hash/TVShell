plugins {
    kotlin("jvm")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val jlibtorrentVersion = "2.0.12.9"
val hostNativeArtifact = when {
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "jlibtorrent-windows"
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) && System.getProperty("os.arch") == "aarch64" -> "jlibtorrent-macosx-arm64"
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "jlibtorrent-macosx-x86_64"
    System.getProperty("os.arch") == "aarch64" -> "jlibtorrent-linux-arm64"
    else -> "jlibtorrent-linux-x86_64"
}

dependencies {
    implementation("com.frostwire:jlibtorrent:$jlibtorrentVersion")
    testImplementation(kotlin("test"))
    testRuntimeOnly("com.frostwire:$hostNativeArtifact:$jlibtorrentVersion")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
    useJUnitPlatform()
}
