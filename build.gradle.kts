import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.Exec

val generatedMacOSResources = layout.buildDirectory.dir("generated/macosResources")
val macOSBridgeSource = layout.projectDirectory.file("src/desktopMain/native/macos/FloatClockMacOS.m")
val macOSBridgeLibrary = generatedMacOSResources.map {
    it.file("native/macos/libfloatclock_macos.dylib")
}

val compileMacOSBridge by tasks.registering(Exec::class) {
    group = "build"
    description = "Compiles the AppKit bridge used to configure the FloatClock NSWindow."
    onlyIf { System.getProperty("os.name").startsWith("Mac", ignoreCase = true) }

    inputs.file(macOSBridgeSource)
    outputs.file(macOSBridgeLibrary)

    doFirst {
        val outputFile = macOSBridgeLibrary.get().asFile
        outputFile.parentFile.mkdirs()
        val javaHome = System.getProperty("java.home")
        commandLine(
            "xcrun", "clang",
            "-dynamiclib",
            "-fobjc-arc",
            "-fblocks",
            "-framework", "AppKit",
            "-I$javaHome/include",
            "-I$javaHome/include/darwin",
            macOSBridgeSource.asFile.absolutePath,
            "-o", outputFile.absolutePath,
        )
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.kotlinx.serialization)
}

group = "top.ntutn"
version = "2.0.0"

buildConfig {
    buildConfigField("String", "APP_NAME", "\"${project.name}\"")
    buildConfigField("String", "APP_VERSION", "\"${version}\"")
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm("desktop")
    sourceSets {
        val desktopMain by getting
        desktopMain.resources.srcDir(generatedMacOSResources)
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.components.resources)
            api(libs.androidx.datastore.preferences.core)
            api(libs.androidx.datastore.core.okio)
            implementation(libs.kotlinx.serialization)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.apache.commons:commons-lang3:3.12.0")
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

tasks.named("desktopProcessResources") {
    dependsOn(compileMacOSBridge)
}

compose.desktop {
    application {
        mainClass = "top.ntutn.floatclock.FloatClock"
        nativeDistributions {
            modules("java.compiler", "java.instrument", "jdk.unsupported")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "floatclock"
            packageVersion = version.toString()
            linux {
                iconFile.set(project.file("src/desktopMain/resources/clock.png"))
            }
            windows {
                shortcut = true
                iconFile.set(project.file("icon.ico"))
                menu = true
                menuGroup = "ntutn"
            }
        }
        args("--release")
        jvmArgs += listOf("--add-opens=java.desktop/javax.swing=ALL-UNNAMED")
    }
}
