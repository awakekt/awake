/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.extension.HostOs
import com.awakekt.awake.build.extension.VulkanDesktopEnv
import com.awakekt.awake.build.extension.requireExclusiveGpu
import com.awakekt.awake.build.extension.useNagaShaderCompiler

// `awake`, the command line for an Awake project's files: validate it, read and edit its scenes, and
// render one headless. Run it with `./gradlew :awake:project:cli:run --args="validate path/to/project"`,
// or `installDist` for an `awake` script in build/install/awake/bin.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

application {
    mainClass.set("com.awakekt.awake.project.cli.MainKt")
    applicationName = "awake"
}

// 25, not the repo-wide 17: `awake render` links the WebGPU backend, whose wgpu4k-jvm dependency ships
// bytecode built for JVM 25.
kotlin {
    jvmToolchain(25)
}

// detekt's compiler frontend tops out at JVM target 22; the target only affects its own analysis.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { jvmTarget = "22" }
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach { jvmTarget = "22" }

dependencies {
    // Core's scene components, registered as a played project registers them, so a scene's components
    // decode as their own types and validate themselves.
    implementation(project(":awake:project:runtime"))
    implementation(project(":awake:project"))
    implementation(project(":awake:scene:document"))
    implementation(project(":awake:scene:binding"))
    implementation(project(":awake:scene:scene-core"))
    implementation(libs.kotlinx.serialization.json)
    // `awake render`: a played project on either backend, headless, with physics when the scene has bodies.
    implementation(project(":awake:backend:vulkan"))
    implementation(project(":awake:backend:webgpu"))
    implementation(project(":awake:backend:jolt"))
    implementation(project(":awake:engine:platform"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

// The GPU-free commands' tests run anywhere; `renderTest` needs a GPU (or Mesa's lavapipe) and, for
// WebGPU, a display, so it runs where the render parity suite does.
tasks.test {
    filter { excludeTestsMatching("*RenderTest") }
}

val desktopNativeLibDir = project(":awake:backend:vulkan:bindings").layout.buildDirectory.dir("desktop-native-libs")

tasks.register<Test>("renderTest") {
    description = "Render a project headless with awake render, on Vulkan and WebGPU."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*RenderTest") }
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    if (HostOs.isMac) {
        // GLFW, which WebGPU's headless bootstrap uses to get an adapter, starts only on the first thread.
        jvmArgs("-XstartOnFirstThread", "--add-opens", "java.base/java.lang=ALL-UNNAMED")
    }
    requireExclusiveGpu(this)
    // -Pawake.prebuiltNatives where the Vulkan bindings can't be built here, as on Windows.
    if (!providers.gradleProperty("awake.prebuiltNatives").isPresent) {
        dependsOn(":awake:backend:vulkan:bindings:buildDesktopNative")
    }
    useNagaShaderCompiler(this)
    jvmArgs("-Djava.library.path=${desktopNativeLibDir.get().asFile.absolutePath}")
    environment(VulkanDesktopEnv.environment())
    // -Pawake.render.backends=webgpu runs one backend, as on a machine without the other.
    providers.gradleProperty("awake.render.backends").orNull?.let { systemProperty("awake.render.backends", it) }
}

// `run` from the repository root, so a relative project path is from there rather than this module.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

// Several Core modules build jars of one name (scene:runtime and project:runtime both build
// runtime-desktop.jar), which the distribution's lib folder can't hold side by side. Each module's jar
// is named after the module's path there, and the start scripts list the same names.
fun distributedName(jar: File): String {
    val module = jar.parentFile?.takeIf { it.name == "libs" }?.parentFile?.takeIf { it.name == "build" }?.parentFile
    return if (module == null || !module.startsWith(rootDir)) jar.name else module.relativeTo(rootDir).invariantSeparatorsPath.replace('/', '-') + "-" + jar.name
}

val distributionLibsDir = layout.buildDirectory.dir("distribution-libs")
val distributionLibs = tasks.register<Sync>("distributionLibs") {
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(distributionLibsDir)
    eachFile { name = distributedName(file) }
}

tasks.named<CreateStartScripts>("startScripts") {
    // The runtime classpath's own order, under the names lib holds.
    val ordered = provider {
        (tasks.jar.get().outputs.files + configurations.runtimeClasspath.get()).map { distributionLibsDir.get().file(distributedName(it)).asFile }
    }
    classpath = files(ordered).builtBy(distributionLibs)
}

distributions {
    main {
        contents {
            // An archive puts lib under a top folder, so the file's own folder is what names it.
            eachFile { if (relativePath.parent?.lastName == "lib") name = distributedName(file) }
        }
    }
}
