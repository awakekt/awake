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
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.inverse
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.passes.SharedSkyboxRenderFeature
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.MOON_DISC_COLOR
import com.awakekt.awake.render.renderer.SUN_DISC_COLOR
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The sun and moon drawn over the sky behind them, on any sky: a gradient, a cubemap or a flat colour.
 *
 * The sun is where the day cycle's sun is (or the scene light, without a day cycle) and the moon opposite it.
 * A body without a texture is the procedural sky's own disc. It draws before geometry and after
 * the features attached before it, so attach it after the sky it sits over.
 *
 * @param sunTexture The sun's image, drawn on a square the size of its disc; null draws the disc.
 * @param moonTexture The moon's image, as [sunTexture]; the phase cuts it.
 * @param look The moon's phase, read every frame.
 * @param shaders The shader set to draw it with.
 * @param additive Add the bodies to the sky rather than covering it.
 */
fun celestialContentFeature(
    sunTexture: TextureAsset? = null,
    moonTexture: TextureAsset? = null,
    look: CelestialLook = CelestialLook(),
    shaders: ShaderSet = PackShaderSets.Celestial,
    additive: Boolean = false,
): ContentFeatureSource = ContentFeatureSource { backend ->
    ContentFeature(
        name = "celestial",
        spec = shaders.stagesFor(backend).spec(
            vertexFormat = VertexFormat.None,
            variant = if (additive) PipelineVariant.BackgroundAdditive else PipelineVariant.BackgroundBlended,
            uniforms = CelestialUniformLayout,
        ),
        textures = mapOf(SUN_BINDING to (sunTexture ?: BLANK), MOON_BINDING to (moonTexture ?: BLANK)),
    ) { pipeline, uniforms, _ -> CelestialRenderFeature(pipeline, uniforms, look, sunTexture != null, moonTexture != null) }
}

private class CelestialRenderFeature(
    private val pipeline: PipelineHandle,
    private val uniforms: UniformBlock,
    private val look: CelestialLook,
    private val sunTextured: Boolean,
    private val moonTextured: Boolean,
) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    private val shared = SharedSkyboxRenderFeature()
    private val sun = Vec3f()
    private val sunRight = Vec3f()
    private val sunUp = Vec3f()
    private val moon = Vec3f()
    private val moonRight = Vec3f()
    private val moonUp = Vec3f()

    override fun recordCommands(context: RenderFrameContext): Unit = with(context) {
        if (!environment.showSky) return
        val inverse = viewProjection.inverse() ?: return
        if (look.followsDirection) sun.set(look.sunDirection.x, look.sunDirection.y, look.sunDirection.z) else writeSunDirection(sun)
        val length = sqrt(sun.x * sun.x + sun.y * sun.y + sun.z * sun.z)
        if (length < MIN_LENGTH) return
        sun.set(sun.x / length, sun.y / length, sun.z / length)
        moon.set(-sun.x, -sun.y, -sun.z)
        axes(sun, sunRight, sunUp)
        axes(moon, moonRight, moonUp)
        val phase = look.moonPhase.mod(1f)
        val angle = phase * TURN
        uniforms.write(frameIndex) {
            put(CelestialFields.InverseViewProjection, inverse)
            put(CelestialFields.CameraEye, cameraEye)
            put(CelestialFields.SunDirection, sun)
            put(CelestialFields.SunRight, sunRight)
            put(CelestialFields.SunUp, sunUp)
            put(CelestialFields.MoonDirection, moon)
            put(CelestialFields.MoonRight, moonRight)
            put(CelestialFields.MoonUp, moonUp)
            put(CelestialFields.SunColor, SUN_DISC_COLOR)
            put(CelestialFields.MoonColor, MOON_DISC_COLOR)
            put(CelestialFields.Visibility, look.sunVisibility, look.moonVisibility, 0f, 0f)
            put(CelestialFields.Params, cos(angle), if (phase < CelestialLook.FULL_MOON) 1f else -1f, if (sunTextured) 1f else 0f, if (moonTextured) 1f else 0f)
        }
        shared.recordCommands(recorder, pipeline, uniforms.binding(frameIndex))
    }

    /** Nothing: the registry owns the pipeline, and the block lives on it. */
    override fun destroy() = Unit
}

/** Writes the screen-plane axes seen looking along [direction]: right is `direction x up`, up is `right x direction`. */
private fun axes(direction: Vec3f, right: Vec3f, up: Vec3f) {
    // A body at the zenith or nadir has no horizon to take "up" from; use +X there.
    val upX = if (abs(direction.y) < POLE) 0f else 1f
    val upY = if (abs(direction.y) < POLE) 1f else 0f
    right.set(
        -direction.z * upY,
        direction.z * upX,
        direction.x * upY - direction.y * upX,
    )
    val length = sqrt(right.x * right.x + right.y * right.y + right.z * right.z)
    right.set(right.x / length, right.y / length, right.z / length)
    up.set(
        right.y * direction.z - right.z * direction.y,
        right.z * direction.x - right.x * direction.z,
        right.x * direction.y - right.y * direction.x,
    )
}

/** Where [CelestialShader] declares the sun's and moon's textures. */
private const val SUN_BINDING = 1
private const val MOON_BINDING = 2
private const val MIN_LENGTH = 1e-4f
private const val POLE = 0.99f
private const val TURN = (2.0 * PI).toFloat()

/** A white texel for a body with no texture; the shader ignores it and draws the disc. */
private val BLANK = TextureAsset(ByteArray(4) { 0xFF.toByte() }, 1, 1)
