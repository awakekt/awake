/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.awakekt.awake.plugin.browser-tests")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    jvmToolchain(17)

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "awake-player.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:player"))
        }
        named("desktopMain") {
            dependencies {
                implementation(project(":awake:backend:vulkan"))
                implementation(project(":awake:engine:window"))
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        named("wasmJsMain") {
            dependencies {
                implementation(project(":awake:backend:webgpu"))
                implementation(project(":awake:core:audio"))
                implementation(libs.kotlinx.browser)
            }
            resources.srcDir(project(":awake:backend:webgpu").file("src/wasmJsMain/resources"))
            // basis/basis_transcoder.{js,wasm} beside index.html for cooked KTX2/Basis textures.
            resources.srcDir(project(":awake:asset:texture-ktx-loader").file("src/wasmJsMain/resources"))
        }
    }
}

tasks.named("wasmJsProcessResources") {
    dependsOn(":awake:asset:texture-ktx-loader:verifyBasisTranscoder")
}

// Several Core modules share jar filenames (e.g. runtime-desktop.jar). Prefix each jar by its
// repository path or Maven group so scripts/package-macos-player.sh can stage all jars in one lib/.
fun stagedJarName(jar: File, componentId: org.gradle.api.artifacts.component.ComponentIdentifier?): String {
    val module = jar.parentFile?.takeIf { it.name == "libs" }?.parentFile?.takeIf { it.name == "build" }?.parentFile
    if (module != null && module.startsWith(rootDir)) {
        return module.relativeTo(rootDir).invariantSeparatorsPath.replace('/', '-') + "-" + jar.name
    }
    return if (componentId is ModuleComponentIdentifier) "${componentId.group}-${jar.name}" else jar.name
}

val r8Classpath by configurations.creating

dependencies {
    // R8 9.5.26 (same shrinker/obfuscator as Android AGP 9.2.1) natively supports Kotlin 2.5
    // metadata and emits an obfuscated JVM classfile JAR via `--classfile`.
    r8Classpath("com.android.tools:r8:9.5.26")
}

// Libraries ship their keep rules in META-INF/proguard; collect them from both project and external
// runtime JARs so the prebuilt macOS player runs through R8 shrinking and obfuscation just like
// `:awake:player:player-android`.
val libraryKeepRules = tasks.register<Sync>("libraryKeepRules") {
    val jars = configurations.named("desktopRuntimeClasspath")
    dependsOn(jars)
    from(jars.map { cfg -> cfg.files.filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        include("META-INF/proguard/*.pro")
    }
    into(layout.buildDirectory.dir("library-keep-rules"))
    eachFile { path = name }
    includeEmptyDirs = false
}

val desktopPackageInput = tasks.register<Sync>("desktopPackageInput") {
    val desktopJar = tasks.named("desktopJar")
    dependsOn(desktopJar)
    val runtimeArtifacts = configurations.named("desktopRuntimeClasspath")
        .flatMap { it.incoming.artifacts.resolvedArtifacts }
    from(desktopJar)
    from(runtimeArtifacts.map { artifacts -> artifacts.map { it.file } }) {
        eachFile {
            val sourceFile = file
            val id = runtimeArtifacts.get().firstOrNull { it.file == sourceFile }?.id?.componentIdentifier
            name = stagedJarName(sourceFile, id)
        }
    }
    into(layout.buildDirectory.dir("desktop-package/lib"))
}

tasks.register<JavaExec>("desktopObfuscate") {
    dependsOn(desktopPackageInput, libraryKeepRules)
    classpath = r8Classpath
    mainClass.set("com.android.tools.r8.R8")

    val inputDir = layout.buildDirectory.dir("desktop-package/lib")
    val rulesDir = layout.buildDirectory.dir("library-keep-rules")
    val outDir = layout.buildDirectory.dir("desktop-package-obfuscated/lib")
    val outJar = layout.buildDirectory.file("desktop-package-obfuscated/lib/awake-player.jar")
    val appRules = project.file("proguard-rules.pro")
    val configFile = layout.buildDirectory.file("tmp/desktopObfuscate/r8.conf")

    inputs.dir(inputDir)
    inputs.dir(rulesDir)
    inputs.file(appRules)
    outputs.file(outJar)

    doFirst {
        val outputDirectory = outDir.get().asFile
        outputDirectory.deleteRecursively()
        outputDirectory.mkdirs()

        val javaHome = File(System.getProperty("java.home"))
        val allJars = inputDir.get().asFile.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "jar" }
            .sortedBy { it.name }
        // Obfuscate all Awake Core & player modules into `awake-player.jar`, while passing
        // third-party runtime libraries (and `awake-core-text-*`, which embeds 400 KB Apache-2.0
        // Roboto font data classes that exceed JVM's 64 KB UTF-8 constant-pool limit in R8
        // `--classfile` output) as `--lib` and staging them alongside it.
        val (programJars, libraryJars) = allJars.partition { jar ->
            !jar.name.startsWith("awake-core-text-") && (
                jar.name.startsWith("awake-") ||
                    jar.name.startsWith("player-shell-") ||
                    jar.name.startsWith("com.awakekt.")
                )
        }
        libraryJars.forEach { jar -> jar.copyTo(File(outputDirectory, jar.name), overwrite = true) }

        val libRuleFiles = rulesDir.get().asFile.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "pro" }
            .sortedBy { it.name }

        val conf = configFile.get().asFile
        conf.parentFile.mkdirs()
        conf.writeText(
            buildString {
                appendLine("-include '${appRules.absolutePath}'")
                libRuleFiles.forEach { rule ->
                    appendLine("-include '${rule.absolutePath}'")
                }
                appendLine("-dontnote **")
            },
        )

        val r8Args = mutableListOf(
            "--release",
            "--classfile",
            "--output",
            outJar.get().asFile.absolutePath,
            "--pg-conf",
            conf.absolutePath,
            "--lib",
            javaHome.absolutePath,
        )
        libraryJars.forEach { jar ->
            r8Args += "--lib"
            r8Args += jar.absolutePath
        }
        programJars.forEach { jar -> r8Args += jar.absolutePath }
        args(r8Args)
    }
}
