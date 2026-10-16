/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

// Resolves a consumer graph holding one Core release and one Vulkan release built against another
// Core (#820, docs/release-process.md "Core alignment BOM") and fails unless every Core module lands
// on the Core release the consumer asked for. Run it through check.sh, or from the repo root:
//   ./gradlew -p tools/core-alignment-check checkCoreAlignment -Pcore=<core> -Pvulkan=<vulkan>
// Add -PmavenLocal to resolve from Maven Local before Central.
plugins {
    kotlin("jvm") version "2.4.10"
}

val core = providers.gradleProperty("core").get()
val vulkan = providers.gradleProperty("vulkan").get()

repositories {
    if (providers.gradleProperty("mavenLocal").isPresent) mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    implementation("com.awakekt.awake.core:math:$core")
    implementation("com.awakekt.awake.backend:vulkan:$vulkan")
}

tasks.register("checkCoreAlignment") {
    val root = configurations.runtimeClasspath.flatMap { it.incoming.resolutionResult.rootComponent }
    doLast {
        val awake = linkedMapOf<String, String>() // group:name -> resolved version
        val seen = mutableSetOf<ResolvedComponentResult>()
        fun walk(component: ResolvedComponentResult) {
            if (!seen.add(component)) return
            val id = component.moduleVersion ?: return
            if (id.group.startsWith("com.awakekt.awake")) awake["${id.group}:${id.name}"] = id.version
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { walk(it.selected) }
        }
        walk(root.get())

        // The Vulkan family is its own version train; every other Awake module is Core. Its bindings
        // publish as com.awakekt.awake:vulkan-kmp, outside the backend group, and are part of it too.
        val vulkanFamily = awake.filterKeys { module ->
            module.startsWith("com.awakekt.awake.backend:vulkan") ||
                module.startsWith("com.awakekt.awake.backend.vulkan:") ||
                module.startsWith("com.awakekt.awake:vulkan-kmp")
        }
        val coreModules = awake - vulkanFamily.keys
        coreModules.forEach { (module, version) -> println("$module:$version") }

        check(vulkanFamily.isNotEmpty() && vulkanFamily.values.all { it == vulkan }) {
            "Expected the Vulkan family at $vulkan, resolved $vulkanFamily"
        }
        check("com.awakekt.awake.asset:shader-compiler-desktop" in coreModules) {
            "shader-compiler-desktop is not in the graph, so the check proves nothing"
        }
        val stale = coreModules.filterValues { it != core }
        check(stale.isEmpty()) {
            "Core modules left off Core $core:\n" + stale.entries.joinToString("\n") { "  ${it.key}:${it.value}" }
        }
        println("${coreModules.size} Core modules, all at $core; Vulkan family at $vulkan")
    }
}
