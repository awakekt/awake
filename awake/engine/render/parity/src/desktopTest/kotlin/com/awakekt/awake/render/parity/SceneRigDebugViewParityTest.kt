/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.math.transformPosition
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.render.passes.uniforms.WIREFRAME_EDGE_GREY
import com.awakekt.awake.render.passes.uniforms.debugLayerColor
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.HeadlessRenderSession
import org.junit.AfterClass
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Clay, the joint-weight views, the skinned shaders' debug views and the wireframe overlay, on both
 * backends. As in [SceneDebugViewParityTest], each check also runs against the frame without the
 * view and must fail there.
 */
class SceneRigDebugViewParityTest {

    @Test
    fun clayIsOneGreyWhateverTheSurfaceIsMadeOf() = eachBackend { backend ->
        val clay = ClayPlane.entries.associateWith { kind -> render(backend) { renderClayPlaneScene(kind, RenderDebugView.Clay) } }
        val lit = ClayPlane.entries.associateWith { kind -> render(backend) { renderClayPlaneScene(kind, RenderDebugView.Off) } }
        val reference = clay.getValue(ClayPlane.Untextured)

        val grey = reference.rgb(CENTRE, CENTRE)
        assertTrue(abs(grey.x - grey.y) <= TOLERANCE && abs(grey.y - grey.z) <= TOLERANCE, "$backend: clay is not grey: $grey")
        assertTrue(grey.x in MIN_CLAY..MAX_CLAY, "$backend: clay is not a mid grey: $grey")
        ClayPlane.entries.forEach { kind ->
            val off = CLAY_POINTS.filter { (x, y) -> !clay.getValue(kind).matches(x, y, reference.rgb(x, y)) }
            assertTrue(off.isEmpty(), "$backend: $kind's clay differs from the untextured plane's at $off")
        }
        val orange = lit.getValue(ClayPlane.TexturedOrange).rgb(CENTRE, CENTRE)
        assertTrue(!lit.getValue(ClayPlane.Untextured).matches(CENTRE, CENTRE, orange), "$backend: lit, the orange and white planes already match")
    }

    @Test
    fun jointWeightsShowEachJointInItsOwnColour() = eachBackend { backend ->
        val weights = render(backend) { renderTwoJointPlaneScene(RenderDebugView.JointWeights) }
        val lit = render(backend) { renderTwoJointPlaneScene(RenderDebugView.Off) }

        assertTrue(weights.matches(LEFT, CENTRE, jointColour(0)), "$backend: joint 0's half reads ${weights.rgb(LEFT, CENTRE)}")
        assertTrue(weights.matches(RIGHT, CENTRE, jointColour(1)), "$backend: joint 1's half reads ${weights.rgb(RIGHT, CENTRE)}")
        assertTrue(!lit.matches(LEFT, CENTRE, jointColour(0)) && !lit.matches(RIGHT, CENTRE, jointColour(1)), "$backend: lit already shows joint colours")
    }

    @Test
    fun selectedJointWeightPaintsTheJointsVerticesRedAndTheRestBlue() = eachBackend { backend ->
        val joint1 = render(backend) { renderTwoJointPlaneScene(RenderDebugView.SelectedJointWeight, joint = 1) }
        val joint0 = render(backend) { renderTwoJointPlaneScene(RenderDebugView.SelectedJointWeight, joint = 0) }
        val lit = render(backend) { renderTwoJointPlaneScene(RenderDebugView.Off, joint = 1) }

        assertTrue(joint1.matches(LEFT, CENTRE, BLUE) && joint1.matches(RIGHT, CENTRE, RED), "$backend: joint 1 reads ${joint1.rgb(LEFT, CENTRE)} | ${joint1.rgb(RIGHT, CENTRE)}")
        assertTrue(joint0.matches(LEFT, CENTRE, RED) && joint0.matches(RIGHT, CENTRE, BLUE), "$backend: joint 0 reads ${joint0.rgb(LEFT, CENTRE)} | ${joint0.rgb(RIGHT, CENTRE)}")
        assertTrue(!lit.matches(RIGHT, CENTRE, RED), "$backend: lit already paints the joint red")
    }

    /** The skinned shaders used to draw lit under every view: albedo must show the texture as stored. */
    @Test
    fun aSkinnedMeshUnderAlbedoDiffersFromItsLitFrame() = eachBackend { backend ->
        val albedo = render(backend) { renderClayPlaneScene(ClayPlane.Skinned, RenderDebugView.Albedo) }
        val lit = render(backend) { renderClayPlaneScene(ClayPlane.Skinned, RenderDebugView.Off) }

        assertTrue(albedo.matches(CENTRE, CENTRE, ORANGE), "$backend: skinned albedo reads ${albedo.rgb(CENTRE, CENTRE)}")
        assertTrue(!lit.matches(CENTRE, CENTRE, ORANGE), "$backend: the lit skinned plane is already its flat texture colour")
    }

    /** Edges over every kind of plane, lit and under clay, with the faces between them untouched. */
    @Test
    fun theWireframeOverlayDrawsEachTriangleEdgeOverTheFrame() = eachBackend { backend ->
        listOf(RenderDebugView.Off, RenderDebugView.Clay).forEach { view ->
            ClayPlane.entries.forEach { kind ->
                val edged = render(backend) { renderClayPlaneScene(kind, view, wireframe = true) }
                val plain = render(backend) { renderClayPlaneScene(kind, view) }
                val label = "$backend, $kind, $view"
                assertTrue(edged.edgeNear(CENTRE, CENTRE), "$label: no edge on the plane's diagonal")
                assertTrue(!plain.edgeNear(CENTRE, CENTRE), "$label: an edge with the overlay off")
                FACE_POINTS.forEach { (x, y) ->
                    assertTrue(edged.matches(x, y, plain.rgb(x, y)), "$label: the overlay changed the face at $x, $y to ${edged.rgb(x, y)}")
                }
            }
        }
    }

    /** A skinned mesh's edges are drawn where its pose puts it, not where it rests. */
    @Test
    fun aSkinnedMeshsEdgesMoveWithItsPose() = eachBackend { backend ->
        val posed = render(backend) {
            renderClayPlaneScene(ClayPlane.Skinned, RenderDebugView.Off, wireframe = true, jointOffset = POSE_SHIFT)
        }
        val posedCentre = pixelColumn(Vec3f(POSE_SHIFT, 0f, 0f))

        assertTrue(posed.edgeNear(posedCentre, CENTRE), "$backend: no edge on the posed plane's diagonal, at column $posedCentre")
        assertTrue(!posed.edgeNear(CENTRE, CENTRE), "$backend: an edge where the plane's diagonal rests")
    }

    private fun eachBackend(check: (HeadlessUiBackend) -> Unit) = BACKEND_ORDER.forEach(check)

    private fun render(backend: HeadlessUiBackend, scene: Renderer.() -> ByteArray) = Frame(session(backend).renderer.scene(), backend)

    private class Frame(private val pixels: ByteArray, private val backend: HeadlessUiBackend) {
        /** The shader's output value, 0-255: WebGPU's target is sRGB, so its stored bytes are decoded. */
        fun rgb(x: Int, y: Int): Vec3f {
            fun channel(c: Int): Float {
                val stored = pixels[(y * SCENE_SIZE + x) * 4 + c].toInt() and 0xFF
                return if (backend == HeadlessUiBackend.WebGpu) SRGB_TO_LINEAR[stored] else stored.toFloat()
            }
            return Vec3f(channel(0), channel(1), channel(2))
        }

        fun matches(x: Int, y: Int, expected: Vec3f): Boolean {
            val actual = rgb(x, y)
            return abs(actual.x - expected.x) <= TOLERANCE && abs(actual.y - expected.y) <= TOLERANCE && abs(actual.z - expected.z) <= TOLERANCE
        }

        /** Whether an edge's grey is within [EDGE_REACH] pixels of [x], [y]: a one-pixel line rasterizes a pixel either way. */
        fun edgeNear(x: Int, y: Int): Boolean =
            (-EDGE_REACH..EDGE_REACH).any { dy -> (-EDGE_REACH..EDGE_REACH).any { dx -> matches(x + dx, y + dy, EDGE) } }
    }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        private val sessions = mutableMapOf<HeadlessUiBackend, HeadlessRenderSession>()

        fun session(backend: HeadlessUiBackend): HeadlessRenderSession =
            sessions.getOrPut(backend) { openHeadlessScene(backend) }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            sessions.remove(HeadlessUiBackend.Vulkan)?.close()
        }

        const val TOLERANCE = 6f
        const val CENTRE = SCENE_SIZE / 2

        /** Inside each half of the two-joint plane, clear of the seam at the centre. */
        const val LEFT = SCENE_SIZE * 3 / 10
        const val RIGHT = SCENE_SIZE * 7 / 10

        /** A mid grey: neither black, nor clipped white. */
        const val MIN_CLAY = 60f
        const val MAX_CLAY = 230f

        /** Off the plane's diagonal, which crosses the centre at about 30 degrees, by 12 pixels or more. */
        val FACE_POINTS = listOf(CENTRE to LEFT, CENTRE to RIGHT, LEFT to CENTRE, RIGHT to CENTRE)

        const val EDGE_REACH = 2

        /** Moves the skinned plane's diagonal about 32 pixels right along the centre row. */
        const val POSE_SHIFT = 3f

        val EDGE = Vec3f(WIREFRAME_EDGE_GREY * 255f, WIREFRAME_EDGE_GREY * 255f, WIREFRAME_EDGE_GREY * 255f)

        /** The pixel column [point] lands in: the same on both backends, which differ only in y. */
        fun pixelColumn(point: Vec3f): Int {
            val clip = clayPlaneLens().viewProjectionMatrix(1f, ClipSpace.Vulkan).transformPosition(Vec4(point.x, point.y, point.z, 1f))
            return ((clip.x / clip.w * 0.5f + 0.5f) * SCENE_SIZE).toInt()
        }

        /** Points across the plane where every kind of surface must draw the same clay. */
        val CLAY_POINTS = listOf(LEFT, CENTRE, RIGHT).flatMap { x -> listOf(LEFT, CENTRE, RIGHT).map { y -> x to y } }

        val RED = Vec3f(255f, 0f, 0f)
        val BLUE = Vec3f(0f, 0f, 255f)

        /** `SolidOrange` as stored: albedo is written unlit, in the colour space it was read in. */
        val ORANGE = Vec3f(220f, 80f, 40f)

        /** `debugLayerColor(joint)`, which `JointWeights` gives each joint, 0-255. */
        fun jointColour(joint: Int): Vec3f = debugLayerColor(joint).let { Vec3f(it.r * 255f, it.g * 255f, it.b * 255f) }

        val SRGB_TO_LINEAR = FloatArray(256) { srgb ->
            val c = srgb / 255.0
            val lin = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            (lin * 255.0).toFloat()
        }
    }
}
