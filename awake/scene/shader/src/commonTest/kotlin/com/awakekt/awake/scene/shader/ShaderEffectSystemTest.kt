/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.asset.shaderdocument.ShaderDocuments
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ShaderEffectSystemTest {
    private val sky = ShaderDocuments.compile(ShaderEffectFixtures.SKY)
    private val pool = ShaderDocuments.compile(ShaderEffectFixtures.POOL)
    private val pixel = TextureAsset(ByteArray(4) { -1 }, width = 1, height = 1)
    private val assets = ShaderEffectAssets(
        documents = mapOf("sky.shader.json" to sky, "pool.shader.json" to pool),
        textures = mapOf("ripples.png" to pixel),
    )
    private val world = World()
    private val host = RecordingHost()
    private val system = ShaderEffectSystem(host, assets)

    private fun spawn(effect: SceneShaderEffect, transform: Transform? = null): Entity =
        world.create().also { entity ->
            world.add(entity, ShaderEffectSource(effect))
            transform?.let { world.add(entity, it) }
        }

    private fun frames(count: Int, delta: Float = 0.25f) = repeat(count) { system.update(world, delta) }

    @Test
    fun anEffectIsAttachedOnceAndDrawsWithItsParameters() {
        spawn(SceneShaderEffect("sky.shader.json", parameters = mapOf("tint" to listOf(0.9f, 0.1f, 0.1f, 1f))))

        frames(3)

        val frame = host.live.single().drawFrame()
        assertTrue(frame.drew)
        assertEquals(0.9f, frame.parameter(0))
        assertEquals(1, host.attachments.size, "one attach, however many frames")
    }

    /** A frame context has no clock, so the effect keeps its own: it advances by each frame's delta. */
    @Test
    fun theEffectsClockRunsOnTheFramesDelta() {
        spawn(SceneShaderEffect("sky.shader.json"))

        frames(4, delta = 0.25f)

        val frame = host.live.single().drawFrame()
        assertEquals(1f, frame.time)
        assertEquals(0.25f, frame.delta)
    }

    @Test
    fun aPlaneFollowsItsNodesTransform() {
        val transform = Transform(position = Vec3f(3f, 0f, 0f))
        spawn(SceneShaderEffect("pool.shader.json", textures = mapOf("ripples" to "ripples.png")), transform)
        frames(2)
        assertEquals(3f, host.live.single().drawFrame().modelTranslationX)

        transform.position.x = 7f
        frames(1)

        assertEquals(7f, host.live.single().drawFrame().modelTranslationX, "this frame's position, not last frame's")
    }

    @Test
    fun aDisabledEffectDrawsNothingAndKeepsItsClock() {
        val entity = spawn(SceneShaderEffect("sky.shader.json", enabled = false))
        frames(2)
        assertFalse(host.live.single().drawFrame().drew)

        world.add(entity, ShaderEffectSource(SceneShaderEffect("sky.shader.json", enabled = true)))
        frames(1)

        val frame = host.live.single().drawFrame()
        assertTrue(frame.drew)
        assertEquals(0.75f, frame.time)
        assertEquals(1, host.attachments.size, "enabling it is not a new attach")
    }

    @Test
    fun newParametersAreWrittenInPlaceWithoutAttachingAgain() {
        val entity = spawn(SceneShaderEffect("sky.shader.json"))
        frames(2)

        world.add(entity, ShaderEffectSource(SceneShaderEffect("sky.shader.json", parameters = mapOf("tint" to listOf(0f, 1f, 0f, 1f)))))
        frames(1)

        assertEquals(1, host.attachments.size)
        assertEquals(0f, host.live.single().drawFrame().parameter(0))
    }

    @Test
    fun anotherDocumentIsAttachedAndTheOldOneDetachedOnlyOnceTheNewOneIsIn() {
        val entity = spawn(SceneShaderEffect("sky.shader.json"))
        frames(2)
        val first = host.live.single()

        world.add(entity, ShaderEffectSource(SceneShaderEffect("pool.shader.json", textures = mapOf("ripples" to "ripples.png"))))
        frames(2)

        assertTrue(first.detached)
        assertNotSame(first, host.live.single())
        assertEquals(2, host.attachments.size)
    }

    /** As the sky does: a failed attach leaves the effect that was drawing. */
    @Test
    fun aFailedAttachKeepsTheEffectThatWasDrawing() {
        val entity = spawn(SceneShaderEffect("sky.shader.json"))
        frames(2)
        val first = host.live.single()

        host.refuseNext = true
        world.add(entity, ShaderEffectSource(SceneShaderEffect("pool.shader.json", textures = mapOf("ripples" to "ripples.png"))))
        frames(2)

        assertSame(first, host.live.single())
        assertFalse(first.detached)
    }

    @Test
    fun anEffectThatDoesNotMatchItsDocumentIsNotDrawn() {
        spawn(SceneShaderEffect("sky.shader.json", parameters = mapOf("tnit" to listOf(1f, 1f, 1f, 1f))))
        spawn(SceneShaderEffect("pool.shader.json"))
        spawn(SceneShaderEffect("missing.shader.json"))

        frames(2)

        assertEquals(emptyList(), host.attachments, "a misspelt parameter, a missing texture and a missing document")
    }

    /** Live editing: parameters that stop matching hide the effect until they match again. */
    @Test
    fun parametersThatStopMatchingHideTheEffectUntilTheyMatchAgain() {
        val entity = spawn(SceneShaderEffect("sky.shader.json"))
        frames(2)

        world.add(entity, ShaderEffectSource(SceneShaderEffect("sky.shader.json", parameters = mapOf("tint" to listOf(1f)))))
        frames(1)
        assertFalse(host.live.single().drawFrame().drew)

        world.add(entity, ShaderEffectSource(SceneShaderEffect("sky.shader.json", parameters = mapOf("tint" to listOf(1f, 1f, 1f, 1f)))))
        frames(1)
        assertTrue(host.live.single().drawFrame().drew)
    }

    /** Live preview: a recompiled document replaces only the effects that use it. */
    @Test
    fun newAssetsReattachOnlyTheEffectsWhoseFilesChanged() {
        spawn(SceneShaderEffect("sky.shader.json"))
        spawn(SceneShaderEffect("pool.shader.json", textures = mapOf("ripples" to "ripples.png")))
        frames(2)
        val (skyBefore, poolBefore) = host.live

        system.assets = ShaderEffectAssets(
            documents = assets.documents + ("sky.shader.json" to ShaderDocuments.compile(ShaderEffectFixtures.SKY)),
            textures = assets.textures,
        )
        frames(2)

        assertTrue(skyBefore.detached)
        assertFalse(poolBefore.detached)
        assertEquals(3, host.attachments.size)
    }

    @Test
    fun aRemovedEntityOrComponentIsDetachedAndReleaseDetachesTheRest() {
        val gone = spawn(SceneShaderEffect("sky.shader.json"))
        val stripped = spawn(SceneShaderEffect("sky.shader.json"))
        spawn(SceneShaderEffect("sky.shader.json"))
        frames(2)

        world.destroy(gone)
        world.remove<ShaderEffectSource>(stripped)
        frames(1)
        assertEquals(1, host.live.size)

        system.release()
        assertEquals(emptyList(), host.live)
    }

    @Test
    fun withNoHostNothingIsAttachedAndNothingFails() {
        val headless = ShaderEffectSystem(host = null, assets)
        spawn(SceneShaderEffect("sky.shader.json"))

        repeat(3) { headless.update(world, 0.1f) }
        headless.release()
    }
}
