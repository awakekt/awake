/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.CloudLayerLook
import com.awakekt.awake.asset.shaderpack.CloudLayerQuality
import com.awakekt.awake.asset.shaderpack.StylizedWaterLook
import com.awakekt.awake.asset.shaderpack.StylizedWaterQuality
import com.awakekt.awake.asset.shaderpack.cloudLayerContentFeature
import com.awakekt.awake.asset.shaderpack.stylizedWaterContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.InterleavedVertices
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import kotlinx.coroutines.runBlocking

private val DAY_SUN = Vec3f(0.25f, 0.42f, -0.87f)
private val DAY_SKY_HORIZON = Color(0.68f, 0.84f, 0.98f, 1f)
private val DAY_SKY_ZENITH = Color(0.18f, 0.46f, 0.88f, 1f)
private val CLOUD_BACKDROP = Color(0.16f, 0.38f, 0.72f, 1f)

/** Builds a horizontal quad of `[halfExtent] * 2` metres at `y = 0` in [VertexFormat.PositionNormalColorUv]. */
internal fun horizontalPlaneGeometry(halfExtent: Float): MeshGeometry {
    val verts = InterleavedVertices(VertexFormat.PositionNormalColorUv, 4)
    val corners = arrayOf(
        floatArrayOf(-halfExtent, 0f, -halfExtent, 0f, 0f),
        floatArrayOf(halfExtent, 0f, -halfExtent, 1f, 0f),
        floatArrayOf(halfExtent, 0f, halfExtent, 1f, 1f),
        floatArrayOf(-halfExtent, 0f, halfExtent, 0f, 1f),
    )
    for (i in corners.indices) {
        val c = corners[i]
        verts.put(i, VertexSemantic.Position, c[0], c[1], c[2])
        verts.put(i, VertexSemantic.Normal, 0f, 1f, 0f)
        verts.put(i, VertexSemantic.Color, 1f, 1f, 1f)
        verts.put(i, VertexSemantic.Uv, c[3], c[4])
    }
    return verts.build(intArrayOf(0, 2, 1, 0, 3, 2))
}

/** Renders the procedural stylized water surface over a backdrop for parity and render evidence. */
fun Renderer.renderStylizedWaterScene(
    quality: StylizedWaterQuality = StylizedWaterQuality.Standard,
    timeSeconds: Float = 0f,
    sunDirection: Vec3f = DAY_SUN,
    enabled: Boolean = true,
    size: Int = SCENE_SIZE,
): ByteArray {
    val host = checkNotNull(this as? ContentFeatureHost) { "This renderer cannot attach content features." }
    val look = StylizedWaterLook(
        enabled = enabled,
        quality = quality,
        timeSeconds = timeSeconds,
        waveScale = 0.55f,
        waveSpeed = 1.2f,
    )
    val attached = runBlocking {
        host.attachContentFeature(stylizedWaterContentFeature(horizontalPlaneGeometry(40f), look, name = "stylized_water_test"))
    }
    val target = createRenderTarget(size, size)
    return try {
        val lens = Lens(eye = Vec3f(0f, 5.5f, 14f), center = Vec3f(0f, 0f, -6f), fovYRadians = 0.85f, near = 0.1f, far = 200f)
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = emptyList(),
                light = SceneLight(direction = sunDirection, color = Vec3f(1f, 0.96f, 0.88f), ambient = 0.35f),
                environment = EnvironmentUniforms(
                    showSky = false,
                    horizonColor = DAY_SKY_HORIZON,
                    zenithColor = DAY_SKY_ZENITH,
                    fogColor = DAY_SKY_HORIZON,
                    fogStart = 30f,
                    fogEnd = 120f,
                    clearColor = Color(0.12f, 0.10f, 0.08f, 1f),
                ),
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
            ),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        target.destroy()
        attached.detach()
    }
}

/** Renders the single-pass procedural cloud layer over a blue sky backdrop for parity and render evidence. */
fun Renderer.renderCloudLayerScene(
    quality: CloudLayerQuality = CloudLayerQuality.Standard,
    timeSeconds: Float = 0f,
    sunDirection: Vec3f = DAY_SUN,
    enabled: Boolean = true,
    fadeStart: Float = 80f,
    fadeEnd: Float = 260f,
    size: Int = SCENE_SIZE,
): ByteArray {
    val host = checkNotNull(this as? ContentFeatureHost) { "This renderer cannot attach content features." }
    val look = CloudLayerLook(
        enabled = enabled,
        quality = quality,
        timeSeconds = timeSeconds,
        scale = 0.035f,
        driftSpeedX = 0.18f,
        driftSpeedZ = 0.12f,
        coverage = 0.62f,
        softness = 0.28f,
        opacity = 0.85f,
        fadeStart = fadeStart,
        fadeEnd = fadeEnd,
    )
    val attached = runBlocking {
        host.attachContentFeature(cloudLayerContentFeature(horizontalPlaneGeometry(320f), look, name = "cloud_layer_test"))
    }
    val target = createRenderTarget(size, size)
    return try {
        val lens = Lens(eye = Vec3f(0f, 42f, 120f), center = Vec3f(0f, 0f, -40f), fovYRadians = 0.9f, near = 0.5f, far = 600f)
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = emptyList(),
                light = SceneLight(direction = sunDirection, color = Vec3f(1f, 0.97f, 0.92f), ambient = 0.42f),
                environment = EnvironmentUniforms(
                    showSky = false,
                    horizonColor = DAY_SKY_HORIZON,
                    zenithColor = DAY_SKY_ZENITH,
                    fogColor = DAY_SKY_HORIZON,
                    fogDensity = 0f,
                    clearColor = CLOUD_BACKDROP,
                ),
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
            ),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        target.destroy()
        attached.detach()
    }
}

/** Appends stylized water and cloud-layer frames to the PR render evidence gallery. */
internal fun writeWaterAndCloudEvidence(
    renderer: Renderer,
    size: Int,
    write: (name: String, caption: String, size: Int, pixels: ByteArray) -> Unit,
) {
    write(
        "scene-stylized-water-standard",
        "Stylized water (Standard): analytic wave normals, Fresnel sky reflection and sun glint",
        size,
        renderer.renderStylizedWaterScene(StylizedWaterQuality.Standard, timeSeconds = 1.5f, size = size),
    )
    write(
        "scene-stylized-water-fast",
        "Stylized water (Fast mobile tier): 2-wave ripple blend, Fresnel sheen and fog",
        size,
        renderer.renderStylizedWaterScene(StylizedWaterQuality.Fast, timeSeconds = 1.5f, size = size),
    )
    write(
        "scene-cloud-layer-standard",
        "Cloud layer (Standard): single-pass dual parallax billowing clouds with horizon fade",
        size,
        renderer.renderCloudLayerScene(CloudLayerQuality.Standard, timeSeconds = 2.0f, size = size),
    )
    write(
        "scene-cloud-layer-fast",
        "Cloud layer (Fast mobile tier): single-pass 2-octave drifting clouds with horizon fade",
        size,
        renderer.renderCloudLayerScene(CloudLayerQuality.Fast, timeSeconds = 2.0f, size = size),
    )
}
