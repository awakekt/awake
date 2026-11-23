/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.CloudLayerLook
import com.awakekt.awake.asset.shaderpack.CloudLayerQuality
import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.StylizedWaterLook
import com.awakekt.awake.asset.shaderpack.StylizedWaterQuality
import com.awakekt.awake.asset.shaderpack.cloudLayerContentFeature
import com.awakekt.awake.asset.shaderpack.stylizedWaterContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.InterleavedVertices
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

private const val DEPTH_BOOT_TIMEOUT_MS = 30_000L

/** Builds a sloped sandy riverbed + faceted river boulders in [VertexFormat.PositionNormalColor] for depth-foam verification. */
private fun rockAndRiverbedGeometry(): MeshGeometry {
    val verts = mutableListOf<Float>()
    val indices = mutableListOf<Int>()
    fun addTri(p0: Vec3f, p1: Vec3f, p2: Vec3f, n: Vec3f, color: Color) {
        val base = verts.size / 9
        for (p in arrayOf(p0, p1, p2)) {
            verts += p.x; verts += p.y; verts += p.z
            verts += n.x; verts += n.y; verts += n.z
            verts += color.r; verts += color.g; verts += color.b
        }
        indices += base; indices += base + 1; indices += base + 2
    }
    fun addFacet(p0: Vec3f, p1: Vec3f, p2: Vec3f, color: Color) {
        val e1x = p1.x - p0.x; val e1y = p1.y - p0.y; val e1z = p1.z - p0.z
        val e2x = p2.x - p0.x; val e2y = p2.y - p0.y; val e2z = p2.z - p0.z
        val nx = e1y * e2z - e1z * e2y
        val ny = e1z * e2x - e1x * e2z
        val nz = e1x * e2y - e1y * e2x
        val invLen = 1f / sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
        addTri(p0, p1, p2, Vec3f(nx * invLen, ny * invLen, nz * invLen), color)
    }
    fun addQuad(p0: Vec3f, p1: Vec3f, p2: Vec3f, p3: Vec3f, n: Vec3f, color: Color) {
        addTri(p0, p1, p2, n, color)
        addTri(p0, p2, p3, n, color)
    }
    fun addBoulder(cx: Float, cz: Float, baseY: Float, peakY: Float, baseR: Float, topR: Float, phase: Float) {
        val sides = 10
        fun ring(y: Float, rScale: Float, jitterAmp: Float, phaseShift: Float): Array<Vec3f> = Array(sides) { i ->
            val a = (i.toFloat() / sides) * (2f * PI.toFloat()) + phase
            val wobble = 1f + jitterAmp * sin(a * 3f + phaseShift) + (jitterAmp * 0.55f) * cos(a * 5f - phaseShift)
            val r = rScale * wobble
            Vec3f(cx + cos(a) * r, y + 0.08f * sin(a * 2f + phaseShift), cz + sin(a) * r)
        }
        val low = ring(baseY, baseR, 0.16f, 0.4f)
        val mid = ring(0.18f, baseR * 0.76f, 0.15f, 1.3f)
        val top = ring(peakY * 0.74f, topR, 0.14f, 2.2f)
        val apex = Vec3f(cx + 0.14f * baseR, peakY, cz - 0.08f * baseR)
        val wetRock = Color(0.32f, 0.31f, 0.29f, 1f)
        val midRock = Color(0.45f, 0.43f, 0.40f, 1f)
        val dryRock = Color(0.55f, 0.52f, 0.48f, 1f)
        for (i in 0 until sides) {
            val next = (i + 1) % sides
            val shade = 0.92f + 0.14f * sin(i * 1.7f + phase)
            val cLow = Color(wetRock.r * shade, wetRock.g * shade, wetRock.b * shade, 1f)
            val cMid = Color(midRock.r * shade, midRock.g * shade, midRock.b * shade, 1f)
            val cTop = Color(dryRock.r * shade, dryRock.g * shade, dryRock.b * shade, 1f)
            // Counter-clockwise winding when viewed from outside (+radial outward normal).
            addFacet(low[next], low[i], mid[i], cLow)
            addFacet(low[next], mid[i], mid[next], cLow)
            addFacet(mid[next], mid[i], top[i], cMid)
            addFacet(mid[next], top[i], top[next], cMid)
            addFacet(top[next], top[i], apex, cTop)
        }
    }
    // Gently sloping sandy shore: y=+1.6 at z=-14 down to y=-3.2 at z=+14.
    addQuad(
        Vec3f(-24f, 1.6f, -14f),
        Vec3f(24f, 1.6f, -14f),
        Vec3f(24f, -3.2f, 14f),
        Vec3f(-24f, -3.2f, 14f),
        Vec3f(0f, 0.985f, 0.17f),
        Color(0.74f, 0.65f, 0.49f, 1f),
    )
    // Primary river boulder + smaller companion stone emerging through the water surface.
    addBoulder(cx = -0.35f, cz = 1.4f, baseY = -2.1f, peakY = 1.45f, baseR = 2.65f, topR = 1.15f, phase = 0.25f)
    addBoulder(cx = 3.35f, cz = 0.15f, baseY = -1.7f, peakY = 0.78f, baseR = 1.45f, topR = 0.60f, phase = 1.65f)
    return MeshGeometry(verts.toFloatArray(), indices.toIntArray(), VertexFormat.PositionNormalColor)
}

private suspend fun openDepthWaterSession(
    backend: HeadlessUiBackend,
    waterFeature: ContentFeatureSource?,
    size: Int,
): HeadlessRenderSession {
    val plan = RenderPlan(
        primary = ScenePipeline(
            PipelineKey.Primary,
            PackShaderSets.LitShadow,
            VertexFormat.PositionNormalColor,
            materialBindings = GroupBindings.UniformOnlyMaterial,
            buildTransparent = true,
        ),
        contentFeatures = listOfNotNull(waterFeature),
        depthPrePassShaderSet = PackShaderSets.ShadowDepth,
        sceneDepthShaderSet = PackShaderSets.SceneDepth,
    )
    if (backend == HeadlessUiBackend.WebGpu) return webGpuHeadlessPlan(plan, surface = HeadlessSurface(size, size))
    val ready = CompletableDeferred<Renderer>()
    val app = AppSpecBuilder().apply { ready { ready.complete(it) } }.build().createLifecycle()
    val engine = VulkanEngine(app, plan)
    engine.create(HeadlessSurface(size, size))
    val renderer = withTimeout(DEPTH_BOOT_TIMEOUT_MS) { ready.await() }
    return object : HeadlessRenderSession {
        override val renderer = renderer
        override fun close() = engine.dispose()
    }
}

/**
 * Renders flowing stylized water around a boulder and sloped sandy shoreline using `SceneDepth` contact foam.
 */
@Suppress("LongParameterList")
fun renderStylizedWaterWithRockScene(
    backend: HeadlessUiBackend,
    quality: StylizedWaterQuality = StylizedWaterQuality.Standard,
    samplesDepth: Boolean = true,
    timeSeconds: Float = 1.2f,
    size: Int = SCENE_SIZE,
    attachDynamically: Boolean = false,
): ByteArray {
    val look = StylizedWaterLook(
        enabled = true,
        quality = quality,
        timeSeconds = timeSeconds,
        waveScale = 0.50f,
        waveSpeed = 1.15f,
        flowDirectionX = 0.65f,
        flowDirectionZ = 0.20f,
        contactFoamWidth = 1.10f,
        contactFoamStrength = 0.92f,
    )
    val feature = stylizedWaterContentFeature(
        geometry = horizontalPlaneGeometry(24f),
        look = look,
        name = "stylized_water_rock_test",
        samplesDepth = samplesDepth,
    )
    return runBlocking {
        openDepthWaterSession(backend, if (attachDynamically) null else feature, size)
    }.use { session ->
        val r = session.renderer
        val attached = if (attachDynamically) {
            val host = checkNotNull(r as? ContentFeatureHost) { "Backend renderer must implement ContentFeatureHost." }
            check(host.supportsSceneDepth) { "$backend: ContentFeatureHost.supportsSceneDepth must be true when sceneDepthShaderSet is configured." }
            runBlocking { host.attachContentFeature(feature) }
        } else {
            null
        }
        try {
            renderDepthWaterFrame(r, size)
        } finally {
            r.waitIdle()
            attached?.detach()
        }
    }
}

private fun renderDepthWaterFrame(r: Renderer, size: Int): ByteArray {
    val target = r.createRenderTarget(size, size)
    val rockMesh = r.createMesh(rockAndRiverbedGeometry())
    val rockMaterial = r.createMaterial(LitShadowUniformLayout)
    try {
        val lens = Lens(eye = Vec3f(0f, 6.2f, 12.5f), center = Vec3f(0f, 0f, -1.5f), fovYRadians = 0.85f, near = 0.2f, far = 150f)
        val draws = listOf(RenderDrawCommand(mesh = rockMesh, material = rockMaterial, model = Mat4()))
        r.renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = draws,
                light = SceneLight(direction = DAY_SUN, color = Vec3f(1f, 0.96f, 0.88f), ambient = 0.35f),
                environment = EnvironmentUniforms(
                    showSky = true,
                    horizonColor = DAY_SKY_HORIZON,
                    zenithColor = DAY_SKY_ZENITH,
                    fogColor = DAY_SKY_HORIZON,
                    fogStart = 28f,
                    fogEnd = 110f,
                    shadowsEnabled = false,
                    clearColor = DAY_SKY_HORIZON,
                ),
                clipSpace = r.clipSpace,
                aspect = 1f,
                drawPreparer = (r as GpuDrawPreparationSource).gpuDrawPreparer,
            ),
        )
        return runBlocking { r.readPixels(target) }.data
    } finally {
        r.waitIdle()
        rockMesh.destroy()
        rockMaterial.destroy()
        target.destroy()
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
        "scene-stylized-water-rock-foam",
        "Stylized water with SceneDepth foam: lacy cellular contact foam, shallow absorption, and underwater caustics",
        size,
        renderStylizedWaterWithRockScene(HeadlessUiBackend.Vulkan, StylizedWaterQuality.Standard, samplesDepth = true, size = size),
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
