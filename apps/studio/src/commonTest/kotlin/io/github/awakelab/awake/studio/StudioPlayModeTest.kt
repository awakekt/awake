/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.editor.core.store.EditorEffect
import io.github.awakelab.awake.editor.core.store.EditorEntityId
import io.github.awakelab.awake.editor.core.store.EditorIntent
import io.github.awakelab.awake.editor.core.store.EditorMode
import io.github.awakelab.awake.editor.core.store.EditorStore
import io.github.awakelab.awake.editor.core.store.EditorTool
import io.github.awakelab.awake.editor.core.store.EditorViewportPoint
import io.github.awakelab.awake.engine.bootstrap.dsl.app
import io.github.awakelab.awake.engine.bootstrap.dsl.module
import io.github.awakelab.awake.engine.platform.dsl.requireService
import io.github.awakelab.awake.scene.core.Name
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.studio.state.StudioEditorBridge
import io.github.awakelab.awake.studio.state.StudioStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudioPlayModeTest {

    /** Edit mode must hold still: a spin that keeps ticking overwrites every inspector edit. */
    @Test
    fun theCubeSpinsOnlyInPlayMode() = runTest {
        val store = StudioStore()
        val editor = StudioEditorBridge(store)
        val game = app { module(studioModule(store, editorBridge = editor)) }
        game.ready(RecordingCameraRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        repeat(3) { game.update(1f / 60f, 1440f, 900f) }

        val cube = assertNotNull(runtime.world.namedEntity("cube"))
        val rotation = assertNotNull(runtime.world.get<Transform>(cube)).rotation
        val atRest = rotation.y
        repeat(5) { game.update(1f / 60f, 1440f, 900f) }
        assertEquals(atRest, rotation.y, "edit mode must not tick the spin system")

        editor.store.dispatch(EditorIntent.StartPlay)
        repeat(5) { game.update(1f / 60f, 1440f, 900f) }
        assertTrue(rotation.y != atRest, "play mode must tick the spin system, still ${rotation.y}")
    }

    /** Play-mode edits are deliberately thrown away on stop, which is what makes play safe. */
    @Test
    fun stoppingDiscardsWhatPlayModeChanged() = runTest {
        val store = StudioStore()
        val editor = StudioEditorBridge(store)
        val game = app { module(studioModule(store, editorBridge = editor)) }
        game.ready(RecordingCameraRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        game.update(1f / 60f, 1440f, 900f)

        editor.store.dispatch(EditorIntent.StartPlay)
        game.update(1f / 60f, 1440f, 900f)
        val cube = assertNotNull(runtime.world.namedEntity("cube"))
        assertNotNull(runtime.world.get<Transform>(cube)).position.x = 9f

        editor.store.dispatch(EditorIntent.StopPlay)
        game.update(1f / 60f, 1440f, 900f)

        val reloaded = assertNotNull(
            runtime.world.namedEntity("cube"),
            "stopping must re-instantiate the scene",
        )
        assertEquals(
            0f,
            assertNotNull(runtime.world.get<Transform>(reloaded)).position.x,
            "a play-mode edit must not survive stop",
        )
    }
}

internal fun World.namedEntity(name: String): Entity? {
    var found: Entity? = null
    queryEach<Name> { entity, value -> if (found == null && value.value == name) found = entity }
    return found
}
