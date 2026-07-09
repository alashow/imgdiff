import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.internal.os.OperatingSystem

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.gson)
    implementation(libs.compose.multiplatform.webview)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

sourceSets.main {
    resources.srcDir(project.projectDir.resolve("frontend/dist"))
    resources.srcDir(project.projectDir.resolve("frontend/src/assets"))
}

val staticIconsDir = project.projectDir.resolve("src/main/resources/icons")

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Image Diff"
            packageVersion = "1.0.0"
            description = "Image Diff"
            macOS {
                bundleID = "tm.alashow.imgdiff"
                dockName = "Image Diff"
                val macIcon = staticIconsDir.resolve("ImgDiff.icns")
                if (macIcon.exists()) {
                    iconFile.set(macIcon)
                }
            }
            windows {
                menuGroup = "Image Diff"
                val windowsIcon = staticIconsDir.resolve("ImgDiff.ico")
                if (windowsIcon.exists()) {
                    iconFile.set(windowsIcon)
                }
            }
            linux {
                packageName = "imgdiff"
                val linuxIcon = staticIconsDir.resolve("ImgDiff.png")
                if (linuxIcon.exists()) {
                    iconFile.set(linuxIcon)
                }
            }
        }
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
    }
}

// Resolve npm executable — Gradle doesn't inherit shell PATH on macOS
fun resolveNpm(): String {
    if (OperatingSystem.current().isWindows) return "npm.cmd"
    val candidates = listOf(
        "/opt/homebrew/bin/npm",   // Apple Silicon Homebrew
        "/usr/local/bin/npm",      // Intel Homebrew / system
        "/usr/bin/npm",
    )
    return candidates.firstOrNull { File(it).exists() } ?: "npm"
}

// Task to run 'npm install'
val npmInstall by tasks.registering(Exec::class) {
    val frontendDir = project.projectDir.resolve("frontend")
    workingDir(frontendDir)
    commandLine(resolveNpm(), "install")
}

// Task to run 'npm run build' (depends on install)
val buildFrontend by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    val frontendDir = project.projectDir.resolve("frontend")
    workingDir(frontendDir)
    commandLine(resolveNpm(), "run", "build")
}

// Force resources process to depend on the fresh build
tasks.named("processResources").configure {
    dependsOn(buildFrontend)
}
