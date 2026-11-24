/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.worldtext

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.testing.NoopRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [WorldTextBatch]: quads uploaded once per layout, transparent draws, and texts no frame draws freed. */
class WorldTextBatchTest {
    private class CountingRenderer : NoopRenderer() {
        var created = 0
        var destroyed = 0
        var waitIdles = 0

        override fun waitIdle() {
            waitIdles++
        }

        override fun createMesh(geometry: MeshGeometry): Mesh {
            created++
            return object : Mesh {
                override val format = geometry.format
                override val sizeBytes: Long = 0
                override fun destroy() {
                    destroyed++
                }
            }
        }
    }

    private val camera = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)

    @Test
    fun aTextIsUploadedOnceAndDrawnTransparentEveryFrame() {
        val renderer = CountingRenderer()
        val batch = WorldTextBatch(renderer)
        val sign = WorldText("Harbor Town")
        val draws = ArrayList<RenderDrawCommand>()
        repeat(3) {
            draws.clear()
            batch.collect(sign, Mat4(), camera, draws)
            batch.releaseUnseen()
        }

        assertEquals(1, renderer.created)
        assertEquals(1, draws.size)
        assertTrue(draws.single().transparent)
        assertTrue(!draws.single().castsShadows)
    }

    @Test
    fun changingTheTextRetiresOldMeshesWithoutMidFrameWaitIdle() {
        val renderer = CountingRenderer()
        val batch = WorldTextBatch(renderer, retireFrames = 2)
        val sign = WorldText("Market")
        val label = WorldText("Supplies", billboard = true)
        val draws = ArrayList<RenderDrawCommand>()
        batch.collect(sign, Mat4(), camera, draws)
        batch.collect(label, Mat4(), camera, draws)
        batch.releaseUnseen()

        sign.text = "Harbor Market"
        batch.collect(sign, Mat4(), camera, draws)
        batch.releaseUnseen()

        assertEquals(3, renderer.created, "two texts, and the changed one again")
        assertEquals(0, renderer.waitIdles, "mid-frame reupload and unseen retirement must never call waitIdle")
        assertEquals(0, renderer.destroyed, "retired meshes stay alive while in-flight frames may still read them")
        assertEquals(2, batch.retiringMeshCount, "changed text's first quads and unseen label are retiring")

        // One more frame: still inside the 2-frame retirement window.
        batch.collect(sign, Mat4(), camera, draws)
        batch.releaseUnseen()
        assertEquals(0, renderer.destroyed)
        assertEquals(2, batch.retiringMeshCount)

        // Second frame after retirement: both retired meshes are destroyed without waitIdle.
        batch.collect(sign, Mat4(), camera, draws)
        batch.releaseUnseen()
        assertEquals(2, renderer.destroyed, "both retired meshes are destroyed once out of flight")
        assertEquals(0, batch.retiringMeshCount)
        assertEquals(0, renderer.waitIdles)

        batch.close()
        assertEquals(3, renderer.destroyed)
        assertEquals(1, renderer.waitIdles, "close waits idle once before destroying remaining meshes and atlases")
    }

    @Test
    fun closeImmediatelyDestroysPendingRetiringMeshesAndZeroRetireFramesDestroysAtOnce() {
        val deferredRenderer = CountingRenderer()
        val deferred = WorldTextBatch(deferredRenderer)
        val sign = WorldText("Dock")
        val draws = ArrayList<RenderDrawCommand>()
        deferred.collect(sign, Mat4(), camera, draws)
        deferred.releaseUnseen()
        deferred.release(sign)
        assertEquals(0, deferredRenderer.waitIdles)
        assertEquals(1, deferred.retiringMeshCount)
        deferred.close()
        assertEquals(1, deferredRenderer.destroyed)
        assertEquals(0, deferred.retiringMeshCount)
        assertEquals(1, deferredRenderer.waitIdles)

        val immediateRenderer = CountingRenderer()
        val immediate = WorldTextBatch(immediateRenderer, retireFrames = 0)
        val immediateSign = WorldText("Pier")
        immediate.collect(immediateSign, Mat4(), camera, draws)
        immediate.releaseUnseen()
        immediateSign.text = "North Pier"
        immediate.collect(immediateSign, Mat4(), camera, draws)
        immediate.releaseUnseen()
        assertEquals(1, immediateRenderer.destroyed)
        assertEquals(1, immediateRenderer.waitIdles)
        immediate.close()
        assertEquals(2, immediateRenderer.destroyed)
    }

    @Test
    fun rapidEditsDrainRetiringMeshesBoundedWithoutMidFrameWaitIdle() {
        val renderer = CountingRenderer()
        val batch = WorldTextBatch(renderer)
        val counter = WorldText("Tick 0")
        val draws = ArrayList<RenderDrawCommand>()

        // Frame 0: initial upload (mesh 0).
        batch.collect(counter, Mat4(), camera, draws)
        batch.releaseUnseen()
        assertEquals(1, renderer.created)
        assertEquals(0, renderer.destroyed)

        // Frames 1..10: text changes every frame, retiring one mesh per frame and growing the scratch array past 8.
        for (tick in 1..10) {
            counter.text = "Tick $tick"
            draws.clear()
            batch.collect(counter, Mat4(), camera, draws)
            batch.releaseUnseen()
            assertEquals(0, renderer.waitIdles, "rapid text updates must never stall mid-frame")
            // Default retireFrames = 3: frames 1..3 accumulate up to 3 retiring meshes; from frame 4 onward each frame drains 1.
            val expectedDestroyed = (tick - 3).coerceAtLeast(0)
            assertEquals(expectedDestroyed, renderer.destroyed, "tick $tick should have destroyed $expectedDestroyed meshes")
            assertEquals(minOf(tick, 3), batch.retiringMeshCount, "retiring queue must remain bounded at <= 3")
        }

        batch.close()
        assertEquals(11, renderer.created)
        assertEquals(11, renderer.destroyed, "every uploaded mesh must be destroyed with zero leaks")
        assertEquals(0, batch.retiringMeshCount)
        assertEquals(1, renderer.waitIdles)
    }

    @Test
    fun releaseThenRecollectBeforeRetirementKeepsNewMeshAndRetiresOld() {
        val renderer = CountingRenderer()
        val batch = WorldTextBatch(renderer, retireFrames = 2)
        val sign = WorldText("Harbor")
        val draws = ArrayList<RenderDrawCommand>()

        batch.collect(sign, Mat4(), camera, draws)
        batch.releaseUnseen()
        batch.release(sign)
        assertEquals(1, batch.retiringMeshCount)
        assertEquals(0, renderer.destroyed)

        // Frame 1: re-collect immediately uploads a fresh mesh while the old one awaits retirement.
        draws.clear()
        batch.collect(sign, Mat4(), camera, draws)
        batch.releaseUnseen()
        assertEquals(2, renderer.created)
        assertEquals(0, renderer.destroyed)

        // Frame 2: still inside the 2-frame window (retireAt = 1 + 2 = 3).
        draws.clear()
        batch.collect(sign, Mat4(), camera, draws)
        batch.releaseUnseen()
        assertEquals(0, renderer.destroyed)
        assertEquals(1, batch.retiringMeshCount)

        // Frame 3: retirement window elapses, destroying only the previously released mesh.
        draws.clear()
        batch.collect(sign, Mat4(), camera, draws)
        batch.releaseUnseen()
        assertEquals(1, renderer.destroyed, "only the previously released mesh should be destroyed")
        assertEquals(0, batch.retiringMeshCount)
        assertEquals(0, renderer.waitIdles)

        batch.close()
        assertEquals(2, renderer.destroyed)
    }

    @Test
    fun immediateModeMultipleUnseenTextsWaitIdleOnlyOnce() {
        val renderer = CountingRenderer()
        val batch = WorldTextBatch(renderer, retireFrames = 0)
        val draws = ArrayList<RenderDrawCommand>()
        val signs = listOf(WorldText("A"), WorldText("B"), WorldText("C"))
        signs.forEach { batch.collect(it, Mat4(), camera, draws) }
        batch.releaseUnseen()

        // Drop all three in one frame: immediate mode waits for the GPU once for the whole batch.
        batch.releaseUnseen()
        assertEquals(3, renderer.destroyed)
        assertEquals(1, renderer.waitIdles, "multiple unseen texts in one frame should only call waitIdle once")
    }
}
