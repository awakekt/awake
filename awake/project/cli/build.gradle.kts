/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// `awake`, the command line for an Awake project's files: validate it, and read and edit its scenes,
// with no Studio and no GPU. Run it with `./gradlew :awake:project:cli:run --args="validate path/to/project"`,
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

dependencies {
    // Core's scene components, registered as a played project registers them, so a scene's components
    // decode as their own types and validate themselves.
    implementation(project(":awake:project:runtime"))
    implementation(project(":awake:project"))
    implementation(project(":awake:scene:document"))
    implementation(project(":awake:scene:binding"))
    implementation(project(":awake:scene:scene-core"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
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
