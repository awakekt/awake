/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.transform.SceneStaticTransform
import com.awakekt.awake.scene.core.transform.StaticTransform
import com.awakekt.awake.scene.document.SceneLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaticTransformBindingTest {
    @Test
    fun aStaticNodeLoadsItsMarkerAndSavesItBack() {
        DefaultSceneComponentResolvers.install()
        val json = """{ "version": 1, "name": "Yard", "nodes": [ { "name": "crate", "components": [ { "component": "static_transform" } ] } ] }"""

        val scene = SceneLoader.decode(json).instantiate()
        val crate = scene.roots.single().entity
        assertTrue(scene.world.has(crate, StaticTransform::class))

        val saved = SceneLoader.decode(SceneLoader.encode(SceneLoader.fromWorld(scene.world, name = "Yard")))
        assertEquals(listOf(SceneStaticTransform), saved.nodes.single().components)
    }
}
