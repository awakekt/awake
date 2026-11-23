/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.UnscaledSystem
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.runtime.SceneSystemPhase
import com.awakekt.awake.scene.runtime.schedule.SceneSystemObserver

/**
 * The plan names of a played scene's systems, one list per phase, in step with the lists of [set]
 * they name, so the runner can tell the app's [SceneSystemObserver] about each system by its own name
 * instead of only about `project-fixed`, `project-pre-animation` and `project-frame`, which run them.
 *
 * Built the first time an observer is installed while [set] plays, so a frame with no observer builds
 * nothing. A phase whose plan does not match its list, as in a set built by hand, has no names, and its
 * systems are left inside the outer mark.
 */
internal class ProjectSystemMarks(val set: SceneSystemSet) {
    val fixed: List<String>? = namesOf(SceneSystemPhase.Fixed, set.fixed.size)
    val preAnimation: List<String>? = namesOf(SceneSystemPhase.PreAnimation, set.preAnimation.size)
    val frame: List<String>? = namesOf(SceneSystemPhase.Frame, set.frame.size)

    private fun namesOf(phase: SceneSystemPhase, size: Int): List<String>? =
        set.planned.filter { it.phase == phase }.map { it.name }.takeIf { it.size == size }
}

/**
 * Runs [systems], told to [observer] by their [names]: [outer], the scheduled system they run inside,
 * is ended before them and begun again after, so no mark encloses another and a profile that adds up
 * its marks counts each system once. [outer] then holds only the runner's own steps around them.
 *
 * With no [observer] or no [names], the systems run bare, and nothing is called or allocated.
 */
internal fun runMarked(
    systems: List<System>,
    names: List<String>?,
    observer: SceneSystemObserver?,
    outer: String,
    world: World,
    delta: Float,
    unscaledDelta: Float = delta,
) {
    if (observer == null || names == null || systems.isEmpty()) {
        // By index: forEach takes an iterator, a small allocation every frame.
        for (index in systems.indices) {
            val sys = systems[index]
            sys.update(world, if (sys is UnscaledSystem) unscaledDelta else delta)
        }
        return
    }
    observer.after(outer)
    for (index in systems.indices) {
        val name = names[index]
        val sys = systems[index]
        observer.before(name)
        sys.update(world, if (sys is UnscaledSystem) unscaledDelta else delta)
        observer.after(name)
    }
    observer.before(outer)
}
