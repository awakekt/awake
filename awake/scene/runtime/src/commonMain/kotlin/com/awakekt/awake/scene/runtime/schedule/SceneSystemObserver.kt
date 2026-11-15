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
 * - **the app's update block and the UI host's phases**: [APP_UPDATE], [UI_BUILD] (composing,
 *   measuring and drawing the UI tree, one phase), [UI_WAIT] and [UI_STAGE].
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

        /** Building the UI frame: compose, measure and draw of the UI tree. */
        const val UI_BUILD = "ui:build"

        /** Waiting on the renderer's frame resources before the UI is staged. */
        const val UI_WAIT = "ui:wait"

        /** Staging the UI, the scene's canvas views included, for the renderer. */
        const val UI_STAGE = "ui:stage"
    }
}
