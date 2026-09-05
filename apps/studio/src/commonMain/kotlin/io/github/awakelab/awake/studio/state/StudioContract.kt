/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.state

import io.github.awakelab.awake.scene.controls.camera.CameraMode
import io.github.awakelab.awake.studio.fixture.StudioSceneDescriptor
import io.github.awakelab.awake.studio.fixture.StudioSceneRegistry

internal object StudioContract {
    enum class Projection { Perspective, Orthographic }

    /** Mirrors [CameraRig]'s own fields. Studio used to carry a parallel
     * `CameraPresetMode` (Orbit/Front/Top) with hand-rolled preset math, which left the engine's
     * Cinematic and TopDown unreachable and drag working only in Orbit. */
    data class CameraState(
        val mode: CameraMode = CameraMode.ThirdPerson,
        val projection: Projection = Projection.Perspective,
    )

    data class State(
        val camera: CameraState = CameraState(),
        /** Where the last save landed -- a path on desktop, a filename in the browser. */
        val lastSavedTo: String? = null,
        /**
         * Which bottom-dock tab is showing.
         *
         * Host state, not the dock's: `EditorDock` is stateless so the selection survives the
         * panel being recomposed, and so a host can restore it with the rest of its layout.
         */
        val dockTab: String = DOCK_TAB_CONSOLE,
        /** The selected file's path, or null. Host state for the same reason [dockTab] is. */
        val selectedFile: String? = null,
        val activeSceneId: String = StudioSceneRegistry.defaultScene.id,
    )

    sealed interface Intent {
        data class SetCameraMode(val mode: CameraMode) : Intent
        data class SetProjection(val projection: Projection) : Intent

        // One-shot: snaps the editor (Scene-view) camera's pose to match the scene's own
        // authored camera, like Blender's "View from Camera" -- not a live lock, the editor
        // camera is free to orbit away again right after.
        data object AlignViewToCamera : Intent

        /** Writes the live world back out as an authored document. */
        data object SaveScene : Intent

        /** Reported by the system that did the writing; [SceneWriter] alone knows where it went. */
        data class SceneSaved(val location: String) : Intent

        data class SelectDockTab(val id: String) : Intent

        /** Which file the Files tab is previewing. Null clears the preview. */
        data class SelectFile(val path: String?) : Intent
        data class SelectScene(val sceneId: String) : Intent
    }

    sealed interface Effect {
        // Rebuilding the fixture mutates the live scene and therefore stays outside the reducer.
        data object ReloadFixture : Effect
        data class LoadScene(val descriptor: StudioSceneDescriptor) : Effect

        // Mutating the editor camera's CameraRig orbit state is a side effect too.
        data object AlignViewToCamera : Effect

        // Exporting reads the whole world and writes a file; neither belongs in a reducer.
        data object SaveScene : Effect

        // Snapshots the authored scene before simulation is allowed to touch it.
        data object StartPlay : Effect

        // Rebuilds the world from that snapshot, discarding whatever Play did to it.
        data object StopPlay : Effect
    }
}

/**
 * The bottom dock's tabs.
 *
 * Constants rather than an enum because `EditorDock` takes string ids -- it is a generic layout and
 * cannot know a host's tab set. Naming them here keeps the reducer, the shell and any test spelling
 * them the same way, which is the failure the ids invite.
 */
const val DOCK_TAB_CONSOLE: String = "console"
const val DOCK_TAB_ASSETS: String = "assets"
const val DOCK_TAB_FILES: String = "files"
const val DOCK_TAB_TIMELINE: String = "timeline"
