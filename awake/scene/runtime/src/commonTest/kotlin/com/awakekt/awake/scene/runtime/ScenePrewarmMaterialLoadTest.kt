/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.render.material.Material
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A material its resolver loads first ([SceneAssetResolver.loadMaterial]), such as images to decode
 * again off the frame thread (#1619), is made only once that load finishes: the frame thread never
 * makes it while its images still need decoding.
 */
class ScenePrewarmMaterialLoadTest : PrewarmFixture(listOf(DecodingMaterials())) {
    private val materials = extraResolvers.single() as DecodingMaterials

    private val painted = SceneAssetReferences(renderables = setOf(SceneRenderableKey("hull", PAINT)))

    @Test
    fun aMaterialIsMadeOnlyOnceItsImagesAreDecoded() {
        val prewarm = runtime.prewarm(painted)
        library.runPrewarms()
        val load = materials.loads.single()
        assertSame(runtime.session.coroutineScope, load.scope, "it decodes in the session's scope")
        assertFalse(prewarm.isReady)
        assertEquals(0, materials.made, "nothing made while its images still need decoding")

        materials.decoded = true
        load.done = true
        library.runPrewarms()

        assertTrue(prewarm.isReady)
        assertEquals(1, materials.made)
        assertEquals(0, materials.madeUndecoded, "the frame thread decoded nothing")
        assertTrue(load.closed, "and the prewarm stops waiting for a load it is done with")
        prewarm.close()
    }

    @Test
    fun aMaterialWithNothingToDecodeIsMadeAtOnce() {
        materials.decoded = true
        val prewarm = runtime.prewarm(painted)
        library.runPrewarms()

        assertTrue(prewarm.isReady)
        assertTrue(materials.loads.isEmpty())
        assertEquals(1, materials.made)
        prewarm.close()
    }

    /** Resolves [PAINT], whose images need decoding until a test says they are [decoded]. */
    class DecodingMaterials : SceneAssetResolver {
        val loads = mutableListOf<ScenePrewarmLoadTest.Load>()
        var decoded = false
        var made = 0
        var madeUndecoded = 0

        override fun canResolveMaterial(name: String): Boolean = name == PAINT

        override fun createMaterial(runtime: SceneAppLifecycleRuntime, name: String): Material {
            made++
            if (!decoded) madeUndecoded++
            return PaintMaterial
        }

        override fun loadMaterial(name: String, scope: CoroutineScope): SceneMeshLoad? =
            if (decoded) null else ScenePrewarmLoadTest.Load(scope).also { loads += it }
    }

    private object PaintMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val PAINT = "gltf-material:ships/sloop.gltf"
    }
}
