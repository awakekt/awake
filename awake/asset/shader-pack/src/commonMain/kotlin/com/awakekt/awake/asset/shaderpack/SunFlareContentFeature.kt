/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.asset.shaders.stagesFor
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Optical sun flare drawn after geometry along the line from the sun's screen position through
 * the screen centre (#1537 Stage C).
 *
 * When [occludeByDepth] is true, five taps in [BindingSemantic.SceneDepth] around the sun's screen
 * UV fade the flare smoothly behind terrain and meshes without hardware occlusion queries, so
 * Vulkan and WebGPU behave identically. Requires `RenderPlan.sceneDepthShaderSet` when occluded.
 *
 * @param texture Optional sprite texture drawn for each element; null draws procedural soft discs/rings.
 * @param look Live per-frame flare settings and element list.
 * @param occludeByDepth When true, samples `SceneDepth` around the sun to hide the flare behind geometry.
 * @param shaders Shader set used for the flare pass.
 */
fun sunFlareContentFeature(
    texture: TextureAsset? = null,
    look: SunFlareLook = SunFlareLook(),
    occludeByDepth: Boolean = true,
    shaders: ShaderSet = if (occludeByDepth) PackShaderSets.SunFlare else PackShaderSets.SunFlareUnoccluded,
): ContentFeatureSource = ContentFeatureSource { backend ->
    ContentFeature(
        name = "sun_flare",
        spec = shaders.stagesFor(backend).spec(
            vertexFormat = VertexFormat.None,
            variant = PipelineVariant.OverlayAdditive,
            uniforms = SunFlareUniformLayout,
        ),
        textures = mapOf(FLARE_TEXTURE_BINDING to (texture ?: BLANK_FLARE)),
        paint = ContentPaint.AfterGeometry,
        samplesSceneDepth = occludeByDepth,
    ) { pipeline, uniforms, _ ->
        SunFlareRenderFeature(pipeline, uniforms, look, textured = texture != null, occludeByDepth = occludeByDepth)
    }
}

/** Records the sun-flare full-screen overlay pass after geometry. */
class SunFlareRenderFeature(
    private val pipeline: PipelineHandle,
    private val uniforms: UniformBlock,
    private val look: SunFlareLook,
    private val textured: Boolean = false,
    private val occludeByDepth: Boolean = true,
) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    private val sun = Vec3f()
    private val geometryBuffer = FloatArray(MAX_SUN_FLARE_ELEMENTS * 4)
    private val colorBuffer = FloatArray(MAX_SUN_FLARE_ELEMENTS * 4)

    override fun recordCommands(context: RenderFrameContext): Unit = with(context) {
        if (!canDraw(environment)) return
        if (!resolveSunDirection(context)) return
        if (sun.y <= 0f) return

        // Project directional sun at infinity: V * (dir, 0) = (R * dir, 0), so viewProjection * (dir, 0) gives clip.
        val vp = viewProjection
        val clipW = vp.m30 * sun.x + vp.m31 * sun.y + vp.m32 * sun.z
        if (clipW <= MIN_CLIP_W) return
        val ndcX = (vp.m00 * sun.x + vp.m01 * sun.y + vp.m02 * sun.z) / clipW
        val ndcY = (vp.m10 * sun.x + vp.m11 * sun.y + vp.m12 * sun.z) / clipW
        val strength = computeStrength(sun.y, ndcX, ndcY)
        if (strength <= 0f) return

        val count = packElements(look.elements)
        uniforms.write(frameIndex) {
            put(SunFlareFields.SunScreen, ndcX, ndcY, strength, aspectFromViewProjection(vp))
            put(SunFlareFields.FlareParams, count.toFloat(), if (textured) 1f else 0f, OCCLUSION_SAMPLE_RADIUS, 0f)
            put(geometryBuffer, SunFlareFields.ElementGeometry)
            put(colorBuffer, SunFlareFields.ElementColor)
        }
        recorder.bindPipeline(pipeline)
        recorder.bindMaterial(BindingSemantic.Material, uniforms.binding(frameIndex))
        if (occludeByDepth) {
            sceneDepthBinding(pipeline)?.let { recorder.bindMaterial(BindingSemantic.SceneDepth, it) }
        }
        recorder.draw(FULLSCREEN_TRIANGLE_VERTICES, 1)
    }

    private fun canDraw(environment: com.awakekt.awake.render.command.GpuEnvironmentState): Boolean =
        look.enabled &&
            environment.showSky &&
            environment.debugView == GpuDebugView.Off &&
            look.intensity > 0f &&
            look.sunVisibility > 0f &&
            look.elements.isNotEmpty()

    private fun resolveSunDirection(context: RenderFrameContext): Boolean {
        if (look.followsDirection) sun.set(look.sunDirection.x, look.sunDirection.y, look.sunDirection.z) else context.writeSunDirection(sun)
        val length = sqrt(sun.x * sun.x + sun.y * sun.y + sun.z * sun.z)
        if (length < MIN_LENGTH) return false
        sun.set(sun.x / length, sun.y / length, sun.z / length)
        return true
    }

    private fun computeStrength(sunY: Float, ndcX: Float, ndcY: Float): Float {
        val edge = max(abs(ndcX), abs(ndcY))
        if (edge >= EDGE_FADE_END) return 0f
        val horizonFade = (sunY / HORIZON_FADE_SIN).coerceIn(0f, 1f)
        val edgeFade = if (edge <= EDGE_FADE_START) 1f else 1f - (edge - EDGE_FADE_START) / (EDGE_FADE_END - EDGE_FADE_START)
        return look.intensity * look.sunVisibility * horizonFade * edgeFade
    }

    private fun packElements(elements: List<SunFlareElement>): Int {
        val count = elements.size.coerceAtMost(MAX_SUN_FLARE_ELEMENTS)
        geometryBuffer.fill(0f)
        colorBuffer.fill(0f)
        for (i in 0 until count) {
            val el = elements[i]
            val base = i * 4
            geometryBuffer[base] = el.position
            geometryBuffer[base + 1] = el.size
            geometryBuffer[base + 2] = if (el.ring) 1f else 0f
            geometryBuffer[base + 3] = el.intensity
            colorBuffer[base] = el.color.r
            colorBuffer[base + 1] = el.color.g
            colorBuffer[base + 2] = el.color.b
            colorBuffer[base + 3] = 1f
        }
        return count
    }

    override fun destroy() = Unit
}

private fun aspectFromViewProjection(vp: Mat4): Float {
    val row0 = sqrt(vp.m00 * vp.m00 + vp.m01 * vp.m01 + vp.m02 * vp.m02)
    val row1 = sqrt(vp.m10 * vp.m10 + vp.m11 * vp.m11 + vp.m12 * vp.m12)
    return if (row0 > MIN_LENGTH) (row1 / row0).coerceIn(MIN_ASPECT, MAX_ASPECT) else 1f
}

private const val FULLSCREEN_TRIANGLE_VERTICES = 3
private const val FLARE_TEXTURE_BINDING = 1
private const val MIN_LENGTH = 1e-4f
private const val MIN_CLIP_W = 1e-4f
private const val HORIZON_FADE_SIN = 0.08f
private const val EDGE_FADE_START = 1.0f
private const val EDGE_FADE_END = 1.35f
private const val OCCLUSION_SAMPLE_RADIUS = 0.015f
private const val MIN_ASPECT = 0.25f
private const val MAX_ASPECT = 4.0f
private val BLANK_FLARE = TextureAsset(ByteArray(4) { 0xFF.toByte() }, 1, 1)
