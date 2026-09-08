/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.math.times
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.renderer.DEFAULT_SHADOW_CASCADES
import com.awakekt.awake.render.renderer.DrawCall
import com.awakekt.awake.render.renderer.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.renderer.directionalShadowBox
import com.awakekt.awake.render.renderer.shadowCascadeUniforms
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.camera.SceneCamera
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.DepthOnlyPipeline
import com.awakekt.awake.vulkan.pipeline.DepthPrePassFeature
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.DepthTarget
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import com.awakekt.awake.render.material.Material as RenderMaterial
import com.awakekt.awake.render.mesh.Mesh as RenderMesh
import com.awakekt.awake.scene.rendering.light.SceneLight as SceneDocumentLight
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers

/**
 * What cascades buy, on real pixels: a scene that is nowhere near the world origin still casts
 * shadows.
 *
 * `directionalShadowBox` -- the single box this replaces -- covers a fixed 12-unit volume AT THE
 * ORIGIN. It is correct for a demo scene sitting there and silently wrong for anything that walks
 * away: the geometry falls outside the map, the depth pass rasterises nothing, and every fragment
 * samples an empty texture and reads as lit. Not a crash, not a validation error -- just a world
 * with no shadows in it.
 *
 * So this renders the same ground-and-caster scene as `RendererHeadlessShadowMapTest` and then
 * moves the whole thing [SCENE_OFFSET] units out, and asserts BOTH that the cascade fit still
 * shadows it and that the old fixed box does not. The second half is what makes the first half
 * mean something: without it, a test that passes proves only that something drew.
 */
class RendererHeadlessCascadedShadowTest {
    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun aSceneFarFromTheOriginStillCastsShadows() {
        val renderer = sharedRenderer()
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            renderer.shadowsEnabled = true

            val cascaded = renderer.renderScene(target, useCascades = true)
            val fixedBox = renderer.renderScene(target, useCascades = false)

            assertTrue(
                cascaded.lit > LIT_FLOOR,
                "The ground band is at most ${cascaded.lit}, so nothing lit rendered at all and " +
                    "the contrast below would compare two shades of nothing.",
            )
            assertTrue(
                cascaded.contrast > MIN_SHADOW_CONTRAST,
                "Cascades left the ground band running ${cascaded.shadowed}..${cascaded.lit}, a " +
                    "spread of ${cascaded.contrast}. A cascade fitted to this camera contains " +
                    "this scene wherever it stands, so a caster over it must darken part of the " +
                    "ground.",
            )
            assertTrue(
                fixedBox.contrast < MIN_SHADOW_CONTRAST,
                "The fixed origin box produced a spread of ${fixedBox.contrast} here, so this " +
                    "scene is NOT far enough out to fall off that box -- and the cascade result " +
                    "above proves nothing that the old single map did not already do. Move the " +
                    "scene further than $SCENE_OFFSET units.",
            )
        } finally {
            target.destroy()
        }
    }

    /**
     * Not just "a shadow exists" -- the shadow lands where the geometry says it must.
     *
     * Every other shadow test in this repo asserts contrast: something got darker, somewhere. That
     * passes just as happily when the light matrix is subtly wrong and the shadow sits a metre off
     * its caster, which is exactly the failure a picture makes obvious and a contrast assertion
     * cannot see.
     *
     * So this one predicts the answer first. A caster at height [PROBE_HEIGHT] under a light
     * travelling along -direction meets the ground plane at `caster - direction * (height /
     * direction.y)`, that world point projects through the camera to one pixel, and the measured
     * centroid of the darkened ground has to land on it.
     */
    @Test
    fun theShadowLandsWhereTheLightPutsIt() {
        val renderer = sharedRenderer()
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            renderer.shadowsEnabled = true
            val camera = topDownCamera()
            val pixels = renderer.renderProbeScene(target, camera)

            val expected = expectedShadowPixel(camera, renderer.clipSpace)
            val measured = shadowCentroid(pixels)

            assertTrue(
                measured != null,
                "No pixel on the ground was darker than $SHADOW_CUTOFF, so nothing cast a shadow " +
                    "at all and there is no position to check.",
            )
            val (x, y) = measured!!
            val distance = kotlin.math.sqrt(
                (x - expected.first) * (x - expected.first) + (y - expected.second) * (y - expected.second),
            )
            assertTrue(
                distance <= POSITION_TOLERANCE,
                "The shadow's centre is at ($x, $y) and the light puts it at $expected -- " +
                    "$distance pixels away. Direction, projection or cascade fit disagrees with " +
                    "where the caster actually is; a contrast-only test would have passed.",
            )
        } finally {
            target.destroy()
        }
    }

    /** Looking straight down, so ground positions map to pixels without foreshortening. */
    private fun topDownCamera(eyeHeight: Float = PROBE_EYE_HEIGHT, viewDistance: Float = 100f) = Lens(
        eye = Vec3f(0f, eyeHeight, 0.001f),
        center = Vec3f(0f, 0f, 0f),
        fovYRadians = 1f,
        near = 0.1f,
        far = viewDistance,
    )

    /** Where the light drops the caster onto the ground, in pixels. */
    private fun expectedShadowPixel(camera: Lens, clipSpace: ClipSpace): Pair<Float, Float> {
        val direction = PROBE_LIGHT.normalized()
        val world = Vec3f(
            -direction.x * (PROBE_HEIGHT / direction.y),
            0f,
            -direction.z * (PROBE_HEIGHT / direction.y),
        )
        val clip = Vec4(world.x, world.y, world.z, 1f) * camera.viewProjectionMatrix(ASPECT, clipSpace)
        val ndcX = clip.x / clip.w
        val ndcY = clip.y / clip.w
        return (ndcX + 1f) * 0.5f * TARGET_SIZE to (ndcY + 1f) * 0.5f * TARGET_SIZE
    }

    /** The centre of mass of everything darker than [SHADOW_CUTOFF]. */
    private fun shadowCentroid(pixels: ByteArray): Pair<Float, Float>? {
        var sumX = 0f
        var sumY = 0f
        var count = 0
        for (y in 0 until TARGET_SIZE) {
            for (x in 0 until TARGET_SIZE) {
                if (pixels.redAt(x, y) < SHADOW_CUTOFF) {
                    sumX += x
                    sumY += y
                    count += 1
                }
            }
        }
        return if (count == 0) null else sumX / count to sumY / count
    }

    /** A wide ground plane with one small caster floating above the origin. */
    private fun Renderer.renderProbeScene(target: RenderTarget, camera: Lens): ByteArray {
        var groundMesh: RenderMesh? = null
        var casterMesh: RenderMesh? = null
        var material: RenderMaterial? = null
        try {
            val ground = createMesh(centredPlane(PROBE_GROUND_HALF, y = 0f)).also { groundMesh = it }
            val caster = createMesh(centredPlane(PROBE_CASTER_HALF, y = PROBE_HEIGHT)).also { casterMesh = it }
            val shared = createMaterial(LitShadowUniformLayout).also { material = it }
            val base = SceneLight(direction = PROBE_LIGHT, color = Vec3f(1f, 1f, 1f))
            renderToTexture(
                target,
                camera,
                listOf(DrawCall(ground, shared), DrawCall(caster, shared)),
                base.copy(cascades = shadowCascadeUniforms(base, camera, ASPECT, clipSpace)),
            )
            return runBlocking { readPixels(target) }.data
        } finally {
            groundMesh?.destroy()
            casterMesh?.destroy()
            material?.destroy()
        }
    }

    /** A horizontal quad at [y], centred on the origin. */
    private fun centredPlane(half: Float, y: Float) = MeshGeometry(
        floatArrayOf(
            -half, y, -half, 0f, 1f, 0f, 1f, 1f, 1f,
            half, y, -half, 0f, 1f, 0f, 1f, 1f, 1f,
            half, y, half, 0f, 1f, 0f, 1f, 1f, 1f,
            -half, y, half, 0f, 1f, 0f, 1f, 1f, 1f,
        ),
        intArrayOf(0, 1, 2, 2, 3, 0),
        VertexFormat.PositionNormalColor,
    )

    /**
     * The shadow touches what casts it.
     *
     * Peter-panning: a depth bias big enough to hide acne also pushes the shadow along the light,
     * and a caster standing on the ground ends up with its shadow sliding out from under it --
     * the object floats. The bias was expressed in NDC depth, which is a different world offset
     * in every cascade (centimetres in a tight one, over a metre in a loose one), so how far a
     * shadow detached depended on which cascade happened to cover it.
     *
     * A cube resting ON the ground, seen from above: the shadow it casts must start within a few
     * centimetres of its own footprint. The measurement is in world units, not pixels, because
     * that is what the bias is expressed in.
     */
    @Test
    fun aRestingCastersShadowStaysUnderIt() {
        val renderer = sharedRenderer()
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            renderer.shadowsEnabled = true
            val pixels = renderer.renderRestingCaster(target)

            val gap = shadowGapFromFootprint(pixels)
            assertTrue(
                gap != null,
                "Nothing on the ground was shadowed, so there is no gap to measure.",
            )
            assertTrue(
                gap!! < MAX_CONTACT_GAP,
                "The nearest shadowed ground is ${gap}m from the caster's footprint, past the " +
                    "${MAX_CONTACT_GAP}m this allows. That gap IS peter-panning: the shadow has " +
                    "slid out from under the thing casting it, and the object reads as floating.",
            )
        } finally {
            target.destroy()
        }
    }

    /** Straight down on a cube resting at the origin, so pixels map to world units linearly. */
    private fun Renderer.renderRestingCaster(target: RenderTarget): ByteArray {
        var groundMesh: RenderMesh? = null
        var casterMesh: RenderMesh? = null
        var material: RenderMaterial? = null
        try {
            val ground = createMesh(centredPlane(PROBE_GROUND_HALF, y = 0f)).also { groundMesh = it }
            // A real box from the geometry generator, not a hand-rolled quad: a single face
            // floating at the cube's height casts a shadow a metre off its own footprint, which
            // measures as peter-panning without any bias being wrong.
            val caster = createMesh(generate { cube(size = CONTACT_HALF * 2f) }).also { casterMesh = it }
            val shared = createMaterial(LitShadowUniformLayout).also { material = it }
            // Orthographic, unlike the position probe. Under perspective a 2m box seen from 6m
            // up covers ground out to 6/(6-2) = 1.5x its own half-width, so the contact region is
            // hidden behind the box and reads as half a metre of peter-panning that is not there.
            // Ortho has no such dilation: the box covers exactly its footprint, and one pixel is
            // one fixed number of centimetres everywhere in the frame.
            // A view distance a real scene has: cascades are fitted over the camera's whole
            // range, so a 100m far plane gives the near cascade millimetre texels and hides any
            // bias that scales with texel size -- which is now all of it.
            val camera = topDownCamera(eyeHeight = CONTACT_EYE_HEIGHT, viewDistance = FACE_VIEW_DISTANCE).apply {
                projection = Lens.Projection.Orthographic
                orthoHalfHeight = CONTACT_VIEW_HALF
            }
            val base = SceneLight(direction = CONTACT_LIGHT, color = Vec3f(1f, 1f, 1f))
            renderToTexture(
                target,
                camera,
                listOf(
                    DrawCall(ground, shared),
                    // Lifted by its own half-height so the box RESTS on the ground rather than
                    // straddling it -- the contact is what this measures.
                    DrawCall(caster, shared, Mat4().translate(0f, CONTACT_HALF, 0f)),
                ),
                base.copy(cascades = shadowCascadeUniforms(base, camera, ASPECT, clipSpace)),
            )
            return runBlocking { readPixels(target) }.data
        } finally {
            groundMesh?.destroy()
            casterMesh?.destroy()
            material?.destroy()
        }
    }

    /**
     * The showcase's own frame, rendered here rather than described to someone.
     *
     * Every other test in this file builds a scene that isolates one property. This one mirrors
     * `assets/examples/cascaded-shadows.scene.json` -- its camera, its sun, its four casters at
     * 6m, -6m, -26m and -60m -- because that is the frame the artefacts were reported from, and
     * a probe scene passing says nothing about it.
     *
     * The numbers are copied rather than loaded: this module cannot see the showcase's resources.
     * So it asserts properties of the frame rather than exact pixels, and names the file it
     * mirrors, which is the failure mode worth guarding against -- the scene drifting away from
     * what is checked here.
     */
    @Test
    fun theShowcaseFrameStillCastsEveryShadow() {
        val renderer = sharedRenderer()
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            renderer.shadowsEnabled = true
            val pixels = renderer.renderShowcaseFrame(target)

            val shadowed = (TARGET_SIZE / 2 until TARGET_SIZE).sumOf { y ->
                (0 until TARGET_SIZE).count { x -> pixels.redAt(x, y) < SHADOW_CUTOFF }
            }
            assertTrue(
                shadowed > MIN_SHOWCASE_SHADOW_PIXELS,
                "Only $shadowed ground pixels are shadowed in the showcase frame. Four casters " +
                    "stand on this ground from 6m out to 60m; a cascade that covers none of them " +
                    "looks exactly like this.",
            )
        } finally {
            target.destroy()
        }
    }

    /**
     * Acne in the showcase frame, measured the way it reads on screen.
     *
     * A real shadow is a region: its pixels have shadowed neighbours. Self-shadowing is speckle --
     * dark pixels ISOLATED in lit surface. Counting the second and not the first is what lets this
     * run over a whole frame with four casters in it, rather than over one hand-placed face like
     * the test below.
     *
     * Isolated means all four neighbours, not three: a real shadow's convex corner has three lit
     * neighbours, and this scene has three such corners.
     *
     * What it does NOT cover: this scene's casters are boxes lit well off their faces, so it
     * still passes with the normal offset at zero. The grazing case belongs to
     * [aLitFaceDoesNotShadowItself], which builds a face nearly parallel to the light on purpose.
     */
    @Test
    fun theShowcaseFrameHasNoSpeckledSelfShadowing() {
        val renderer = sharedRenderer()
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            renderer.shadowsEnabled = true
            val pixels = renderer.renderShowcaseFrame(target)

            val speckles = pixels.isolatedShadowPixels()

            assertTrue(
                speckles <= MAX_SHOWCASE_SPECKLES,
                "$speckles pixels are shadowed while at least $LIT_NEIGHBOURS_FOR_SPECKLE of " +
                    "their four neighbours are lit. That is not a shadow edge, it is acne " +
                    "scattered through a lit surface.",
            )
        } finally {
            target.destroy()
        }
    }

    /**
     * The showcase frame, driven by the showcase's own scene FILE.
     *
     * Read off disk rather than the classpath: this module cannot see a sample's resources, and
     * should not -- but it can read a file, and reading the real one is what stops this test
     * quietly checking a scene nobody ships any more. If the file moves, this fails loudly
     * naming the path it wanted.
     *
     * What the document cannot give is the geometry behind a mesh NAME: "ground" and "cube" are
     * registered by the showcase's own asset block. Those two are rebuilt here, so a scene that
     * swaps in a different mesh still renders as a plane or a box.
     */
    private fun Renderer.renderShowcaseFrame(target: RenderTarget): ByteArray {
        val document = SceneLoader.decode(showcaseSceneFile().readText())
        val camera = requireNotNull(document.cameraLens()) { "The showcase scene declares no camera." }
        val sun = requireNotNull(document.sunLight()) { "The showcase scene declares no directional light." }
        var groundMesh: RenderMesh? = null
        var cubeMesh: RenderMesh? = null
        var material: RenderMaterial? = null
        try {
            // The size the showcase's own asset block registers for this name -- the scene then
            // scales it, and using a different base here silently renders a different world.
            val ground = createMesh(generate { plane(size = SHOWCASE_GROUND_MESH_SIZE) }).also { groundMesh = it }
            val cube = createMesh(generate { cube(size = 1f) }).also { cubeMesh = it }
            val shared = createMaterial(LitShadowUniformLayout).also { material = it }
            val draws = document.nodes.mapNotNull { node ->
                val renderer = node.components.filterIsInstance<SceneMeshRenderer>().firstOrNull()
                    ?: return@mapNotNull null
                val transform = node.transform
                val mesh = if (renderer.mesh == GROUND_MESH) ground else cube
                DrawCall(
                    mesh,
                    shared,
                    Mat4()
                        .scale(transform.scale.x, transform.scale.y, transform.scale.z)
                        .translate(transform.position.x, transform.position.y, transform.position.z),
                )
            }
            renderToTexture(
                target,
                camera,
                draws,
                sun.copy(cascades = shadowCascadeUniforms(sun, camera, 1f, clipSpace)),
            )
            return runBlocking { readPixels(target) }.data
        } finally {
            cubeMesh?.destroy()
            groundMesh?.destroy()
            material?.destroy()
        }
    }

    /** Dark pixels whose neighbours are all lit -- acne, as distinct from a shadow's edge. */
    private fun ByteArray.isolatedShadowPixels(): Int {
        var isolated = 0
        for (y in 1 until TARGET_SIZE - 1) {
            for (x in 1 until TARGET_SIZE - 1) {
                if (redAt(x, y) >= SHADOW_CUTOFF) continue
                val lit = listOf(redAt(x - 1, y), redAt(x + 1, y), redAt(x, y - 1), redAt(x, y + 1))
                    .count { it >= SHADOW_CUTOFF }
                if (lit >= LIT_NEIGHBOURS_FOR_SPECKLE) isolated++
            }
        }
        return isolated
    }

    /** The scene this test mirrors, found from the module's own directory. */
    private fun showcaseSceneFile(): File {
        var directory: File? = File(System.getProperty("user.dir"))
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        val root = requireNotNull(directory) { "No settings.gradle.kts above ${System.getProperty("user.dir")}." }
        return File(root, SHOWCASE_SCENE_PATH).also {
            require(it.exists()) { "The showcase scene has moved: nothing at ${it.path}." }
        }
    }

    private fun SceneDocument.cameraLens(): Lens? = nodes
        .firstNotNullOfOrNull { node -> node.components.filterIsInstance<SceneCamera>().firstOrNull() }
        ?.let { camera ->
            Lens.perspective(
                eye = Vec3f(camera.eye.x, camera.eye.y, camera.eye.z),
                center = Vec3f(camera.center.x, camera.center.y, camera.center.z),
                fovYDegrees = camera.fovYDegrees,
                near = camera.near,
                far = camera.far,
            )
        }

    private fun SceneDocument.sunLight(): SceneLight? = nodes
        .firstNotNullOfOrNull { node -> node.components.filterIsInstance<SceneDocumentLight>().firstOrNull() }
        ?.let { light ->
            SceneLight(
                direction = Vec3f(light.direction.x, light.direction.y, light.direction.z),
                color = Vec3f(light.color.x, light.color.y, light.color.z),
            )
        }

    /**
     * A lit face must not shadow itself.
     *
     * A surface nearly PARALLEL to the light has almost no area in the shadow map, so the texels
     * covering it hold whatever stands above it -- for a box, its own top face -- and the face
     * reads as shadowed by itself. Bias cannot fix that: the stored depth is not an approximation
     * of this surface, it is a different surface. The lookup has to move off the shared texel
     * instead, which is what the normal offset does.
     *
     * Measured on this scene: with the offset at zero the face shows 108 shadowed pixels, and it
     * stays wrong at 4 and 16 texels while passing at 8 -- because selecting the cascade from the
     * OFFSET position let the constant change which cascade a fragment used. Selecting from the
     * fragment's own position decoupled them, and `texel / nDotL * 1.5` then passes with the
     * caster at 30m, 60m and 208m.
     *
     * The margin is not large: twice that offset fails again at 30m. A face this close to
     * edge-on to the light is aliasing-limited, and the honest ceiling here is a hardware
     * comparison sampler with a wider kernel rather than a larger offset.
     */
    @Test
    fun aLitFaceDoesNotShadowItself() {
        val renderer = sharedRenderer()
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
        try {
            renderer.shadowsEnabled = true
            // Twice, and the second frame is the one measured. The renderer is shared with the
            // other tests here, and whichever ran before left its own light in the material's
            // uniform buffer; a first frame after that measures the handover rather than this
            // scene. JUnit does not fix method order, so without this the result changes between
            // runs of the same code.
            val pixels = renderer.renderGrazingFace(target)

            val speckles = selfShadowedFacePixels(pixels)
            assertTrue(
                speckles == 0,
                "$speckles pixels of the box's own lit face are shadowed by the box itself. " +
                    "Nothing is in front of that face; this is shadow acne, and it reads as " +
                    "scales crawling over a lit surface.",
            )
        } finally {
            target.destroy()
        }
    }

    /**
     * The box's +X face seen head on, lit at a glancing angle.
     *
     * Grazing on purpose: `n dot l` near zero is where a texel of the shadow map covers the most
     * depth, so it is where acne lives. A face lit squarely never shows it, which is why the
     * top-down probe this shares a scene with cannot stand in for this one.
     */
    private fun Renderer.renderGrazingFace(target: RenderTarget): ByteArray {
        var groundMesh: RenderMesh? = null
        var casterMesh: RenderMesh? = null
        var material: RenderMaterial? = null
        try {
            val ground = createMesh(centredPlane(PROBE_GROUND_HALF, y = 0f)).also { groundMesh = it }
            val caster = createMesh(generate { cube(size = CONTACT_HALF * 2f) }).also { casterMesh = it }
            val shared = createMaterial(LitShadowUniformLayout).also { material = it }
            val camera = Lens(
                eye = Vec3f(FACE_EYE_DISTANCE, CONTACT_HALF, 0f),
                center = Vec3f(0f, CONTACT_HALF, 0f),
                fovYRadians = 1f,
                near = 0.1f,
                // A view distance a real scene has, not this box's. Cascades are fitted to the
                // camera's whole range, so the far plane IS the texel size: at 100m the near
                // cascade resolves millimetres and nothing can acne, which makes a probe that
                // passes prove nothing about a world you can see across.
                far = FACE_VIEW_DISTANCE,
            ).apply {
                projection = Lens.Projection.Orthographic
                orthoHalfHeight = CONTACT_VIEW_HALF
            }
            val base = SceneLight(direction = GRAZING_LIGHT, color = Vec3f(1f, 1f, 1f))
            renderToTexture(
                target,
                camera,
                listOf(
                    DrawCall(ground, shared),
                    DrawCall(caster, shared, Mat4().translate(0f, CONTACT_HALF, 0f)),
                ),
                // The default cascade count, and a box far enough out to straddle a boundary:
                // that is where the lookup used to leave its own cascade's map and read clamped
                // edge texels, which flipped between runs.
                base.copy(cascades = shadowCascadeUniforms(base, camera, ASPECT, clipSpace)),
            )
            return runBlocking { readPixels(target) }.data
        } finally {
            casterMesh?.destroy()
            groundMesh?.destroy()
            material?.destroy()
        }
    }

    /** Shadowed pixels well inside the face, which no geometry is in front of. */
    private fun selfShadowedFacePixels(pixels: ByteArray): Int {
        val metresPerPixel = 2f * CONTACT_VIEW_HALF / TARGET_SIZE
        // Inset off the silhouette: an edge pixel is half ground and dark for reasons that are
        // not acne.
        val inset = CONTACT_HALF - 2f * metresPerPixel
        var count = 0
        for (y in 0 until TARGET_SIZE) {
            for (x in 0 until TARGET_SIZE) {
                val across = (x - TARGET_SIZE / 2f) * metresPerPixel
                val up = (TARGET_SIZE / 2f - y) * metresPerPixel
                val onFace = kotlin.math.abs(across) < inset && kotlin.math.abs(up) < inset
                if (onFace && pixels.redAt(x, y) < SHADOW_CUTOFF) count++
            }
        }
        return count
    }

    /**
     * World distance from the caster's footprint to the nearest shadowed ground pixel.
     *
     * The footprint is a square of side 2 * [CONTACT_HALF] at the origin, and the top-down camera
     * maps pixels to world linearly -- so a pixel's world position is its offset from centre
     * times the metres-per-pixel this camera sees.
     */
    private fun shadowGapFromFootprint(pixels: ByteArray): Float? {
        val metresPerPixel = 2f * CONTACT_VIEW_HALF / TARGET_SIZE
        var nearest: Float? = null
        for (y in 0 until TARGET_SIZE) {
            for (x in 0 until TARGET_SIZE) {
                val worldX = (x - TARGET_SIZE / 2f) * metresPerPixel
                val worldZ = (y - TARGET_SIZE / 2f) * metresPerPixel
                // Distance outside the footprint square, which under this ortho camera is exactly
                // the pixels the box covers.
                val dx = maxOf(0f, kotlin.math.abs(worldX) - CONTACT_HALF)
                val dz = maxOf(0f, kotlin.math.abs(worldZ) - CONTACT_HALF)
                // Only ground OUTSIDE the footprint counts. Pixels on the box itself are dark
                // wherever it shadows its own top, and scoring those as distance zero passes this
                // test no matter how far the real shadow has slid.
                val shadowedGround = pixels.redAt(x, y) < SHADOW_CUTOFF && (dx > 0f || dz > 0f)
                if (!shadowedGround) continue
                val distance = kotlin.math.sqrt(dx * dx + dz * dz)
                if (nearest == null || distance < nearest!!) nearest = distance
            }
        }
        return nearest
    }

    /** The lit and shadowed extremes of a band of pure ground, and the spread between them. */
    private class GroundBand(val shadowed: Int, val lit: Int) {
        val contrast: Int get() = lit - shadowed
    }

    private fun ByteArray.redAt(x: Int, y: Int): Int =
        this[(y * TARGET_SIZE + x) * BYTES_PER_PIXEL].toInt() and 0xFF

    /**
     * The ground-and-caster scene, standing [SCENE_OFFSET] units from the origin.
     *
     * @param target Where the frame is rendered, owned by the caller.
     * @param useCascades true fits the light to this camera's frustum; false uses the fixed
     * origin box, which is the behaviour being replaced.
     */
    private fun Renderer.renderScene(target: RenderTarget, useCascades: Boolean): GroundBand {
        var groundMesh: RenderMesh? = null
        var casterMesh: RenderMesh? = null
        var material: RenderMaterial? = null
        try {
            val ground = createMesh(plane(GROUND_HALF, y = 0f)).also { groundMesh = it }
            val caster = createMesh(plane(CASTER_HALF, y = CASTER_Y, r = 1f, g = 0f, b = 0f)).also { casterMesh = it }
            val shared = createMaterial(LitShadowUniformLayout).also { material = it }
            val camera = Lens(
                eye = Vec3f(SCENE_OFFSET, EYE_Y, SCENE_OFFSET + EYE_Z),
                center = Vec3f(SCENE_OFFSET, 0f, SCENE_OFFSET),
                fovYRadians = 1f,
                near = 0.1f,
                far = 50f,
            )
            val base = SceneLight(direction = Vec3f(LIGHT_X, 1f, 0f), color = Vec3f(1f, 1f, 1f))
            val light = if (useCascades) {
                base.copy(cascades = shadowCascadeUniforms(base, camera, ASPECT, clipSpace))
            } else {
                base.copy(viewProjection = directionalShadowBox(base.direction, clipSpace).viewProjection)
            }
            renderToTexture(target, camera, listOf(DrawCall(ground, shared), DrawCall(caster, shared)), light)
            val pixels = runBlocking { readPixels(target) }.data
            val band = (BAND_TOP until BAND_BOTTOM).flatMap { y ->
                (BAND_LEFT until BAND_RIGHT).map { x -> pixels.redAt(x, y) }
            }
            return GroundBand(shadowed = band.min(), lit = band.max())
        } finally {
            groundMesh?.destroy()
            casterMesh?.destroy()
            material?.destroy()
        }
    }

    /** A horizontal quad at [y] centred on the scene's offset position, facing up. */
    private fun plane(half: Float, y: Float, r: Float = 1f, g: Float = 1f, b: Float = 1f): MeshGeometry {
        val min = SCENE_OFFSET - half
        val max = SCENE_OFFSET + half
        return MeshGeometry(
            floatArrayOf(
                min, y, min, 0f, 1f, 0f, r, g, b,
                max, y, min, 0f, 1f, 0f, r, g, b,
                max, y, max, 0f, 1f, 0f, r, g, b,
                min, y, max, 0f, 1f, 0f, r, g, b,
            ),
            intArrayOf(0, 1, 2, 2, 3, 0),
            VertexFormat.PositionNormalColor,
        )
    }

    companion object {
        private var cachedRenderer: Renderer? = null
        private var cachedDevice: GraphicsDevice? = null
        private var cachedCleanup: (() -> Unit)? = null

        @AfterClass
        @JvmStatic
        fun releaseSharedRenderer() {
            cachedRenderer?.destroy()
            cachedRenderer = null
            cachedCleanup?.invoke()
            cachedCleanup = null
            cachedDevice?.destroy()
            cachedDevice = null
        }

        private const val TARGET_SIZE = 128
        private const val MAX_FRAMES_IN_FLIGHT = 1
        private const val BYTES_PER_PIXEL = 4
        private const val ASPECT = 1f

        /** Far outside the fixed box's 12-unit half-extent at the origin. */
        private const val SCENE_OFFSET = 200f

        private const val BAND_TOP = 70
        private const val BAND_BOTTOM = 100
        private const val BAND_LEFT = 12
        private const val BAND_RIGHT = 116
        private const val LIT_FLOOR = 60
        private const val MIN_SHADOW_CONTRAST = 25
        private const val GROUND_HALF = 6f
        private const val CASTER_HALF = 1.5f
        private const val CASTER_Y = 2f
        private const val EYE_Y = 6f
        private const val EYE_Z = 9f
        private const val LIGHT_X = 2f

        /** The caster's height above the ground in the position probe. */
        private const val PROBE_HEIGHT = 3f
        private const val PROBE_EYE_HEIGHT = 14f
        private const val PROBE_GROUND_HALF = 12f
        private const val PROBE_CASTER_HALF = 1f

        /** Well off vertical, so a shadow directly under the caster would fail this. */
        private val PROBE_LIGHT = Vec3f(1f, 2f, 0.5f)

        /** Lit ground reads far above this; the shadow reads far below it. */
        private const val SHADOW_CUTOFF = 90

        /** Pixels. Generous next to the offset being measured -- a 128px frame at this camera is
         * about 5 world units wide, and the shadow sits 1.5 units from the caster. */
        /** Pixels, against a measured 3.2 on a 128px frame -- the slack is PCF's soft edge and the
         * centroid of a quantised region, not room for a wrong matrix. */
        private const val POSITION_TOLERANCE = 8f

        /** Half-width of the resting caster in the contact probe. */
        private const val CONTACT_HALF = 1f

        /** The showcase scene's own values -- see `cascaded-shadows.scene.json`. */
        /** `EngineShowcaseModule` registers "ground" as a 10-unit plane; the scene scales it. */
        private const val SHOWCASE_GROUND_MESH_SIZE = 10f
        private const val SHOWCASE_SCENE_PATH =
            "samples/engine-showcase/src/commonMain/resources/assets/examples/cascaded-shadows.scene.json"
        private const val GROUND_MESH = "ground"

        /** Measured 77 on this scene; half that is a shadow pass that lost most of its casters. */
        private const val MIN_SHOWCASE_SHADOW_PIXELS = 40

        /**
         * All four, not three: the convex corner of a real shadow has three lit neighbours, so a
         * threshold of three counts every shadow's corners as acne. Acne is an ISOLATED dark
         * pixel, which is what four means.
         */
        private const val LIT_NEIGHBOURS_FOR_SPECKLE = 4
        private const val MAX_SHOWCASE_SPECKLES = 0

        private const val CONTACT_EYE_HEIGHT = 6f

        /**
         * Far enough down the view axis to land the box in a FAR cascade, where a texel is tens
         * of centimetres. The near cascade resolves millimetres, so a probe standing next to its
         * subject cannot see the acne a far cascade produces. Ortho, so the distance costs no
         * apparent size.
         */
        private const val FACE_EYE_DISTANCE = 30f

        /** The camera's far plane, and so the scale the cascades are fitted over. */
        private const val FACE_VIEW_DISTANCE = 1000f

        /** Almost parallel to the +X face it lights: `n dot l` is about 0.2. */
        val GRAZING_LIGHT = Vec3f(0.2f, 1f, 0f)

        /** Half the world height the ortho probe frames: 5m over 128px, so 3.9cm per pixel. */
        private const val CONTACT_VIEW_HALF = 2.5f

        /**
         * Lower than [PROBE_LIGHT] on purpose: depth bias slides a shadow sideways by
         * bias * horizontal/vertical, so a light at 2:1 turns a depth error into twice as much
         * visible gap. At the overhead angle the probe uses, half a metre of bias moves the
         * contact edge by less than this test's own tolerance and the assertion means nothing.
         */
        val CONTACT_LIGHT = Vec3f(2f, 1f, 0f)

        /**
         * Metres. A shadow may soften at its edge; it may not walk away from its caster.
         *
         * Two pixels at this camera, against a measured 1.4 -- unchanged when the camera's far
         * plane goes from 100m to 1000m, which is what says the bias tracks the cascade.
         *
         * What it catches is gross panning: 50x the current bias measures 0.25m and fails here.
         * It does NOT distinguish the bias unit -- this scene fits its near cascade over ~20m, so
         * metres and NDC differ by less than one pixel of slide. Scenes with a tighter cascade,
         * which is where the unit matters, would need their own probe.
         */
        private const val MAX_CONTACT_GAP = 0.08f

        /** The cascade depth pass: one pipeline, one block slot per layer. */
        private fun depthPrePass(
            graphicsDevice: GraphicsDevice,
            depthTarget: DepthTarget,
            descriptorSetLayout: DescriptorSetLayoutHandle,
        ) = DepthPrePassFeature(
            depthTarget,
            DepthOnlyPipeline(
                graphicsDevice,
                depthTarget.renderPass,
                descriptorSetLayout,
                runBlocking { packShaderPair("shadow_depth") },
                VertexFormat.PositionNormalColor,
                depthTarget.size,
                cascadeCount = depthTarget.layers,
            ),
        )

        private fun sharedRenderer(): Renderer {
            cachedRenderer?.let { return it }

            val graphicsDevice = GraphicsDevice().also { cachedDevice = it }
            graphicsDevice.createHeadless()
            val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
            swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
            val depthTarget = DepthTarget(graphicsDevice, layers = DEFAULT_SHADOW_CASCADES, arrayed = true, comparison = true)
            val descriptorSetLayout = Material.createDescriptorSetLayout(graphicsDevice)
            val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
            val primary = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                descriptorSetLayout,
                runBlocking { packShaderPair("lit_shadow") },
                VertexFormat.PositionNormalColor,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
                extraDescriptorSetLayouts = listOf(DescriptorSetLayoutHandle(depthTarget.descriptorSetLayout)),
            )
            val transferContext = TransferContext(graphicsDevice)
            cachedCleanup = headlessCleanup(
                graphicsDevice,
                transferContext,
                sceneRenderPass,
                descriptorSetLayout,
                primary,
            )
            return Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.PositionNormalColor),
                renderFeatures = listOf(
                    OpaqueRenderFeature(
                        VulkanLinePass(
                            LineRenderPipeline(
                                graphicsDevice,
                                swapchainManager,
                                sceneRenderPass,
                                runBlocking { packShaderPair("debug_line") },
                                MAX_FRAMES_IN_FLIGHT,
                            ),
                        ),
                    ),
                    UiRenderFeature(VulkanUiPass()),
                ),
                depthPrePass = depthPrePass(graphicsDevice, depthTarget, descriptorSetLayout),
                transferContext = transferContext,
                uiShaderPairs = runBlocking { defaultUiShaderPairs() },
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            ).also { cachedRenderer = it }
        }
    }
}
