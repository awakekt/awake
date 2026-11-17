/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.FrameUniformBlock
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.ParticleDrawLayout
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.passes.uniforms.SpriteDrawLayout
import com.awakekt.awake.render.passes.uniforms.SpriteUniformLayout
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.render.passes.uniforms.TrailDrawLayout
import com.awakekt.awake.render.passes.uniforms.TrailUniformLayout
import com.awakekt.awake.render.passes.uniforms.WorldTextDrawLayout
import com.awakekt.awake.render.passes.uniforms.WorldTextUniformLayout
import com.awakekt.awake.render.passes.uniforms.drawBlockFloats
import com.awakekt.awake.render.passes.uniforms.identityFrameBlock
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.passes.uniforms.planDrawBlockFloats
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.renderer.MAX_SHADOW_CASCADES
import com.awakekt.awake.render.renderer.SkinnedDrawLayout
import com.awakekt.awake.render.renderer.SkinnedFields
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.render.renderer.UniformLayout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The frame block and a draw's own block hold, between them, what the single block held: every
 * field a lit shader read from [MaterialUniformLayouts.LitShadow] or `PbrTextured` is read from one
 * of the two with the same floats, and the product the shader now computes is the MVP it was given.
 */
class FrameUniformsTest {
    @Test
    fun theFrameBlockHoldsTheSingleBlocksFrameFields() {
        val frame = FrameUniformBlock().pack(VIEW_PROJECTION, EYE, FORWARD, LIGHT, CASCADES, FOG, FOG_DENSITY, GpuDebugView.Off, EXPOSURE)
        val whole = lit().whole(MaterialUniformLayouts.LitShadow.total)

        assertEquals(MaterialUniformLayouts.Frame.total, frame.size)
        assertContentEquals(VIEW_PROJECTION.data, frame.field(MaterialUniformLayouts.Frame, UniformFields.ViewProjection))
        FRAME_FIELDS.forEach { field ->
            assertContentEquals(
                whole.field(MaterialUniformLayouts.LitShadow, field),
                frame.field(MaterialUniformLayouts.Frame, field),
                "${field.name} moved to the frame block with other floats",
            )
        }
        assertEquals(EXPOSURE, frame[MaterialUniformLayouts.Frame.offsetOf(UniformFields.Exposure)])
    }

    @Test
    fun aLitDrawsBlockHoldsTheSingleBlocksDrawFields() {
        val draw = lit(fade = 0.25f)
        val own = draw.drawBlockFloats(MaterialUniformLayouts.LitShadowDraw.total, alphaToCoverageEnabled = false)
        val whole = draw.whole(MaterialUniformLayouts.LitShadow.total)

        listOf(UniformFields.Model, UniformFields.VertexAnimation, UniformFields.Material).forEach { field ->
            assertContentEquals(
                whole.field(MaterialUniformLayouts.LitShadow, field),
                own.field(MaterialUniformLayouts.LitShadowDraw, field),
                field.name,
            )
        }
        assertEquals(
            whole[MaterialUniformLayouts.LitShadow.offsetOf(UniformFields.Exposure) + 1],
            own[MaterialUniformLayouts.LitShadowDraw.offsetOf(UniformFields.DrawFade)],
            "the dither fade",
        )
    }

    @Test
    fun aTexturedDrawsBlockHoldsTheSingleBlocksDrawFields() {
        val draw = textured(fade = 0.5f)
        val own = draw.drawBlockFloats(MaterialUniformLayouts.PbrTexturedDraw.total, alphaToCoverageEnabled = false)
        val whole = draw.whole(MaterialUniformLayouts.PbrTextured.total)

        TEXTURED_DRAW_FIELDS.forEach { field ->
            assertContentEquals(
                whole.field(MaterialUniformLayouts.PbrTextured, field),
                own.field(MaterialUniformLayouts.PbrTexturedDraw, field),
                field.name,
            )
        }
        assertEquals(
            whole[MaterialUniformLayouts.PbrTextured.offsetOf(UniformFields.Exposure) + 1],
            own[MaterialUniformLayouts.PbrTexturedDraw.offsetOf(UniformFields.DrawFade)],
            "the dither fade",
        )
    }

    /** Depth passes read these two from the start of whichever draw block the buffer holds. */
    @Test
    fun everySplitDrawBlockStartsWithWhatADepthPassReads() {
        listOf(MaterialUniformLayouts.LitShadowDraw, MaterialUniformLayouts.PbrTexturedDraw).forEach { layout ->
            assertEquals(listOf(UniformFields.Model, UniformFields.VertexAnimation), layout.fields.take(2))
        }
    }

    @Test
    fun anInstancedDrawsBlockLeavesTheModelToItsInstances() {
        val own = lit().drawBlockFloats(MaterialUniformLayouts.LitShadowDraw.total, alphaToCoverageEnabled = false, instanced = true)
        assertContentEquals(Mat4().data, own.field(MaterialUniformLayouts.LitShadowDraw, UniformFields.Model))
    }

    @Test
    fun theFrameBlockIsRefilledInPlace() {
        val block = FrameUniformBlock()
        val first = block.pack(VIEW_PROJECTION, EYE, FORWARD, LIGHT, CASCADES, FOG, FOG_DENSITY, GpuDebugView.Off, EXPOSURE)
        val second = block.pack(Mat4(), Vec3f(1f, 2f, 3f), FORWARD, LIGHT, null, FOG, 0f, GpuDebugView.Off.copy(layer = 5), 1f)
        assertSame(first, second, "a frame allocates no block")
        assertEquals(5f, second[MaterialUniformLayouts.Frame.offsetOf(UniformFields.Exposure) + 1], "Exposure.y carries debugView.layer")
    }

    @Test
    fun remainingSceneShadersPlanAndPackCameraFreeDrawBlocks() {
        val spriteExtras = floatArrayOf(0.5f, 0.5f, 0.25f, 0f, 1f, 0.8f, 0.6f, 1f)
        val sprite = RenderDrawCommand(FakeMesh(VertexFormat.PositionUv), FakeMaterial, model = MODEL, extraUniformFloats = spriteExtras)
        assertEquals(SpriteDrawLayout.total, sprite.planDrawBlockFloats(SpriteUniformLayout.total))
        val spriteOwn = sprite.drawBlockFloats(SpriteDrawLayout.total, alphaToCoverageEnabled = false, instancedKind = null)
        assertContentEquals(MODEL.data, spriteOwn.field(SpriteDrawLayout, UniformFields.Model))
        assertContentEquals(spriteExtras, spriteOwn.copyOfRange(SpriteDrawLayout.offsetOf(UniformFields.Model) + 16, spriteOwn.size))

        val textExtras = floatArrayOf(1f, 1f, 1f, 1f, 4f, 0f, 0f, 0f)
        val worldText = RenderDrawCommand(FakeMesh(VertexFormat.PositionUvColor), FakeMaterial, model = MODEL, extraUniformFloats = textExtras)
        assertEquals(WorldTextDrawLayout.total, worldText.planDrawBlockFloats(WorldTextUniformLayout.total))
        val textOwn = worldText.drawBlockFloats(WorldTextDrawLayout.total, alphaToCoverageEnabled = false, instancedKind = null)
        assertContentEquals(MODEL.data, textOwn.field(WorldTextDrawLayout, UniformFields.Model))

        val trailExtras = floatArrayOf(0.2f, 0.4f, 0.9f, 1f)
        val trail = RenderDrawCommand(FakeMesh(VertexFormat.PositionRgbaUv), FakeMaterial, model = MODEL, extraUniformFloats = trailExtras)
        assertEquals(TrailDrawLayout.total, trail.planDrawBlockFloats(TrailUniformLayout.total))
        val trailOwn = trail.drawBlockFloats(TrailDrawLayout.total, alphaToCoverageEnabled = false, instancedKind = null)
        assertContentEquals(MODEL.data, trailOwn.field(TrailDrawLayout, UniformFields.Model))

        val particleExtras = FloatArray(ParticleDrawLayout.total) { it + 1f }
        val particle = RenderDrawCommand(FakeMesh(VertexFormat.PositionUv), FakeMaterial, extraUniformFloats = particleExtras)
        assertEquals(ParticleDrawLayout.total, particle.planDrawBlockFloats(ParticleUniformLayout.total, instancedKind = InstancedDrawKind.Particle))
        assertContentEquals(particleExtras, particle.drawBlockFloats(ParticleDrawLayout.total, alphaToCoverageEnabled = false, instancedKind = InstancedDrawKind.Particle))

        val joints = FloatArray(SkinnedFields.JointPalette.floats) { (it % 16 == 0).let { diag -> if (diag) 1f else 0f } }
        val skinned = RenderDrawCommand(FakeMesh(VertexFormat.PositionNormalColorSkin), FakeMaterial, model = MODEL, extraUniformFloats = joints)
        assertEquals(SkinnedDrawLayout.total, skinned.planDrawBlockFloats(0))
        val skinnedOwn = skinned.drawBlockFloats(SkinnedDrawLayout.total, alphaToCoverageEnabled = false, instancedKind = null)
        assertContentEquals(joints, skinnedOwn.field(SkinnedDrawLayout, SkinnedFields.JointPalette))
        assertContentEquals(MODEL.data, skinnedOwn.field(SkinnedDrawLayout, UniformFields.Model))

        val unshadowedLit = RenderDrawCommand(FakeMesh(VertexFormat.PositionNormalColor), FakeMaterial, model = MODEL)
        assertEquals(MaterialUniformLayouts.LitDraw.total, unshadowedLit.planDrawBlockFloats(MaterialUniformLayouts.Lit.total, hasShadowCascades = false))
        assertEquals(
            MaterialUniformLayouts.InstancedDraw.total,
            unshadowedLit.planDrawBlockFloats(MaterialUniformLayouts.Lit.total, instancedKind = InstancedDrawKind.Plain, hasShadowCascades = false),
        )
    }

    @Test
    fun packInputIgnoresPointShadowSubPassesWhenDirectionalCascadesAreAbsent() {
        val pointFaces = List(18) { face ->
            GpuSubPass(target = null, targetLayer = MAX_SHADOW_CASCADES + face, viewProjection = VIEW_PROJECTION)
        }
        val packed = FrameUniformBlock().pack(
            GpuPassInput(
                viewProjection = VIEW_PROJECTION,
                cameraEye = EYE,
                passUniforms = LIGHT,
                prePasses = pointFaces,
            ),
        )
        assertEquals(MaterialUniformLayouts.Frame.total, packed.size)
        val identity = identityFrameBlock()
        assertContentEquals(Mat4().data, identity.field(MaterialUniformLayouts.Frame, UniformFields.ViewProjection))
        assertEquals(1f, identity.field(MaterialUniformLayouts.Frame, UniformFields.LightColor)[0])
    }

    /**
     * The engine's own input says which cascades it drew: a casting point light's faces start at
     * layer 0 in a frame with none (#1709), and must not be read as cascades by their layers.
     */
    @Test
    fun aResolvedInputsPointFacesAtLowLayersAreNotReadAsCascades() {
        val pointFaces = List(6) { face -> GpuSubPass(target = null, targetLayer = face, viewProjection = VIEW_PROJECTION) }
        fun packed(faces: List<GpuSubPass>) = FrameUniformBlock().pack(
            GpuPassInput(viewProjection = VIEW_PROJECTION, cameraEye = EYE, passUniforms = LIGHT, prePasses = faces, resolvedPath = true),
        )
        assertContentEquals(packed(emptyList()), packed(pointFaces), "packed as a frame with no cascades")
    }

    private fun RenderDrawCommand.whole(count: Int): FloatArray = uniformFloats(
        materialUniformFloatCount = count,
        viewProjection = VIEW_PROJECTION,
        cameraEye = EYE,
        lightPayload = LIGHT,
        shadowCascades = CASCADES,
        fogColor = FOG,
        fogDensity = FOG_DENSITY,
        cameraForward = FORWARD,
        exposure = EXPOSURE,
    )

    private fun FloatArray.field(layout: UniformLayout, field: UniformField): FloatArray =
        copyOfRange(layout.offsetOf(field), layout.offsetOf(field) + field.floats)

    private fun lit(fade: Float = 0f) = RenderDrawCommand(
        mesh = FakeMesh(VertexFormat.PositionNormalColor),
        material = FakeMaterial,
        model = MODEL,
        extraUniformFloats = floatArrayOf(0.2f, 0.7f, 0f, 0f),
        vertexAnimation = Vec3f(0.4f, 1f, 3f),
        timeSeconds = 2f,
    ).apply { fadeOpacity = 1f - fade }

    private fun textured(fade: Float = 0f) = RenderDrawCommand(
        mesh = FakeMesh(VertexFormat.PositionNormalColorUv),
        material = FakeMaterial,
        model = MODEL,
        extraUniformFloats = pbrMaterialFloats(0.1f, 0.6f, Color.White, Color.Transparent, TextureAnimation(columns = 2, rows = 2, framesPerSecond = 4f)),
        timeSeconds = 2f,
    ).apply { fadeOpacity = 1f - fade }

    private class FakeMesh(override val format: VertexFormat) : Mesh {
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private companion object {
        const val EXPOSURE = 1.5f
        const val FOG_DENSITY = 0.02f
        val FOG = Color(0.5f, 0.6f, 0.7f, 1f)
        val EYE = Vec3f(1f, 4f, 6f)
        val FORWARD = Vec3f(0f, -0.6f, -0.8f)
        val MODEL = Mat4().apply { data[12] = 2f; data[13] = 0.5f }
        val VIEW_PROJECTION = Mat4().apply { data[0] = 0.5f; data[14] = -3f }
        val LIGHT = FloatArray(MaterialUniformLayouts.SceneLight.total) { 0.1f * (it + 1) }
        val CASCADES = GpuShadowCascadeData(listOf(Mat4(), Mat4().apply { data[0] = 0.25f }), floatArrayOf(10f, 30f))
        val FRAME_FIELDS = listOf(
            UniformFields.LightDirection,
            UniformFields.LightColor,
            UniformFields.PointLightPositions,
            UniformFields.PointLightColors,
            UniformFields.CascadeViewProjections,
            UniformFields.CascadeDepthScales,
            UniformFields.CameraPosition,
            UniformFields.CameraForward,
            UniformFields.FogColor,
            UniformFields.DebugView,
        )
        val TEXTURED_DRAW_FIELDS = listOf(
            UniformFields.Model,
            UniformFields.VertexAnimation,
            UniformFields.PbrFactors,
            UniformFields.BaseColorFactor,
            UniformFields.EmissiveFactor,
            UniformFields.TextureFrames,
            UniformFields.TextureScroll,
        )
    }
}
