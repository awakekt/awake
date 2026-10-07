/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilityLayeringChecksTest {
    // --- which configurations are main code

    @Test
    fun theConfigurationsABuildFileDeclaresMainDependenciesInAreMain() {
        listOf(
            "api",
            "implementation",
            "compileOnly",
            "runtimeOnly",
            "compileOnlyApi",
            "commonMainApi",
            "commonMainImplementation",
            "desktopMainImplementation",
            "androidMainCompileOnly",
            "wasmJsMainRuntimeOnly",
        ).forEach { assertTrue(isMainDependencyConfiguration(it), it) }
    }

    @Test
    fun testConfigurationsAreNotMain() {
        listOf(
            "testImplementation",
            "testFixturesApi",
            "commonTestImplementation",
            "desktopTestApi",
            "androidHostTestImplementation",
            "androidDeviceTestImplementation",
            "iosSimulatorArm64TestImplementation",
        ).forEach { assertFalse(isMainDependencyConfiguration(it), it) }
    }

    /** They inherit from the declaring ones and declare nothing themselves; counting them would only repeat. */
    @Test
    fun derivedResolvableAndConsumableConfigurationsAreNotMain() {
        listOf(
            "desktopCompileClasspath",
            "desktopRuntimeClasspath",
            "desktopApiElements",
            "apiElements",
            "commonMainResolvableDependenciesMetadata",
            "kover",
        ).forEach { assertFalse(isMainDependencyConfiguration(it), it) }
    }

    @Test
    fun mainProjectDependenciesKeepOnlyMainConfigurationsAndRepeatNothing() {
        val dependencies = mainProjectDependencies(
            mapOf(
                "commonMainApi" to listOf(":awake:core:math", ":awake:ecs"),
                "desktopMainImplementation" to listOf(":awake:ecs"),
                "commonTestImplementation" to listOf(":awake:scene:runtime"),
            ),
        )

        assertEquals(setOf(":awake:core:math", ":awake:ecs"), dependencies)
    }

    // --- reaching a scene module

    @Test
    fun aDirectDependencyOnASceneModuleIsReached() {
        val graph = mapOf(":awake:navigation" to setOf(":awake:core:math", ":awake:scene:world"))

        assertEquals(
            mapOf(":awake:scene:world" to listOf(":awake:navigation", ":awake:scene:world")),
            sceneModulesReached(graph, ":awake:navigation"),
        )
    }

    /** A capability that needs another module that needs the scene layer cannot be used without it either. */
    @Test
    fun aSceneModuleReachedThroughAnotherModuleIsFoundWithItsChain() {
        val graph = mapOf(
            ":awake:capability" to setOf(":awake:helper"),
            ":awake:helper" to setOf(":awake:project:runtime"),
            ":awake:project:runtime" to setOf(":awake:scene:document"),
        )

        assertEquals(
            mapOf(
                ":awake:scene:document" to listOf(":awake:capability", ":awake:helper", ":awake:project:runtime", ":awake:scene:document"),
            ),
            sceneModulesReached(graph, ":awake:capability"),
        )
    }

    @Test
    fun theShortestChainIsReportedAndTheWalkStopsAtTheFirstSceneModule() {
        val graph = mapOf(
            ":awake:a" to setOf(":awake:b", ":awake:scene:x"),
            ":awake:b" to setOf(":awake:scene:x"),
            ":awake:scene:x" to setOf(":awake:scene:y"),
        )

        assertEquals(mapOf(":awake:scene:x" to listOf(":awake:a", ":awake:scene:x")), sceneModulesReached(graph, ":awake:a"))
    }

    @Test
    fun aCycleEnds() {
        val graph = mapOf(":awake:a" to setOf(":awake:b"), ":awake:b" to setOf(":awake:a"))

        assertEquals(emptyMap(), sceneModulesReached(graph, ":awake:a"))
    }

    @Test
    fun aModuleThatOnlyLooksLikeASceneModuleIsNotOne() {
        val graph = mapOf(":awake:a" to setOf(":awake:scenery", ":awake:scene"))

        assertEquals(emptyMap(), sceneModulesReached(graph, ":awake:a"))
    }

    // --- scene types in source

    @Test
    fun anImportOrAFullyQualifiedReferenceIsFoundOnItsLine() {
        val source = """
            package com.awakekt.awake.navigation

            import com.awakekt.awake.core.math.Vec3f
            import com.awakekt.awake.scene.world.WorldCellCoord

            fun cell(): Any = com.awakekt.awake.scene.world.WorldPartitionConfig()
        """.trimIndent()

        assertEquals(listOf(4, 6), sceneReferenceLines(source))
    }

    /** A KDoc link to a scene type, or a sentence about one, is documentation, not a dependency. */
    @Test
    fun commentsAreNotReferences() {
        val source = """
            /**
             * Fed by [com.awakekt.awake.scene.world.WorldPartitionSystem].
             */
            // import com.awakekt.awake.scene.world.WorldCellCoord
            /* outer /* nested com.awakekt.awake.scene.x */ still com.awakekt.awake.scene.y */
            val x = 1 // see com.awakekt.awake.scene.z
        """.trimIndent()

        assertEquals(emptyList(), sceneReferenceLines(source))
    }

    @Test
    fun stringAndCharacterLiteralsAreNotReferences() {
        val source = """
            val a = "com.awakekt.awake.scene.world"
            val b = "escaped \" com.awakekt.awake.scene.world"
            val c = '"'
            val d = ${"\"\"\""}raw com.awakekt.awake.scene.world${"\"\"\""}
            import com.awakekt.awake.scene.real.Type
        """.trimIndent()

        assertEquals(listOf(5), sceneReferenceLines(source))
    }

    @Test
    fun lineNumbersSurviveMultiLineCommentsAndRawStrings() {
        val source = listOf(
            "/*",
            " * two",
            " */",
            "val raw = " + "\"\"\"",
            "line",
            "\"\"\"",
            "import com.awakekt.awake.scene.document.SceneComponent",
        ).joinToString("\n")

        assertEquals(listOf(7), sceneReferenceLines(source))
    }

    @Test
    fun aPackageThatOnlyStartsLikeTheScenePackageIsNotOne() {
        assertEquals(emptyList(), sceneReferenceLines("import com.awakekt.awake.scenery.Thing\nimport com.awakekt.awake.render.passes.ScenePassCompiler"))
    }

    // --- the ledger

    @Test
    fun aModuleCoupledToTheSceneLayerThatIsNotOnTheLedgerFailsAndSaysWhyAndWhatToDo() {
        val failures = capabilityLayeringFailures(
            sceneCouplings = mapOf(
                ":awake:particles" to listOf("reaches :awake:scene:document through :awake:particles -> :awake:x -> :awake:scene:document"),
                ":awake:core:math" to emptyList(),
            ),
            debt = emptySet(),
        )

        assertEquals(1, failures.size)
        assertTrue(":awake:particles depends on the scene layer" in failures.single(), failures.single())
        assertTrue(":awake:x -> :awake:scene:document" in failures.single(), "the message should give the chain: ${failures.single()}")
        assertTrue("awake/scene/README.md" in failures.single(), "the message should say where the rule is: ${failures.single()}")
    }

    @Test
    fun aLongListOfReasonsIsCutShort() {
        val reasons = (1..10).map { "awake/x/src/commonMain/kotlin/X.kt:$it names a scene type" }

        val failure = capabilityLayeringFailures(mapOf(":awake:x" to reasons), emptySet()).single()

        assertTrue("and 7 more" in failure, failure)
        assertFalse("X.kt:4 " in failure, failure)
    }

    @Test
    fun aModuleOnTheLedgerIsToleratedWhileItIsStillCoupled() {
        assertEquals(
            emptyList(),
            capabilityLayeringFailures(
                sceneCouplings = mapOf(":awake:navigation" to listOf("depends on :awake:scene:world")),
                debt = setOf(":awake:navigation"),
            ),
        )
    }

    /** The ledger only shrinks: a debt that has been paid fails until its line is deleted. */
    @Test
    fun aModuleOnTheLedgerThatIsNoLongerCoupledFails() {
        val failures = capabilityLayeringFailures(
            sceneCouplings = mapOf(":awake:navigation" to emptyList()),
            debt = setOf(":awake:navigation", ":awake:gone"),
        )

        assertEquals(2, failures.size)
        assertTrue(failures.all { "Remove it from the list" in it }, failures.toString())
    }

    @Test
    fun cleanModulesAndAnEmptyLedgerPass() {
        assertEquals(
            emptyList(),
            capabilityLayeringFailures(mapOf(":awake:core:math" to emptyList(), ":awake:ecs" to emptyList()), emptySet()),
        )
    }
}
