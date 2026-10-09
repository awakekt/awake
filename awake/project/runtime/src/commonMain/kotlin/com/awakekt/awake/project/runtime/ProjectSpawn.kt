/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SpawnedNode
import com.awakekt.awake.scene.runtime.spawn

/**
 * Puts [node] into the running scene of [project], as [spawn] does, and starts its skinned glTF models
 * playing their first clip on a loop, as [runProject] starts the scene's own. Its models must be among
 * those [project] loaded, for example because the scene places them too. [SpawnedNode.despawn] takes it out.
 */
fun SceneAppLifecycleRuntime.spawn(project: LoadedProject, node: SceneNode): SpawnedNode {
    val spawned = spawn(node)
    startSkinnedAnimations(project.models, spawned.entities.toSet())
    return spawned
}
