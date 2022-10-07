import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.nio.file.Files

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

group = "top.ntutn"
version = "2.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            kotlin {
                sourceSets["jvmMain"].kotlin.srcDir("build/src/generated/kotlin")
            }
            dependencies {
                implementation(compose.desktop.currentOs)
                // https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
                implementation("org.apache.commons:commons-lang3:3.12.0")
                // https://mvnrepository.com/artifact/com.arkivanov.decompose/decompose
//                implementation("com.arkivanov.decompose:decompose:1.0.0-alpha-06")
//                implementation("com.arkivanov.decompose:extensions-compose-jetbrains:1.0.0-alpha-06")
                implementation("org.mapdb:mapdb:3.0.8") {
                    exclude("org.jetbrains.kotlin", "kotlin-stdlib")
                }
            }
        }
        val jvmTest by getting
    }
}

compose.desktop {
    application {
        mainClass = "top.ntutn.floatclock.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "floatclock"
            packageVersion = version.toString()
            linux {
                iconFile.set(project.file("src/jvmMain/resources/clock.png"))
            }
            windows {
                shortcut = true
                iconFile.set(project.file("icon.ico"))
            }
        }
        args("--release")
    }
}

tasks.register("generateBuildConfig") {
    val content = """
        package top.ntutn.floatclock
        
        object BuildConfig {
            val version = "$version"
        }
    """.trimIndent()
    val dir = File(projectDir, "build/src/generated/kotlin/top/ntutn/floatclock").also {
        if (it.exists()) {
            it.deleteRecursively()
        }
        it.mkdirs()
    }
    File(dir, "BuildConfig.kt").writeText(content)
}

tasks.register("repackageDeb") {
    dependsOn(tasks.getByPath("packageDeb"))
    doLast {
        val repackageOutputPath = File(projectDir, "build/compose/binaries/main/repackageDeb")
        if (repackageOutputPath.exists()) {
            repackageOutputPath.deleteRecursively()
        }
        repackageOutputPath.mkdirs()
        println("repackage output is ${repackageOutputPath.absolutePath}")
        val extractPath = File(repackageOutputPath, "extract").also { it.mkdir() }
        val debianPath = File(extractPath, "DEBIAN").also { it.mkdir() }
        val buildPath = File(repackageOutputPath, "build").also { it.mkdir() }

        // 查找原有deb文件
        val originDebFile = File(projectDir, "build/compose/binaries/main/deb").listFiles { _: File, name: String ->
            name.endsWith(".deb")
        }?.firstOrNull()
        require(originDebFile != null) { "未找到原始打包结果！" }

        println("原始打包结果 ${originDebFile.absolutePath}")

        // 解包
        execProcessWait("dpkg", "-X", originDebFile.absolutePath, extractPath.absolutePath + "/")
        execProcessWait("dpkg", "-e", originDebFile.absolutePath, debianPath.absolutePath + "/")

        // 释放LICENSE文件
        File(debianPath, "copyright").writeText(File(projectDir, "LICENSE").readText())
        File(extractPath, "opt/floatclock/share/doc/copyright").writeText(File(projectDir, "LICENSE").readText())

        // 重建快捷方式
        val libDir = File(extractPath, "opt/floatclock/lib")
        File(projectDir, "repackageDeb/opt/floatclock/lib/floatclock.desktop").copyTo(File(libDir, "floatclock-floatclock.desktop"), true)

        // 覆盖控制脚本
        val installSize = Files.walk(File(extractPath, "opt").toPath()).mapToLong { p -> p.toFile().length() }.sum().let {
            (it / 1024f).toLong()
        }
        val newControl = File(projectDir, "repackageDeb/DEBIAN/control").readText()
            .replace("{version}", version.toString())
            .replace("{maintainer}", "zerofancy")
            .replace("{maintainer-email}", "ntutn.top@gmail.com")
            .replace("{description}", "A simple clock floating on other windows.")
            .replace("{installed-size}", installSize.toString())
            .replace("{homepage}", "https://github.com/zerofancy/floatclock")
        File(debianPath, "control").writeText(newControl)
        File(projectDir, "repackageDeb/DEBIAN/preinst").copyTo(File(debianPath, "preinst"), true)

        // 重置DEBIAN文件夹权限
        execProcessWait("chmod", "755", "-R", debianPath.absolutePath)

        // 重新打包 (ubuntu下默认使用zstd压缩，但这不能被debian/deepin现有版本支持)
        execProcessWait("dpkg-deb", "-b","-Zxz", extractPath.absolutePath, buildPath.absolutePath + "/")

        val outputFile = buildPath.listFiles { _: File, name: String-> name.endsWith(".deb") }?.firstOrNull()
        require(outputFile != null)

        val arch = "amd64"
        val finalOutputFile = File(buildPath, "floatclock_${version}-1_$arch.deb")

        outputFile.renameTo(finalOutputFile)

        println("重新打包输出文件 ${finalOutputFile.absolutePath}")
    }
}

fun execProcessWait(command: String, vararg args: String = emptyArray()) {
    val commandAndArgs = listOf(command) + args
    print("exec:" + commandAndArgs.joinToString(" "))
    val process = Runtime.getRuntime().exec(commandAndArgs.toTypedArray())
    val code = process.waitFor()
    process.inputStream.use { inputStream ->
        inputStream.readAllBytes().decodeToString().let {
            println(it)
        }
    }
    process.errorStream.use {
        val errorOutput = it.readAllBytes().decodeToString()
        println(errorOutput)
    }
    require(code == 0)
}

tasks {
    "compileJava" {
        dependsOn("generateBuildConfig")
    }
}