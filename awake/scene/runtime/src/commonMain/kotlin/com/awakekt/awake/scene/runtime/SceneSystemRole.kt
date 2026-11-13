/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

/**
 * What a system is for, so a host plans only the ones it needs. The set is closed and has only the
 * members something uses today; a member is added when a system needs it.
 */
enum class SceneSystemRole {
    /**
     * Part of the simulation: it changes state the game's rules read, or may. Every host runs it. This is
     * the role of a system that was not given one, so such a system plans where it always did.
     */
    Simulation,

    /**
     * Only for a player who sees or hears the scene: local input, drawing, effects and sound. A host that
     * runs the simulation alone, such as a dedicated server, leaves it out. A system is marked this only
     * when no [Simulation] system reads what it writes.
     */
    Presentation,
}

/** What a host runs, as [SceneSystemRole]s: the other half of the filter that plans a scene's systems. */
enum class SceneHostRole {
    /** A host a player plays on: runs [SceneSystemRole.Simulation] and [SceneSystemRole.Presentation] systems. */
    Full,

    /** A dedicated server or a headless simulation: runs [SceneSystemRole.Simulation] systems only. */
    SimulationOnly,
    ;

    /** Whether a host with this role runs a system with [role]. */
    fun runs(role: SceneSystemRole): Boolean = this == Full || role == SceneSystemRole.Simulation
}
