/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.project.DEFAULT_SORTING_LAYER
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.scene2d.SortingLayers
import com.awakekt.awake.scene.scene2d.Sprite
import com.awakekt.awake.scene.scene2d.SpriteClipSystem
import com.awakekt.awake.scene.scene2d.SpriteClips
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A project's sprites as `runProject` and [sceneSystemsFor] play them: their clips advance on the
 * scene's clock, once a frame whoever runs the clip system, and the manifest's sorting layers reach
 * the runtime that draws them.
 */
class ProjectSpritesTest {
    @Test
    fun aSceneWithSpriteClipsGetsTheClipSystemFromSceneSystemsFor() {
        installProjectComponents()
        val scene = SceneLoader.decode(CLIPS_SCENE)
        val services = SceneHostServices(input = { GameplayInput(Input().currentSnapshot, InputOwnership()) }, renderer = NoopRenderer())

        val systems = sceneSystemsFor(scene, services)

        assertEquals(1, systems.frame.count { it is SpriteClipSystem })
        systems.close()
    }

    @Test
    fun aHostThatPlaysTheSceneInAWorldOfItsOwnAdvancesItsClips() {
        installProjectComponents()
        val scene = SceneLoader.decode(CLIPS_SCENE)
        val services = SceneHostServices(input = { GameplayInput(Input().currentSnapshot, InputOwnership()) }, renderer = NoopRenderer())
        val world = World()
        scene.instantiate(world = world)
        val systems = sceneSystemsFor(scene, services)

        repeat(FRAMES) { systems.frame.forEach { system -> system.update(world, DELTA) } }

        assertEquals(FRAMES * DELTA, world.clips().elapsedSeconds, TOLERANCE)
        assertEquals(1, world.sprite().frame, "a quarter second at six frames a second shows the second cell")
        systems.close()
    }

    @Test
    fun aPlayedProjectAdvancesItsClipsOnceAFrame() = runTest {
        val game = play(CLIPS_SCENE)
        repeat(FRAMES - 1) { game.frame() }

        // The app's infrastructure advances clips too; the scene's own clip system must not add a second step.
        assertEquals(FRAMES * DELTA, game.world.clips().elapsedSeconds, TOLERANCE)
        assertEquals(1, game.world.sprite().frame)
    }

    @Test
    fun aPlayedProjectDrawsByItsManifestsSortingLayers() = runTest {
        val game = play(CLIPS_SCENE, sortingLayers = """["Background","Default","Foreground"]""")

        val layers = game.runtime.sortingLayers
        assertEquals(listOf("Background", "Default", "Foreground"), layers.names)
        assertEquals(-1, layers.rank("Background"))
        assertEquals(1, layers.rank("Foreground"))
    }

    @Test
    fun aProjectWithoutSortingLayersDrawsEverythingOnTheDefaultLayer() = runTest {
        val game = play(CLIPS_SCENE)

        assertEquals(SortingLayers.None, game.runtime.sortingLayers)
    }

    @Test
    fun theProjectsDefaultLayerIsTheScenes() {
        assertEquals(SortingLayers.DEFAULT, DEFAULT_SORTING_LAYER)
    }

    // --- harness

    private class Game(val runtime: SceneAppLifecycleRuntime, private val update: () -> Unit) {
        val world: World get() = runtime.world
        fun frame() = update()
    }

    private suspend fun play(scene: String, sortingLayers: String? = null): Game {
        val manifest = if (sortingLayers == null) MANIFEST else MANIFEST.dropLast(1) + ""","sortingLayers":$sortingLayers}"""
        val project = loadProject(files(manifest, scene))
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        assertTrue(runtime.hasInfrastructureSystem(SpriteClipSystem::class), "the default infrastructure advances sprite clips")
        return Game(runtime) { game.update(DELTA, WIDTH, HEIGHT) }.also { it.frame() }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private fun files(manifest: String, scene: String) = AssetSource { path ->
        runCatching { mapOf(MANIFEST_PATH to manifest, "scenes/main.scene.json" to scene).getValue(path.value).encodeToByteArray() }
    }

    private fun World.clips(): SpriteClips {
        var found: SpriteClips? = null
        queryEach(SpriteClips::class) { _, clips -> found = clips }
        return assertNotNull(found)
    }

    private fun World.sprite(): Sprite {
        var found: Sprite? = null
        queryEach(Sprite::class) { _, sprite -> found = sprite }
        return assertNotNull(found)
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val FRAMES = 15
        const val TOLERANCE = 1e-4f

        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        val CLIPS_SCENE = """
            {"version": 1, "nodes": [{"name": "Hero", "components": [
              {"component": "sprite", "texture": "hero.png", "columns": 4, "rows": 1, "sortingLayer": "Foreground"},
              {"component": "sprite_clips", "clips": {"walk": {"firstFrame": 0, "frameCount": 4, "framesPerSecond": 6}}}
            ]}]}
        """.trimIndent()
    }
}
