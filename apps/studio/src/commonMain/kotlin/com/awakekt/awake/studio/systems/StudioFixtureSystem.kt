/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.systems

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.editor.EditorHistory
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.writeSceneDocument
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.studio.fixture.StudioFixture
import com.awakekt.awake.studio.state.StudioContract
import com.awakekt.awake.studio.state.StudioStore

/** Applies Studio host effects to its one fixture; it does not own an engine-demo catalogue. */
internal class StudioFixtureSystem(
    private val runtime: SceneAppLifecycleRuntime,
    private val store: StudioStore,
    private val fixture: StudioFixture,
    private val history: EditorHistory,
    private val alignViewToAuthoredCamera: (World) -> Unit,
) : System {
    /** The scene as authored, held only while Play is running. */
    private var authoredBeforePlay: SceneDocument? = null

    override fun update(world: World, delta: Float) {
        store.drainEffects().forEach { effect ->
            when (effect) {
                StudioContract.Effect.ReloadFixture -> fixture.load(runtime)
                is StudioContract.Effect.LoadScene -> {
                    fixture.selectScene(runtime, effect.descriptor)
                    history.clear()
                    alignViewToAuthoredCamera(world)
                }
                StudioContract.Effect.AlignViewToCamera -> alignViewToAuthoredCamera(world)
                StudioContract.Effect.SaveScene -> save()
                StudioContract.Effect.StartPlay -> startPlay()
                StudioContract.Effect.StopPlay -> stopPlay()
            }
        }
    }

    /**
     * Snapshots the authored scene so Stop has something to put back.
     *
     * Play runs on the same world the editor was using rather than a second one: Studio's systems,
     * renderer and camera are all bound to `runtime.world`, and scheduling a parallel world is a
     * runtime change, not an editor one. What matters to a user is the guarantee -- that simulation
     * cannot damage the scene they authored -- and a snapshot delivers that guarantee on one world.
     */
    private fun startPlay() {
        authoredBeforePlay = fixture.exportFrom(runtime)
    }

    /**
     * Rebuilds the world from the snapshot, discarding everything simulation did.
     *
     * The history is cleared rather than kept. Rebuilding replaces every component instance in the
     * world, and the commands on the stack hold references to the *old* ones -- undoing into them
     * would write to objects nothing renders any more and look like undo silently failing. Losing
     * the stack at Stop is the honest version of that.
     */
    private fun stopPlay() {
        val snapshot = authoredBeforePlay ?: return
        authoredBeforePlay = null
        fixture.load(runtime, snapshot)
        history.clear()
    }

    /**
     * Exports the live world and writes it out.
     *
     * [EditorHistory.markSaved] is what makes the toolbar's unsaved dot go away, and it runs after
     * the write rather than before: a failed export must leave the document dirty, because the edit
     * it describes is still only in memory.
     */
    private fun save() {
        val document = fixture.exportFrom(runtime)
        val location = writeSceneDocument(SAVE_FILE_NAME, SceneLoader.encode(document))
        history.markSaved()
        store.dispatch(StudioContract.Intent.SceneSaved(location))
    }

    private companion object {
        /** One fixture, one file. A Save As needs a picker, which is its own piece of work. */
        const val SAVE_FILE_NAME = "rotating-cube.scene.json"
    }
}
