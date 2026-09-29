/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.player

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SkinnedPlaybackTest {
    @Test
    fun aSkinnedModelPlaysItsFirstClip() = runTest {
        val files = mapOf(
            "awake.project.json" to MANIFEST.encodeToByteArray(),
            "scenes/main.scene.json" to SCENE.encodeToByteArray(),
            MODEL to File(SAMPLE_MODEL).readBytes(),
        )
        val project = loadPlayableProject(AssetSource { path -> runCatching { files.getValue(path.value) } })
        val game = app { scene("play") { playProject(project) } }
        game.ready(TestRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        game.update(1f / 60f, 800f, 600f)

        val animated = mutableListOf<Pair<Animator, SkinnedPose>>()
        runtime.world.queryEach(Animator::class, SkinnedPose::class) { _, animator, pose -> animated += animator to pose }
        assertEquals(1, animated.size, "the model must get exactly one animator")
        val (_, pose) = animated.single()
        val before = pose.jointPalette.copyOf()
        repeat(FRAMES) { game.update(1f / 60f, 800f, 600f) }

        assertFalse(before.contentEquals(pose.jointPalette), "the joints must move as the clip plays")
    }

    private class TestRenderer : NoopRenderer(), GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val FRAMES = 20
        const val MODEL = "models/walker.gltf"
        // A public-domain skinned sample shipped with the engine showcase.
        const val SAMPLE_MODEL = "../../../samples/engine-showcase/src/commonMain/resources/assets/models/CesiumMan.gltf"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """
{ "version": 1, "name": "walk", "nodes": [
  { "name": "Walker", "components": [ { "component": "meshRenderer", "mesh": "$MODEL", "material": "skinned-material" } ] }
] }
"""
    }
}
