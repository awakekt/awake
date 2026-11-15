/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.schedule

/**
 * Told when each part of a scene frame starts and ends, so a profile can read the clock or the heap
 * at those marks and charge what it measures to the part that spent it (#1621).
 *
 * Install one with [com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime.systemObserver]. None is
 * installed by default, and then nothing is called and nothing is allocated: the frame costs what it
 * did before this existed.
 *
 * Called on the frame's thread, around:
 * - **every scheduled system, each time it runs**, under its plan name
 *   ([com.awakekt.awake.scene.runtime.PlannedSystem.name]). A fixed system runs once a step, so it is
 *   reported once a step; its interpolation is reported as `<name>.interpolate`.
 * - **the infrastructure systems** (transforms, scene extraction, recording), under
 *   `infrastructure:<class name>`; the transform pass run before pre-animation and frame systems is
 *   `infrastructure:pre-frame transforms`.
 * - **the app's update block and the UI host's phases**: [APP_UPDATE], [UI_BUILD], [UI_WAIT] and
 *   [UI_STAGE]. The UI host's own stages, [UI_COMPOSE], [UI_MEASURE] and [UI_DRAW], are reported in
 *   their own marks, and [UI_BUILD] is ended before each and begun again after it, so it holds only
 *   what runs between them. The renderer's own stages, [RENDER_PREPARE], [RENDER_UPLOAD],
 *   [RENDER_RECORD] and [RENDER_SUBMIT], are reported the same way inside the system that draws, on
 *   a backend that splits its draw. No mark encloses another.
 *
 * Every name is a string built once, when the scene starts, so a name can be compared by identity
 * and a profile keyed by it allocates nothing to look it up. [before] and [after] come in pairs
 * around one run; a system that throws is reported by the app's error handler, and if it ends the
 * frame, its [after] is not called.
 */
interface SceneSystemObserver {
    /** [name] is about to run. */
    fun before(name: String)

    /** [name] has run. */
    fun after(name: String)

    /** The names of the runtime's own phases, which are not scheduled systems. */
    companion object {
        /** The app's own `update { }` block, once a fixed step. */
        const val APP_UPDATE = "app:update"

        /**
         * Building the UI frame, less its [UI_COMPOSE], [UI_MEASURE] and [UI_DRAW] stages: input
         * dispatch, focus, semantics and the scene canvas around them.
         */
        const val UI_BUILD = "ui:build"

        /** Composing the UI tree, or skipping a composition nothing invalidated. */
        const val UI_COMPOSE = "ui:compose"

        /** Measuring and placing the UI tree, or reusing last frame's layout. */
        const val UI_MEASURE = "ui:measure"

        /** Painting the UI tree into the frame's primitives. */
        const val UI_DRAW = "ui:draw"

        /** Waiting on the renderer's frame resources before the UI is staged. */
        const val UI_WAIT = "ui:wait"

        /** Staging the UI, the scene's canvas views included, for the renderer. */
        const val UI_STAGE = "ui:stage"

        /** The renderer acquiring its surface and ordering the frame's draws. */
        const val RENDER_PREPARE = "render:prepare"

        /** The renderer writing the frame's uniforms and textures to the GPU. */
        const val RENDER_UPLOAD = "render:upload"

        /** The renderer encoding the frame's passes. */
        const val RENDER_RECORD = "render:record"

        /** The renderer handing the encoded frame to the GPU. */
        const val RENDER_SUBMIT = "render:submit"
    }
}
