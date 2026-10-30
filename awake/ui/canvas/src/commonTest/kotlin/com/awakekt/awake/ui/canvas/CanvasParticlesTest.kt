/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clip
import com.awakekt.awake.compose.ui.graphics.CircleShape
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.particles.ParticleSpawnRate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CanvasParticlesTest {
    private val red = Color(1f, 0f, 0f, 1f)
    private val green = Color(0f, 1f, 0f, 1f)
    private val blue = Color(0f, 0f, 1f, 1f)

    @Test
    fun getSetAndRemoveWireCanvasParticlesAndOwnerCleanly() {
        val element = CanvasElement().apply {
            kind = CanvasElementKind.Bar
            width = 120f
            height = 20f
        }
        val emitter = CanvasParticles(maxParticles = 16, random = Random(7))
        val beforeVersion = element.componentsVersion

        element.set(emitter)
        assertSame(emitter, element.particles)
        assertSame(emitter, element.get<CanvasParticles>())
        assertSame(element, emitter.owner)
        assertNotEquals(beforeVersion, element.componentsVersion)
        assertTrue(emitter in element.components())

        val removed = element.remove(CanvasParticles::class)
        assertSame(emitter, removed)
        assertNull(element.particles)
        assertNull(emitter.owner)
    }

    @Test
    fun behindContentAndOverContentOrderRelativeToBackgroundWidgetAndChildren() {
        val overButton = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            anchor = CanvasAnchor.TopLeft
            width = 80f
            height = 40f
            background = "#0000FF"
            particles = CanvasParticles(maxParticles = 8, random = Random(1)).apply {
                placement = CanvasParticlePlacement.OverContent
                spawnRate = 0f
                speed = 0f
                size = 8f
                tint = red
            }
        }
        val childPanel = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            anchor = CanvasAnchor.TopLeft
            width = 20f
            height = 20f
            background = "#00FF00"
        }
        overButton.particles?.emit(1)

        val host = ComposeHost()
        val overItems = listOf(
            CanvasItem(1, overButton),
            CanvasItem(2, childPanel, parent = overButton),
        )
        val overOut = host.frame(FrameInput(200, 120, deltaSeconds = 0f)) {
            CanvasOverlay(overItems)
        }
        val overBackIdx = overOut.primitives.indexOfFirst { it is UiDrawPrimitive.Quad && it.color == blue }
        val overTexIdx = overOut.primitives.indexOfFirst { it is UiDrawPrimitive.Texture }
        val overChildIdx = overOut.primitives.indexOfFirst { it is UiDrawPrimitive.Quad && it.color == green }
        assertTrue(overBackIdx in 0 until overTexIdx, "OverContent must draw after element background")
        assertTrue(overTexIdx < overChildIdx, "OverContent must draw before child elements")

        overButton.particles?.placement = CanvasParticlePlacement.BehindContent
        val behindOut = host.frame(FrameInput(200, 120, deltaSeconds = 0f)) {
            CanvasOverlay(overItems)
        }
        val behindTexIdx = behindOut.primitives.indexOfFirst { it is UiDrawPrimitive.Texture }
        val behindBackIdx = behindOut.primitives.indexOfFirst { it is UiDrawPrimitive.Quad && it.color == blue }
        assertTrue(behindTexIdx in 0 until behindBackIdx, "BehindContent must draw before element background")
    }

    @Test
    fun visibleEmitterStepsEvenOffscreenAndPausesWhileHidden() {
        val element = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            anchor = CanvasAnchor.TopLeft
            offsetX = 4000f
            offsetY = 10f
            width = 40f
            height = 20f
            particles = CanvasParticles(maxParticles = 16, random = Random(2)).apply {
                spawnRate = 0f
                lifetime = 1f
                speed = 100f
                directionDegrees = 0f
                spreadDegrees = 0f
            }
        }
        val emitter = checkNotNull(element.particles)
        emitter.emit(1)

        val host = ComposeHost()
        val items = listOf(CanvasItem(1, element))
        host.frame(FrameInput(200, 120, deltaSeconds = 0.1f)) {
            CanvasOverlay(items)
        }
        assertEquals(10f, emitter.simulation.posX[0], 1e-3f, "Off-screen visible element must still step")

        element.visible = false
        host.frame(FrameInput(200, 120, deltaSeconds = 0.1f)) {
            CanvasOverlay(items)
        }
        assertEquals(10f, emitter.simulation.posX[0], 1e-3f, "Hidden element must pause stepping")
    }

    @Test
    fun localSpaceFalseTrailsInRootCanvasDpAndScalesUniformlyWhenCanvasScaleChangesMidFlight() {
        val element = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            anchor = CanvasAnchor.TopLeft
            offsetX = 40f
            offsetY = 30f
            width = 20f
            height = 10f
            particles = CanvasParticles(maxParticles = 8, random = Random(3)).apply {
                localSpace = false
                anchor = CanvasAnchor.Center
                spawnRate = 0f
                lifetime = 5f
                speed = 0f
                size = 8f
            }
        }
        val emitter = checkNotNull(element.particles)
        val items = listOf(CanvasItem(1, element))
        val host = ComposeHost()
        var currentScale = 1f

        // Frame 1: places element at (40..60, 30..40) dp; anchor Center = (50, 35) dp.
        host.frame(FrameInput(400, 300, deltaSeconds = 0f)) {
            CanvasOverlay(items, scale = currentScale)
        }
        emitter.emit(1)
        assertEquals(50f, emitter.simulation.posX[0], 1e-3f)
        assertEquals(35f, emitter.simulation.posY[0], 1e-3f)

        // Frame 2: move element to offsetX = 120 dp while keeping scale = 1f -> particle stays at (50, 35) px!
        element.offsetX = 120f
        val movedAt1x = host.frame(FrameInput(400, 300, deltaSeconds = 0.05f)) {
            CanvasOverlay(items, scale = currentScale)
        }
        val tex1x = movedAt1x.primitives.filterIsInstance<UiDrawPrimitive.Texture>().single()
        assertEquals(50f, tex1x.x + tex1x.w * 0.5f, 1e-2f, "Detached particle must stay at 50 px when element moves")
        assertEquals(35f, tex1x.y + tex1x.h * 0.5f, 1e-2f)
        assertEquals(8f, tex1x.h, 1e-2f)

        // Frame 3: mid-flight UI scale doubles to 2f -> in-flight particle at (50, 35) dp scales to (100, 70) px, size 16 px!
        currentScale = 2f
        val scaled2x = host.frame(FrameInput(400, 300, deltaSeconds = 0.05f)) {
            CanvasOverlay(items, scale = currentScale)
        }
        val tex2x = scaled2x.primitives.filterIsInstance<UiDrawPrimitive.Texture>().single()
        assertEquals(100f, tex2x.x + tex2x.w * 0.5f, 1e-2f, "In-flight particle must scale to 100 px under scale = 2f")
        assertEquals(70f, tex2x.y + tex2x.h * 0.5f, 1e-2f, "In-flight particle must scale to 70 px under scale = 2f")
        assertEquals(16f, tex2x.h, 1e-2f, "In-flight particle quad height must scale to 16 px under scale = 2f")
    }

    @Test
    fun elementRectAndElementEdgeShapesUseElementWidthAndHeight() {
        val bar = CanvasElement().apply {
            kind = CanvasElementKind.Bar
            width = 160f
            height = 24f
            particles = CanvasParticles(maxParticles = 64, random = Random(9)).apply {
                shape = CanvasParticleShape(kind = CanvasParticleShapeKind.ElementEdge)
                spawnRate = 0f
                speed = 0f
            }
        }
        val emitter = checkNotNull(bar.particles)
        assertEquals(64, emitter.emit(64))
        for (i in 0 until 64) {
            val px = emitter.simulation.posX[i]
            val py = emitter.simulation.posY[i]
            val onVerticalEdge = kotlin.math.abs(kotlin.math.abs(px) - 80f) < 1e-3f && py in -12f..12f
            val onHorizontalEdge = kotlin.math.abs(kotlin.math.abs(py) - 12f) < 1e-3f && px in -80f..80f
            assertTrue(onVerticalEdge || onHorizontalEdge, "Particle ($px, $py) must lie on 160x24 perimeter")
        }
    }

    @Test
    fun clippedRespectsEnclosingMaskAndClippedFalseEscapesIt() {
        val element = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            anchor = CanvasAnchor.TopLeft
            width = 30f
            height = 30f
            particles = CanvasParticles(maxParticles = 4, random = Random(5)).apply {
                clipped = true
                offsetX = 60f
                offsetY = 0f
                spawnRate = 0f
                speed = 0f
                size = 8f
            }
        }
        val emitter = checkNotNull(element.particles)
        emitter.emit(1)
        val items = listOf(CanvasItem(1, element))
        val host = ComposeHost()

        // In 200x120, CircleShape spans x = 40..160 so particle at (75, 15) is inside and drawn.
        val unculledOut: FrameOutput = host.frame(FrameInput(200, 120, deltaSeconds = 0f)) {
            CanvasOverlay(items, modifier = Modifier.clip(CircleShape))
        }
        assertEquals(1, unculledOut.primitives.filterIsInstance<UiDrawPrimitive.Texture>().size)

        // In 40x40, CircleShape spans 0..40 so particle at x = 75 px is culled when clipped = true.
        val clippedFrameOut = host.frame(FrameInput(40, 40, deltaSeconds = 0f)) {
            CanvasOverlay(items, modifier = Modifier.clip(CircleShape))
        }
        assertTrue(clippedFrameOut.primitives.none { it is UiDrawPrimitive.Texture })

        // With clipped = false, particle at x = 75 px escapes the 40x40 CircleShape clip!
        emitter.clipped = false
        val escapedOut = host.frame(FrameInput(40, 40, deltaSeconds = 0f)) {
            CanvasOverlay(items, modifier = Modifier.clip(CircleShape))
        }
        assertEquals(1, escapedOut.primitives.filterIsInstance<UiDrawPrimitive.Texture>().size)
    }

    @Test
    fun canvasBurstPoolSkipsAndCountsOverflowAndAutoRecyclesSlotsOnExpiry() {
        val pool = CanvasBurstPool(maxSlots = 2, slotMaxParticles = 8) { Random(it + 1) }
        val host = ComposeHost()

        assertTrue(pool.spawn(20f, 30f, count = 4) { it.lifetime = 0.2f })
        assertTrue(pool.spawn(60f, 30f, count = 4) { it.lifetime = 0.2f })
        assertEquals(2, pool.activeSlotCount)

        assertFalse(pool.spawn(90f, 30f, count = 4) { it.lifetime = 0.2f })
        assertEquals(1L, pool.droppedBurstCount)

        host.frame(FrameInput(200, 120, deltaSeconds = 0.1f)) {
            CanvasOverlay(pool.items)
        }
        assertEquals(2, pool.activeSlotCount)

        host.frame(FrameInput(200, 120, deltaSeconds = 0.1f)) {
            CanvasOverlay(pool.items)
        }
        host.frame(FrameInput(200, 120, deltaSeconds = 0.05f)) {
            CanvasOverlay(pool.items)
        }
        assertEquals(0, pool.activeSlotCount)
        assertTrue(pool.items.all { !it.element.visible })
        assertTrue(pool.spawn(40f, 50f, count = 4) { it.lifetime = 0.2f })
    }

    @Test
    fun resolvePremultipliedBitmapPremultipliesOnceAndSharesCachedInstance() {
        val source = ImageBitmap(
            width = 1,
            height = 1,
            pixels = byteArrayOf(200.toByte(), 100.toByte(), 50.toByte(), 128.toByte()),
        )
        val images = mapOf("fx/glow.png" to source)
        val emitterA = CanvasParticles().apply { texture = "fx/glow.png" }
        val emitterB = CanvasParticles().apply { texture = "fx/glow.png" }

        val cache = PremultipliedImages()

        val first = checkNotNull(resolvePremultipliedBitmap(emitterA, images, cache))
        val second = checkNotNull(resolvePremultipliedBitmap(emitterB, images, cache))
        assertSame(first, second, "Multiple emitters sharing one source bitmap must share one premultiplied upload")
        assertEquals(100, first.pixels[0].toInt() and 0xFF)
        assertEquals(50, first.pixels[1].toInt() and 0xFF)
        assertEquals(25, first.pixels[2].toInt() and 0xFF)
        assertEquals(128, first.pixels[3].toInt() and 0xFF)
        assertEquals(1, cache.size)
    }

    @Test
    fun anOverlaysPremultipliedCopiesAreItsOwnAndGoWhenItsImagesChange() {
        val glow = ImageBitmap(width = 1, height = 1, pixels = byteArrayOf(-1, 0, 0, -128))
        val spark = ImageBitmap(width = 1, height = 1, pixels = byteArrayOf(0, -1, 0, -128))
        val first = mapOf("glow" to glow)
        val overlayA = PremultipliedImages()
        val overlayB = PremultipliedImages()

        val copyA = overlayA.of(first, glow)
        assertSame(copyA, overlayA.of(first, glow))
        assertEquals(0, overlayB.size, "one overlay's copies are not another's")
        overlayB.of(first, glow)
        assertEquals(1, overlayB.size)

        overlayA.of(mapOf("spark" to spark), spark)
        assertEquals(1, overlayA.size, "a new images map drops the copies of the old one")
    }

    @Test
    fun anInvalidBurstCycleFailsWhereItIsSetAndLeavesTheEmitterAsItWas() {
        val emitter = CanvasParticles(maxParticles = 4)
        val valid = CanvasParticleBurstCycle(cycleSeconds = 1f, activeSeconds = 0.5f, burstInterval = 0.1f, burstSize = 2)
        emitter.burstCycle = valid

        assertFailsWith<IllegalArgumentException> {
            emitter.burstCycle = CanvasParticleBurstCycle(cycleSeconds = 1f, activeSeconds = 2f, burstInterval = 0.1f, burstSize = 4)
        }
        assertSame(valid, emitter.burstCycle)
        emitter.step(0.1f) // a later step cannot throw: the bad cycle never got in
    }

    @Test
    fun aFinishedBurstKeepsItsAuthoredEnabledAndEmitStartsItAgain() {
        val emitter = CanvasParticles(maxParticles = 4, random = Random(6)).apply {
            spawnRate = 100f
            burstCount = 2
            lifetime = 0.1f
            speed = 0f
        }
        emitter.step(0.05f)
        assertEquals(2, emitter.liveParticleCount)
        emitter.step(0.2f)

        assertTrue(emitter.isFinished)
        assertTrue(emitter.enabled, "finishing is runtime state; the authored enabled stays on")

        assertEquals(1, emitter.emit(1))
        assertFalse(emitter.isFinished, "emit starts a finished emitter again")
        emitter.step(0.05f)
        assertEquals(1, emitter.liveParticleCount, "the emitted particle steps")
        emitter.step(0.2f)
        assertTrue(emitter.isFinished)

        emitter.reset()
        assertFalse(emitter.isFinished)
    }

    @Test
    fun aZeroOrNegativeBurstCountSpawnsNothingAndFinishes() {
        for (count in listOf(0, -3)) {
            val emitter = CanvasParticles(maxParticles = 4).apply {
                spawnRate = 100f
                burstCount = count
            }
            emitter.step(0.1f)
            assertEquals(0, emitter.liveParticleCount, "burstCount $count spawns nothing")
            assertTrue(emitter.isFinished, "burstCount $count is used up at once")
        }
    }

    @Test
    fun dynamicSpawnRateIsAskedOnlyOnTheContinuousRatePath() {
        var cycleAsks = 0
        var cappedAsks = 0
        val cycled = CanvasParticles(maxParticles = 8).apply {
            burstCycle = CanvasParticleBurstCycle(cycleSeconds = 1f, activeSeconds = 0.5f, burstInterval = 0.1f, burstSize = 1)
            dynamicSpawnRate = ParticleSpawnRate { 50f.also { cycleAsks += 1 } }
        }
        val capped = CanvasParticles(maxParticles = 8).apply {
            spawnRate = 0f
            burstCount = 2
            lifetime = 10f
            dynamicSpawnRate = ParticleSpawnRate { 100f.also { cappedAsks += 1 } }
        }

        cycled.step(0.1f)
        capped.step(0.1f)
        assertEquals(0, cycleAsks, "a burst cycle replaces the rate, so the provider is never asked")
        assertEquals(1, cappedAsks)
        assertEquals(2, capped.liveParticleCount, "the dynamic rate spawns up to burstCount")

        capped.step(0.1f)
        assertEquals(1, cappedAsks, "a used-up burstCount spawns nothing more, so the provider is not asked again")
    }
}
