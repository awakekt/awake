/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.player

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.project.runtime.SceneFeature
import com.awakekt.awake.project.runtime.SceneSystemPlan
import com.awakekt.awake.scene.document.SceneDocument
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AwakePlayerTest {
    @Test
    fun loadsFullProjectResolvesTitleStringsAndRunsSmokeTest() = runTest {
        val source = inMemorySource(
            "awake.project.json" to SAMPLE_MANIFEST,
            "scenes/main.scene.json" to SAMPLE_SCENE,
            "strings/en.json" to """{"hud":{"greeting":"Ahoy"}}""",
        )

        loadPlayerProject(source, config = AwakePlayerConfig(hasRenderer = false)).use { game ->
            assertEquals("Harbor Town", game.title)
            assertEquals("Ahoy", game.strings?.text("hud.greeting"))

            val report = smokeTestPlayer(game, frames = 5)
            assertEquals("com.example.harbor", report.projectId)
            assertEquals("Harbor Town", report.projectName)
            assertEquals("scenes/main.scene.json", report.entryScene)
            assertEquals(1, report.nodeCount)
            assertEquals(5, report.frames)
            assertTrue(report.formatLine().startsWith("AWAKE_PLAYER_OK title=Harbor Town "))
        }
    }

    @Test
    fun loadsStandaloneDeclarativeSceneWithoutProjectManifest() = runTest {
        val assets = inMemorySource("scenes/sunset_bay.scene.json" to SAMPLE_SCENE)

        loadPlayerScene(
            scenePath = "scenes/sunset_bay.scene.json",
            assets = assets,
            config = AwakePlayerConfig(hasRenderer = false),
        ).use { game ->
            assertEquals("Sunset Bay", game.title)
            assertEquals("scenes/sunset_bay.scene.json", game.project.manifest.entryScene)
            assertNull(game.strings)

            val report = smokeTestPlayer(game, frames = 3)
            assertEquals(1, report.nodeCount)
            assertEquals(3, report.frames)
        }
    }

    @Test
    fun loadsRawSceneDocumentJsonDirectly() = runTest {
        loadPlayerSceneDocument(
            sceneJson = SAMPLE_SCENE,
            name = "Quick Sandbox",
            config = AwakePlayerConfig(hasRenderer = false),
        ).use { game ->
            assertEquals("Quick Sandbox", game.title)
            val report = smokeTestPlayer(game, frames = 2)
            assertEquals(1, report.nodeCount)
        }
    }

    @Test
    fun rejectsTraversalAndAbsolutePathsForStandaloneScene() = runTest {
        assertFailsWith<IllegalArgumentException> {
            loadPlayerScene("../escape.scene.json", assets = EmptyAssetSource)
        }
        assertFailsWith<IllegalArgumentException> {
            loadPlayerScene("/abs.scene.json", assets = EmptyAssetSource)
        }
    }

    @Test
    fun runsCustomSceneFeatureSuppliedInConfig() = runTest {
        var ticks = 0
        val counterFeature = object : SceneFeature {
            override val id: String = "test.counter"
            override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
                plan.frame("test-counter") {
                    object : System {
                        override fun update(world: World, deltaTime: Float) {
                            ticks++
                        }
                    }
                }
            }
        }

        loadPlayerSceneDocument(
            sceneJson = SAMPLE_SCENE,
            name = "Feature Host",
            config = AwakePlayerConfig(features = listOf(counterFeature), hasRenderer = false),
        ).use { game ->
            smokeTestPlayer(game, frames = 4)
            assertEquals(4, ticks)
        }
    }

    @Test
    fun createsPlayerAppAndExposesPointerCursorProviders() = runTest {
        loadPlayerSceneDocument(
            sceneJson = SAMPLE_SCENE,
            name = "App Shell",
            config = AwakePlayerConfig(hasRenderer = false),
        ).use { game ->
            val app = createPlayerApp(game, AwakePlayerConfig(windowTitle = "Custom Title"))
            try {
                assertNotNull(pointerCursorOf(app).invoke())
                assertNull(pointerCursorImageOf(app).invoke())
            } finally {
                app.dispose()
            }
        }
    }

    @Test
    fun resolvePlayerTitlePrefersOverrideThenProjectThenDefault() {
        assertEquals("Custom", resolvePlayerTitle("Custom", "Project"))
        assertEquals("Project", resolvePlayerTitle(null, "Project"))
        assertEquals("Project", resolvePlayerTitle("   ", "Project"))
        assertEquals(DEFAULT_PLAYER_TITLE, resolvePlayerTitle(null, null))
    }

    private fun inMemorySource(vararg entries: Pair<String, String>): AssetSource {
        val map = entries.associate { (k, v) -> k to v.encodeToByteArray() }
        return object : AssetSource {
            override suspend fun read(path: AssetPath): Result<ByteArray> =
                map[path.value]?.let { Result.success(it) }
                    ?: Result.failure(IllegalArgumentException("Missing test asset: ${path.value}"))

            override suspend fun exists(path: AssetPath): Boolean = path.value in map
        }
    }

    private companion object {
        const val SAMPLE_MANIFEST =
            """{"formatVersion":1,"id":"com.example.harbor","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        const val SAMPLE_SCENE = """{
            "version": 1,
            "name": "main",
            "nodes": [
                {
                    "name": "Camera",
                    "components": [
                        {
                            "component": "camera",
                            "eye": { "x": 0.0, "y": 5.0, "z": 10.0 },
                            "center": { "x": 0.0, "y": 0.0, "z": 0.0 },
                            "fovYDegrees": 45.0,
                            "near": 0.1,
                            "far": 500.0,
                            "primary": true
                        }
                    ]
                }
            ]
        }"""
    }
}
