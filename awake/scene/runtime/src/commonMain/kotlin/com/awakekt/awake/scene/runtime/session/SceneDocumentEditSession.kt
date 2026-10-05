/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.session

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.binding.destroy
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneExtensionRegistry
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneValidator

/**
 * Execution mode of a scene document session.
 */
enum class SceneDocumentSessionMode {
    /** The scene document is in authoring mode and not executing runtime simulation. */
    Edit,

    /** The scene document has been instantiated into a play world and is actively simulating. */
    Play,

    /** The active simulation is paused. */
    Pause,
}

/**
 * Owns authored scene data and a disposable, isolated Play world.
 *
 * Starting Play serializes an authored snapshot before instantiation, so simulation mutations
 * cannot alter the document. Stopping Play destroys only the isolated scene. Callers may replace
 * authored data through [applyAuthoredDocument] only while editing; provider-specific Apply UI
 * decides when that explicit operation is allowed.
 *
 * @param initialDocument The initial authored [SceneDocument] snapshot.
 * @param extensions Optional registry of scene document schema extensions.
 */
class SceneDocumentEditSession(
    initialDocument: SceneDocument,
    private val extensions: SceneExtensionRegistry? = null,
) {
    /**
     * The current authored document snapshot.
     */
    var authoredDocument: SceneDocument = initialDocument
        private set

    /**
     * The current execution mode of the session.
     */
    var mode: SceneDocumentSessionMode = SceneDocumentSessionMode.Edit
        private set

    /**
     * Whether single-frame stepping is currently enabled while paused.
     */
    var isStepping: Boolean = false
        private set

    private var playScene: Scene? = null

    /**
     * The active ECS [World] of the play scene, or `null` when in edit mode.
     */
    val world: World?
        get() = playScene?.world

    /**
     * Starts runtime play mode by creating and returning an isolated [Scene] instance.
     *
     * @return The newly instantiated simulation [Scene].
     */
    fun startPlay(): Scene {
        check(mode == SceneDocumentSessionMode.Edit) { "Play is already active." }
        val snapshot = SceneLoader.decode(SceneLoader.encode(authoredDocument))
        SceneValidator.requireValid(snapshot, extensions)
        return SceneLoader.instantiate(snapshot).also { scene ->
            playScene = scene
            mode = SceneDocumentSessionMode.Play
        }
    }

    /**
     * Pauses the active runtime simulation.
     */
    fun pausePlay() {
        check(mode == SceneDocumentSessionMode.Play) { "Can only pause when playing." }
        mode = SceneDocumentSessionMode.Pause
    }

    /**
     * Resumes the paused runtime simulation.
     */
    fun resumePlay() {
        check(mode == SceneDocumentSessionMode.Pause) { "Can only resume when paused." }
        mode = SceneDocumentSessionMode.Play
    }

    /**
     * Steps the paused runtime simulation forward by a single frame.
     */
    fun stepPlay() {
        check(mode == SceneDocumentSessionMode.Pause) { "Can only step when paused." }
        isStepping = true
    }

    /**
     * Clears the single-frame stepping flag after stepping executes.
     */
    fun clearStepping() {
        isStepping = false
    }

    /**
     * Stops runtime simulation and tears down the isolated simulation scene.
     */
    fun stopPlay() {
        if (mode == SceneDocumentSessionMode.Edit) return
        playScene?.destroy()
        playScene = null
        isStepping = false
        mode = SceneDocumentSessionMode.Edit
    }

    /**
     * Replaces the authored scene document while in edit mode.
     *
     * @param document The updated [SceneDocument] to store.
     */
    fun applyAuthoredDocument(document: SceneDocument) {
        check(mode == SceneDocumentSessionMode.Edit) {
            "Authored scene data can only be changed while not playing."
        }
        authoredDocument = document
    }

    /**
     * Closes this session, ensuring any active simulation is stopped and cleaned up.
     */
    fun close() {
        stopPlay()
    }
}
