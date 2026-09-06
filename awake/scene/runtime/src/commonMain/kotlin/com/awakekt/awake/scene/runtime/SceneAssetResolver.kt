/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh

/**
 * Pluggable resolver for dynamic GPU assets. Allows plugins (e.g. glTF, O3D, terrain)
 * to resolve asset names on demand without hardcoding asset names or paths in the scene fixture.
 */
interface SceneAssetResolver {
    fun canResolveMesh(name: String): Boolean = false
    fun createMesh(runtime: SceneAppLifecycleRuntime, name: String): Mesh? = null

    fun canResolveMaterial(name: String): Boolean = false
    fun createMaterial(runtime: SceneAppLifecycleRuntime, name: String): Material? = null
}
