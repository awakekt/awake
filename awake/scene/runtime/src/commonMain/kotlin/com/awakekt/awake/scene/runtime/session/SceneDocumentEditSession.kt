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

enum class SceneDocumentSessionMode { Edit, Play, Pause }

/**
 * Owns authored scene data and a disposable, isolated Play world.
 *
 * Starting Play serializes an authored snapshot before instantiation, so simulation mutations
 * cannot alter the document. Stopping Play destroys only the isolated scene. Callers may replace
 * authored data through [applyAuthoredDocument] only while editing; provider-specific Apply UI
 * decides when that explicit operation is allowed.
 */
class SceneDocumentEditSession(
    initialDocument: SceneDocument,
    private val extensions: SceneExtensionRegistry? = null,
) {
    var authoredDocument: SceneDocument = initialDocument
        private set

    var mode: SceneDocumentSessionMode = SceneDocumentSessionMode.Edit
        private set

    var isStepping: Boolean = false
        private set

    private var playScene: Scene? = null

    val world: World?
        get() = playScene?.world

    fun startPlay(): Scene {
        check(mode == SceneDocumentSessionMode.Edit) { "Play is already active." }
        val snapshot = SceneLoader.decode(SceneLoader.encode(authoredDocument))
        SceneValidator.requireValid(snapshot, extensions)
        return SceneLoader.instantiate(snapshot).also { scene ->
            playScene = scene
            mode = SceneDocumentSessionMode.Play
        }
    }

    fun pausePlay() {
        check(mode == SceneDocumentSessionMode.Play) { "Can only pause when playing." }
        mode = SceneDocumentSessionMode.Pause
    }

    fun resumePlay() {
        check(mode == SceneDocumentSessionMode.Pause) { "Can only resume when paused." }
        mode = SceneDocumentSessionMode.Play
    }

    fun stepPlay() {
        check(mode == SceneDocumentSessionMode.Pause) { "Can only step when paused." }
        isStepping = true
    }

    fun clearStepping() {
        isStepping = false
    }

    fun stopPlay() {
        if (mode == SceneDocumentSessionMode.Edit) return
        playScene?.destroy()
        playScene = null
        isStepping = false
        mode = SceneDocumentSessionMode.Edit
    }

    fun applyAuthoredDocument(document: SceneDocument) {
        check(mode == SceneDocumentSessionMode.Edit) {
            "Authored scene data can only be changed while not playing."
        }
        authoredDocument = document
    }

    fun close() {
        stopPlay()
    }
}
