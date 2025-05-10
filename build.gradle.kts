import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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

compose.desktop {
    application {
        mainClass = "top.ntutn.floatclock.MainKt"
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
