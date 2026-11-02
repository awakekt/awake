/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRegistry
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.core.math.Frustum
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.debug.DebugLineLayout
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.rendering.debug.DebugLineBuffer
import com.awakekt.awake.scene.rendering.debug.DebugVisualizationSystem
import com.awakekt.awake.scene.rendering.debug.WorldDebugSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DebugSourceLinesTest {
    @Test
    fun sourceLinesAndToggleLinesReachTheRendererInOneCall() {
        val world = worldWithCamera()
        world.add(world.create(), WorldDebugSettings(showFrustum = true, frustumTargetEntityId = cameraId))
        val registry = DebugRegistry(setOf("cells"))
        registry.register(LineSource("cells", count = 3))
        val renderer = RecordingRenderer()

        DebugVisualizationSystem(renderer, { null }, registry).update(world, FRAME)

        assertEquals(1, renderer.calls, "one call, so neither set of lines wipes out the other")
        assertEquals(Frustum.EDGES.size + 3, renderer.last?.size)
        assertEquals(SOURCE_COLOR, renderer.last?.last()?.color, "the sources' lines come after the toggles'")
    }

    @Test
    fun sourceLinesDrawWithNoDebugSettingsInTheWorld() {
        val registry = DebugRegistry(setOf("cells"))
        registry.register(LineSource("cells", count = 2))
        val renderer = RecordingRenderer()

        DebugVisualizationSystem(renderer, { null }, registry).update(worldWithCamera(), FRAME)

        assertEquals(2, renderer.last?.size)
    }

    @Test
    fun aSourceThatIsOffIsNeverAskedAndNothingIsDrawn() {
        val registry = DebugRegistry(setOf("net"))
        val off = LineSource("cells", count = 2)
        registry.register(off)
        val renderer = RecordingRenderer()

        DebugVisualizationSystem(renderer, { null }, registry).update(worldWithCamera(), FRAME)

        assertEquals(0, off.calls)
        assertEquals(0, renderer.calls)
    }

    @Test
    fun withNoSourceOnTheTogglesDrawAsTheyDidBefore() {
        val world = worldWithCamera()
        world.add(world.create(), WorldDebugSettings(showFrustum = true, frustumTargetEntityId = cameraId))
        val withOffRegistry = RecordingRenderer()
        val withoutRegistry = RecordingRenderer()

        DebugVisualizationSystem(withOffRegistry, { null }, DebugRegistry.Off).update(world, FRAME)
        DebugVisualizationSystem(withoutRegistry).update(world, FRAME)

        assertEquals(withoutRegistry.last, withOffRegistry.last)
        assertEquals(Frustum.EDGES.size, withOffRegistry.last?.size)
    }

    @Test
    fun steadyFramesReuseEverySegment() {
        val buffer = DebugLineBuffer()
        val source = LineSource("cells", count = 4)
        val registry = DebugRegistry(setOf("cells")).also { it.register(source) }

        buffer.clear()
        buffer.addSources(registry)
        val first = buffer.lines.toList()
        source.offset = 5f
        buffer.clear()
        buffer.addSources(registry)

        assertEquals(4, buffer.lines.size)
        for (i in first.indices) assertSame(first[i], buffer.lines[i], "segment $i is reused, not made again")
        assertEquals(5f, buffer.lines[0].start.x, "with this frame's points")
    }

    @Test
    fun aNewColourAtAPlaceMakesANewSegmentThere() {
        val buffer = DebugLineBuffer()
        buffer.line(Vec3f(), Vec3f(1f, 0f, 0f), Color.White)
        val white = buffer.lines[0]

        buffer.clear()
        buffer.line(Vec3f(), Vec3f(1f, 0f, 0f), SOURCE_COLOR)

        assertNotSame(white, buffer.lines[0])
        assertEquals(SOURCE_COLOR, buffer.lines[0].color)
        assertEquals(Color.White, white.color, "a segment's colour never changes under a caller holding it")
    }

    @Test
    fun linesPastTheLimitAreDroppedAndCounted() {
        val buffer = DebugLineBuffer(maxLines = 3)
        val built = List(2) { LineSegment(Vec3f(), Vec3f(), Color.White) }

        buffer.addAll(built)
        repeat(4) { buffer.line(Vec3f(), Vec3f(), SOURCE_COLOR) }

        assertEquals(3, buffer.lines.size)
        assertEquals(3, buffer.dropped)
        buffer.clear()
        assertEquals(0, buffer.dropped)
        assertTrue(buffer.lines.isEmpty())
    }

    @Test
    fun theLimitIsTheRenderersCeilingAtMost() {
        assertEquals(DebugLineLayout.MAX_LINES_CEILING, DebugLineBuffer().maxLines)
        assertFailsWith<IllegalArgumentException> { DebugLineBuffer(DebugLineLayout.MAX_LINES_CEILING + 1) }
    }

    @Test
    fun noLinesFromAnOnSourceDrawsNothing() {
        val registry = DebugRegistry(setOf("cells"))
        registry.register(LineSource("cells", count = 0))
        val renderer = RecordingRenderer()

        DebugVisualizationSystem(renderer, { null }, registry).update(worldWithCamera(), FRAME)

        assertEquals(0, renderer.calls)
        assertNull(renderer.last)
    }

    private var cameraId = -1

    private fun worldWithCamera(): World {
        val world = World()
        val camera = world.create()
        world.add(camera, Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(), fovYRadians = 1f, near = 0.1f, far = 100f)))
        cameraId = camera.id
        return world
    }

    private class RecordingRenderer : NoopRenderer() {
        var calls = 0
        var last: List<LineSegment>? = null

        override fun drawDebugLines(lines: List<LineSegment>) {
            calls++
            last = lines.toList()
        }
    }

    private class LineSource(override val id: String, private val count: Int) : DebugSource {
        override val title = id
        var calls = 0
        var offset = 0f
        private val a = Vec3f()
        private val b = Vec3f()

        override fun rows(out: DebugRows) = Unit

        override fun lines(out: DebugLines) {
            calls++
            for (i in 0 until count) {
                a.set(offset + i, 0f, 0f)
                b.set(offset + i, 1f, 0f)
                out.line(a, b, SOURCE_COLOR)
            }
        }
    }

    private companion object {
        const val FRAME = 1f / 60f
        val SOURCE_COLOR = Color(r = 1f, g = 0f, b = 1f, a = 1f)
    }
}
