/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.scene.binding.Scene
import com.awakekt.awake.scene.rendering.mesh.InstancedMeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime

private const val GRID_SIDE = 16
private const val FLOORS = 4
private const val SPACING = 1.6f
private const val CUBE_Y = 0.5f

/** A 16x16x4 building of 1,024 cubes exercises one real instanced draw rather than a hand-written
 * list of entities. [InstancedMeshRenderer] isn't an authorable [com.awakekt.awake.scene.runtime
 * .SceneComponent] yet -- the instanced-cubes example's scene document authors an empty,
 * named placeholder node instead (`instanced-cubes`, no components), same "author a named
 * node, attach the state a scene document can't express in `onActivated`" shape
 * [SkinnedExampleDriver.attachPose] already uses for its joint palette. Reuses the existing
 * `"cube"`/`"lit-shadow"` named assets (same mesh/material [samples.studio.StudioModule]
 * already registers for the rotating-cube example) -- one GPU draw call for the whole grid. */
internal object InstancedCubesExampleDriver {
    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        val node = instance.roots.find { it.name == "instanced-cubes" } ?: return
        val mesh = runtime.requireMesh("cube")
        val material = runtime.requireMaterial("lit-shadow")
        runtime.world.add(node.entity, InstancedMeshRenderer(mesh, material, gridTransforms()))
    }

    /** Exposed to the showcase regression test so the central empty cell is an explicit contract. */
    internal fun gridTransforms(): List<Mat4> {
        val offset = (GRID_SIDE - 1) * SPACING / 2f
        val transforms = mutableListOf<Mat4>()
        for (floor in 0 until FLOORS) {
            for (x in 0 until GRID_SIDE) {
                for (z in 0 until GRID_SIDE) {
                    transforms += Mat4().translate(
                        x * SPACING - offset,
                        CUBE_Y + floor * SPACING,
                        z * SPACING - offset,
                    )
                }
            }
        }
        return transforms
    }
}
