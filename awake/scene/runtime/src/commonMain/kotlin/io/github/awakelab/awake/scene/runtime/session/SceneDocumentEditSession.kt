/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime.session

import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.document.Scene
import io.github.awakelab.awake.scene.document.SceneDocument
import io.github.awakelab.awake.scene.document.SceneExtensionRegistry
import io.github.awakelab.awake.scene.document.SceneLoader
import io.github.awakelab.awake.scene.document.SceneValidator
import io.github.awakelab.awake.scene.document.destroy

enum class SceneDocumentSessionMode { Edit, Play }

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

    fun stopPlay() {
        playScene?.destroy()
        playScene = null
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
