/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerifyPublishedArtifactsTaskTest {
    @Test
    fun aPinHoldingEveryCoreModuleStrictlyAtTheReleasePasses() = repository { local ->
        webgpuPin(local, dependencies = listOf(MATH to PIN, PLATFORM to PIN), constraints = listOf(MATH to strictly(PIN), PLATFORM to strictly(PIN)))

        assertEquals(emptyList(), violations(local))
    }

    @Test
    fun anEdgeAtTheReleaseSnapshotFails() = repository { local ->
        webgpuPin(local, dependencies = listOf(MATH to "$PIN-SNAPSHOT"), constraints = listOf(MATH to strictly(PIN)))

        assertEquals(listOf("$WASM (wasmJsRuntimeElements): $MATH is at $PIN-SNAPSHOT, not Core $PIN"), violations(local))
    }

    @Test
    fun anEdgeAtTheNextPatchFails() = repository { local ->
        // What main publishes after the release: it sorts above the release and moves a consumer's Core up.
        webgpuPin(local, dependencies = listOf(MATH to "1.2.4-SNAPSHOT"), constraints = listOf(MATH to strictly(PIN)))

        assertEquals(listOf("$WASM (wasmJsRuntimeElements): $MATH is at 1.2.4-SNAPSHOT, not Core $PIN"), violations(local))
    }

    @Test
    fun aCoreConstraintWithoutStrictlyFails() = repository { local ->
        webgpuPin(local, dependencies = listOf(MATH to PIN), constraints = listOf(MATH to """{"requires":"$PIN"}"""))

        assertEquals(
            listOf("$WASM (wasmJsRuntimeElements): the constraint on $MATH is not strictly $PIN (requires $PIN, strictly null)"),
            violations(local),
        )
    }

    @Test
    fun aDependencyNoStrictConstraintHoldsFails() = repository { local ->
        webgpuPin(local, dependencies = listOf(MATH to PIN, PLATFORM to PIN), constraints = listOf(MATH to strictly(PIN)))

        assertEquals(listOf("$WASM (wasmJsRuntimeElements): $PLATFORM has no strictly constraint"), violations(local))
    }

    @Test
    fun anEdgeOnlyAPlatformModuleNamesIsChecked() = repository { local ->
        // The root declares nothing itself; the platform module it is available at holds the edge.
        webgpuPin(local, dependencies = listOf(MATH to "1.2.4-SNAPSHOT"), constraints = listOf(MATH to strictly(PIN)))
        val root = LocalModuleMetadata(local.resolve(ROOT))

        assertTrue(root.edges.isNotEmpty() && root.edges.all { it.module.endsWith(WASM) }, "only the platform module names Awake edges")
        assertEquals(listOf("$WASM (wasmJsRuntimeElements): $MATH is at 1.2.4-SNAPSHOT, not Core $PIN"), violations(local))
    }

    @Test
    fun aConstraintOnACoordinateThisBuildDoesNotPublishFails() = repository { local ->
        // The project's name where its artifact id was overridden: the constraint holds nothing.
        webgpuPin(
            local,
            dependencies = listOf(MATH to PIN),
            constraints = listOf(MATH to strictly(PIN), "com.awakekt.awake.backend:bindings" to strictly(PIN)),
        )

        assertEquals(
            listOf("$WASM (wasmJsRuntimeElements): com.awakekt.awake.backend:bindings is not a module this build publishes"),
            violations(local),
        )
    }

    @Test
    fun aMissingPlatformModuleFails() = repository { local ->
        webgpuPin(local, dependencies = listOf(MATH to PIN), constraints = listOf(MATH to strictly(PIN)))
        Files.delete(local.resolve(WASM))

        assertEquals(listOf("$WASM: Gradle module metadata is not in Maven Local"), violations(local))
    }

    @Test
    fun aVulkanReleaseNeedsTheExactCoreButNoConstraint() = repository { local ->
        // Its own family's edges carry the Vulkan version, and only WebGPU holds Core strictly.
        val root = "com/awakekt/awake/backend/vulkan/0.1.26/vulkan-0.1.26.module"
        val family = "com.awakekt.awake:vulkan-kmp" to "0.1.26"
        write(local.resolve(root), variant("desktopRuntimeElements", dependencies = listOf(MATH to PIN, family)))
        assertEquals(emptyList(), violations(local, root, "vulkan"))

        write(local.resolve(root), variant("desktopRuntimeElements", dependencies = listOf(MATH to "$PIN-SNAPSHOT", family)))
        assertEquals(listOf("$root (desktopRuntimeElements): $MATH is at $PIN-SNAPSHOT, not Core $PIN"), violations(local, root, "vulkan"))
    }

    private fun violations(local: Path, root: String = ROOT, family: String = "webgpu"): List<String> =
        pinnedCoreViolations(LocalModuleMetadata(local.resolve(root)), PIN, family, PUBLISHED) {
            local.relativize(it).toString().replace('\\', '/')
        }

    /** A WebGPU pin as Kotlin Multiplatform lays it out: a root that points at its wasm platform module. */
    private fun webgpuPin(local: Path, dependencies: List<Pair<String, String>>, constraints: List<Pair<String, String>>) {
        write(
            local.resolve(ROOT),
            """{"variants":[{"name":"wasmJsRuntimeElements-published","available-at":{"url":"../../webgpu-wasm-js/$PIN-SNAPSHOT/webgpu-wasm-js-$PIN-SNAPSHOT.module"}}]}""",
        )
        val stdlib = """{"group":"org.jetbrains.kotlin","module":"kotlin-stdlib","version":{"requires":"2.4.10"}}"""
        val ygdrasil = """{"group":"io.ygdrasil","module":"wgpu4k","version":{"requires":"0.2.0-20260716.235022-2"}}"""
        val deps = dependencies.map { (coordinate, version) -> edge(coordinate, """{"requires":"$version"}""") } + stdlib
        val held = constraints.map { (coordinate, version) -> edge(coordinate, version) } + ygdrasil
        write(
            local.resolve(WASM),
            """{"variants":[{"name":"wasmJsRuntimeElements","dependencies":[${deps.joinToString()}],"dependencyConstraints":[${held.joinToString()}]}]}""",
        )
    }

    private fun variant(name: String, dependencies: List<Pair<String, String>>): String {
        val deps = dependencies.map { (coordinate, version) -> edge(coordinate, """{"requires":"$version"}""") }
        return """{"variants":[{"name":"$name","dependencies":[${deps.joinToString()}]}]}"""
    }

    private fun edge(coordinate: String, version: String): String {
        val (group, module) = coordinate.split(':')
        return """{"group":"$group","module":"$module","version":$version}"""
    }

    private fun strictly(version: String) = """{"requires":"$version","strictly":"$version"}"""

    private fun write(path: Path, text: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
    }

    private fun repository(test: (Path) -> Unit) {
        val local = createTempDirectory("maven-local")
        try {
            test(local)
        } finally {
            local.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val PIN = "1.2.3"
        const val MATH = "com.awakekt.awake.core:math"
        const val PLATFORM = "com.awakekt.awake.engine:platform"
        const val ROOT = "com/awakekt/awake/backend/webgpu/$PIN-SNAPSHOT/webgpu-$PIN-SNAPSHOT.module"
        const val WASM = "com/awakekt/awake/backend/webgpu-wasm-js/$PIN-SNAPSHOT/webgpu-wasm-js-$PIN-SNAPSHOT.module"
        val PUBLISHED = setOf(MATH, PLATFORM, "com.awakekt.awake.backend:vulkan", "com.awakekt.awake:vulkan-kmp")
    }
}
