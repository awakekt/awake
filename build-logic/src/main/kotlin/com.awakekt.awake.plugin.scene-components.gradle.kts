/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.google.devtools.ksp.gradle.KspExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Generates a scene module's @SceneBinding bindings and its SceneComponentModule registry with the
// :awake:scene:binding:processor KSP processor. Apply it to a Kotlin Multiplatform scene module and
// name the registry object:
//     awakeSceneComponents { registry.set("com.awakekt.awake.scene.core.SceneCoreSceneComponents") }
//
// KSP runs once, over commonMain, so every target compiles the same generated sources; that is the
// only way common code (the module's own, and DefaultSceneComponentResolvers downstream) can name
// them. Each task that reads commonMain's sources waits for it.

plugins {
    id("com.google.devtools.ksp")
}

interface AwakeSceneComponentsExtension {
    /** The qualified name of the module's generated SceneComponentModule object. */
    val registry: Property<String>
}

val awakeSceneComponents = extensions.create<AwakeSceneComponentsExtension>("awakeSceneComponents")
val kspCommonTask = "kspCommonMainKotlinMetadata"

dependencies {
    add("kspCommonMainMetadata", project(":awake:scene:binding:processor"))
}

// The processor fails the build, naming the option, when a module generates bindings without it.
extensions.configure<KspExtension> {
    arg("awake.sceneComponents.registry", awakeSceneComponents.registry)
}

extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.named("commonMain") {
        kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin"))
    }
}

// Compilations, each target's own KSP run, source jars, Dokka and Android lint all read commonMain's sources.
tasks.configureEach {
    val readsCommonSources = name.startsWith("compile") || (name.startsWith("ksp") && name != kspCommonTask) ||
        name.endsWith("SourcesJar") || name == "sourcesJar" || name.startsWith("dokka") || name.startsWith("lint")
    if (readsCommonSources) dependsOn(kspCommonTask)
}
