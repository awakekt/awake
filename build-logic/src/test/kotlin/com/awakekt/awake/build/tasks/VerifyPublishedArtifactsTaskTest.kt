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
        write(local.resolve(root), variant("desktopRuntimeElements", dependencies = listOf(BOM to PIN, MATH to PIN, family)))
        assertEquals(emptyList(), violations(local, root, "vulkan"))

        write(local.resolve(root), variant("desktopRuntimeElements", dependencies = listOf(BOM to PIN, MATH to "$PIN-SNAPSHOT", family)))
        assertEquals(listOf("$root (desktopRuntimeElements): $MATH is at $PIN-SNAPSHOT, not Core $PIN"), violations(local, root, "vulkan"))
    }

    @Test
    fun aVulkanVariantReachingCoreWithoutThePlatformFails() = repository { local ->
        val root = "com/awakekt/awake/backend/vulkan/0.1.26/vulkan-0.1.26.module"
        write(local.resolve(root), variant("desktopRuntimeElements", dependencies = listOf(MATH to PIN)))

        assertEquals(listOf("$root (desktopRuntimeElements): reaches Core without $BOM at $PIN"), violations(local, root, "vulkan"))
    }

    @Test
    fun aCoreModuleWhoseEveryVariantNamesThePlatformPasses() = repository { local ->
        coreModule(local, "math", apiEdges = listOf(BOM to VERSION), runtimeEdges = listOf(BOM to VERSION, MATH to VERSION))

        assertEquals(emptyList(), platformEdgeViolations(local, "math"))
    }

    @Test
    fun aCoreVariantWithoutThePlatformFails() = repository { local ->
        // The variant keeps another dependency, so it is not simply empty, but the platform never reaches it.
        coreModule(local, "math", apiEdges = listOf(BOM to VERSION), runtimeEdges = listOf(MATH to VERSION))

        assertEquals(
            listOf("$MATH_DESKTOP (desktopRuntimeElements-published): no dependency on $BOM at $VERSION"),
            platformEdgeViolations(local, "math"),
        )
    }

    @Test
    fun aCorePlatformEdgeAtAnotherVersionFails() = repository { local ->
        coreModule(local, "math", apiEdges = listOf(BOM to "1.2.2"), runtimeEdges = listOf(BOM to VERSION))

        assertEquals(
            listOf("$MATH_DESKTOP (desktopApiElements-published): no dependency on $BOM at $VERSION"),
            platformEdgeViolations(local, "math"),
        )
    }

    @Test
    fun theCorePlatformListingEveryPublishedCoreModulePasses() = repository { local ->
        bomModule(local, listOf(MATH, PLATFORM))

        assertEquals(emptyList(), listingViolations(local, members = setOf(MATH, PLATFORM), held = setOf(MATH, PLATFORM)))
    }

    @Test
    fun theCorePlatformNamingAModuleMavenLocalDoesNotHoldFails() = repository { local ->
        bomModule(local, listOf(MATH, PLATFORM))

        assertEquals(
            listOf(
                "$BOM_MODULE (apiElements): $PLATFORM is not in Maven Local at $VERSION",
                "$BOM_MODULE (runtimeElements): $PLATFORM is not in Maven Local at $VERSION",
            ),
            listingViolations(local, members = setOf(MATH, PLATFORM), held = setOf(MATH)),
        )
    }

    @Test
    fun theCorePlatformNamingAModuleThisBuildDoesNotPublishFails() = repository { local ->
        bomModule(local, listOf(MATH, "com.awakekt.awake.backend:vulkan"))

        assertEquals(
            listOf(
                "$BOM_MODULE (apiElements): com.awakekt.awake.backend:vulkan is not a Core module this build publishes",
                "$BOM_MODULE (runtimeElements): com.awakekt.awake.backend:vulkan is not a Core module this build publishes",
            ),
            listingViolations(local, members = setOf(MATH), held = setOf(MATH, "com.awakekt.awake.backend:vulkan")),
        )
    }

    @Test
    fun theCorePlatformLeavingOutAPublishedModuleFails() = repository { local ->
        bomModule(local, listOf(MATH))

        assertEquals(
            listOf(
                "$BOM_MODULE (apiElements): $PLATFORM is published but not listed",
                "$BOM_MODULE (runtimeElements): $PLATFORM is published but not listed",
            ),
            listingViolations(local, members = setOf(MATH, PLATFORM), held = setOf(MATH, PLATFORM)),
        )
    }

    @Test
    fun theCorePlatformAtAnotherVersionFails() = repository { local ->
        bomModule(local, listOf(MATH), version = "1.2.2")

        assertEquals(
            listOf(
                "$BOM_MODULE (apiElements): $MATH is at 1.2.2, not $VERSION",
                "$BOM_MODULE (runtimeElements): $MATH is at 1.2.2, not $VERSION",
            ),
            listingViolations(local, members = setOf(MATH), held = setOf(MATH)),
        )
    }

    @Test
    fun theCoordinatesMavenLocalHoldsAreReadFromTheirFolders() = repository { local ->
        val repository = local.resolve("com/awakekt/awake")
        write(repository.resolve("core/math/$VERSION/math-$VERSION.pom"), "<project/>")
        write(repository.resolve("bom/$VERSION/bom-$VERSION.pom"), "<project/>")
        write(repository.resolve("core/math/1.0.0/math-1.0.0.pom"), "<project/>")
        write(repository.resolve("engine/render/passes/$VERSION/passes-$VERSION.pom"), "<project/>")

        assertEquals(
            setOf(MATH, BOM, "com.awakekt.awake.engine.render:passes"),
            heldCoordinates(repository, VERSION).keys,
        )
    }

    private fun platformEdgeViolations(local: Path, artifact: String): List<String> =
        coreBomViolations(LocalModuleMetadata(local.resolve("com/awakekt/awake/core/$artifact/$VERSION/$artifact-$VERSION.module")), VERSION) {
            local.relativize(it).toString().replace('\\', '/')
        }

    private fun listingViolations(local: Path, members: Set<String>, held: Set<String>): List<String> =
        coreBomListingViolations(LocalModuleMetadata(local.resolve(BOM_MODULE)), VERSION, members, held) {
            local.relativize(it).toString().replace('\\', '/')
        }

    /** A Core module as Kotlin Multiplatform lays it out: a root that points at its desktop platform module. */
    private fun coreModule(local: Path, artifact: String, apiEdges: List<Pair<String, String>>, runtimeEdges: List<Pair<String, String>>) {
        write(
            local.resolve("com/awakekt/awake/core/$artifact/$VERSION/$artifact-$VERSION.module"),
            """{"variants":[{"name":"desktopApiElements-published","available-at":{"url":"../../$artifact-desktop/$VERSION/$artifact-desktop-$VERSION.module"}}]}""",
        )
        val api = variantObject("desktopApiElements-published", apiEdges)
        val runtime = variantObject("desktopRuntimeElements-published", runtimeEdges)
        write(
            local.resolve("com/awakekt/awake/core/$artifact-desktop/$VERSION/$artifact-desktop-$VERSION.module"),
            """{"variants":[$api,$runtime,{"name":"desktopSourcesElements-published"}]}""",
        )
    }

    /** The Core platform: one constraint per coordinate, on the API and runtime variants alike. */
    private fun bomModule(local: Path, coordinates: List<String>, version: String = VERSION) {
        val constraints = coordinates.joinToString { edge(it, """{"requires":"$version"}""") }
        write(
            local.resolve(BOM_MODULE),
            """{"variants":[{"name":"apiElements","dependencyConstraints":[$constraints]},{"name":"runtimeElements","dependencyConstraints":[$constraints]}]}""",
        )
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

    private fun variant(name: String, dependencies: List<Pair<String, String>>): String =
        """{"variants":[${variantObject(name, dependencies)}]}"""

    private fun variantObject(name: String, dependencies: List<Pair<String, String>>): String {
        val deps = dependencies.map { (coordinate, version) -> edge(coordinate, """{"requires":"$version"}""") }
        return """{"name":"$name","dependencies":[${deps.joinToString()}]}"""
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
        const val VERSION = PIN
        const val MATH = "com.awakekt.awake.core:math"
        const val PLATFORM = "com.awakekt.awake.engine:platform"
        const val BOM = "com.awakekt.awake:bom"
        const val BOM_MODULE = "com/awakekt/awake/bom/$VERSION/bom-$VERSION.module"
        const val MATH_DESKTOP = "com/awakekt/awake/core/math-desktop/$VERSION/math-desktop-$VERSION.module"
        const val ROOT = "com/awakekt/awake/backend/webgpu/$PIN-SNAPSHOT/webgpu-$PIN-SNAPSHOT.module"
        const val WASM = "com/awakekt/awake/backend/webgpu-wasm-js/$PIN-SNAPSHOT/webgpu-wasm-js-$PIN-SNAPSHOT.module"
        val PUBLISHED = setOf(MATH, PLATFORM, BOM, "com.awakekt.awake.backend:vulkan", "com.awakekt.awake:vulkan-kmp")
    }
}
