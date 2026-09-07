/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.extension.*
import com.awakekt.awake.build.tasks.*
import de.undercouch.gradle.tasks.download.Download
import java.io.File

plugins {
    id("de.undercouch.download")
}

val glslangDownload =
    tasks.register<Download>("glslangDownload") {
        val hostFile = when {
            HostOs.isMac -> "main-osx"
            HostOs.isWindows -> "master-windows"
            HostOs.isLinux -> "main-linux"
            else -> throw GradleException("No glslang build for host ${HostOs.slug}.")
        }
        src("https://github.com/KhronosGroup/glslang/releases/download/main-tot/glslang-$hostFile-Release.zip")
        dest(layout.buildDirectory.file("glslang.zip"))
    }

val glslangDownloadCopy = tasks.register<Copy>("glslangDownloadCopy") {
    dependsOn(glslangDownload)
    from(zipTree(layout.buildDirectory.file("glslang.zip")))
    into(layout.buildDirectory.dir("glslang"))
}

tasks.register("glslValidator") {
    dependsOn(glslangDownloadCopy)

    val bin = layout.buildDirectory.dir("glslang/bin").get().asFile.path
    val shadersDir = file("src/commonMain/resources/assets/shader/vulkan")
    val shaders = project.fileTree(shadersDir) {
        include("**/*.frag", "**/*.vert")
    }

    doLast {
        shaders.forEach { shaderFile ->
            val spvFile = File(shadersDir, "${shaderFile.name}.spv")
            val process = ProcessBuilder(
                "$bin/glslangValidator", "-V", shaderFile.absolutePath, "-o", spvFile.absolutePath
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("glslangValidator failed for ${shaderFile.name} (exit $exitCode):\n$output")
            }
        }
    }
}
