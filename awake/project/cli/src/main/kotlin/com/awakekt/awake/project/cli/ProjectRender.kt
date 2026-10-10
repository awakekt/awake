/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.FileSystemAssetSource
import com.awakekt.awake.core.io.createPlatformFileSystem
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.project.runtime.PROJECT_MANIFEST
import com.awakekt.awake.project.runtime.ProjectRenderPlan
import com.awakekt.awake.project.runtime.loadProject
import com.awakekt.awake.project.runtime.runProject
import com.awakekt.awake.render.capture.FramebufferAttachment
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.debug.debugSettings
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/** The GPU backend `awake render` draws with. */
internal enum class RenderBackend(val label: String) {
    Vulkan("vulkan"),
    WebGpu("webgpu"),
    ;

    companion object {
        /**
         * Vulkan, which needs no window or display, except on Windows, where Awake ships no Vulkan
         * library and WebGPU's comes with it.
         */
        fun default(): RenderBackend = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) WebGpu else Vulkan

        /** The backend called [name], or a usage error naming the ones there are. */
        fun named(name: String): RenderBackend =
            entries.firstOrNull { it.label == name.lowercase() } ?: throw UsageException("no backend '$name'; use vulkan or webgpu")
    }
}

/** What `awake render` shows: the lit frame, or one of the renderer's debug views. */
internal enum class RenderView(val label: String, val debugView: RenderDebugView, val wireframe: Boolean = false) {
    Lit("lit", RenderDebugView.Off),
    Clay("clay", RenderDebugView.Clay),
    Normals("normals", RenderDebugView.WorldNormals),
    Depth("depth", RenderDebugView.LinearDepth),
    Albedo("albedo", RenderDebugView.Albedo),
    Shadows("shadows", RenderDebugView.ShadowVisibility),
    JointWeights("joint-weights", RenderDebugView.JointWeights),
    Wireframe("wireframe", RenderDebugView.Off, wireframe = true),
    ;

    companion object {
        /** The view called [name], or a usage error naming the ones there are. */
        fun named(name: String): RenderView = entries.firstOrNull { it.label == name.lowercase() }
            ?: throw UsageException("no view '$name'; there are ${entries.joinToString { it.label }}")
    }
}

/**
 * What `awake render` draws: the scene in [scene], [frames] frames into play, [width] by [height]
 * pixels, with [backend], as [view] shows it from the camera called [camera], or the scene's primary
 * camera.
 */
internal class RenderRequest(
    val scene: File,
    val width: Int,
    val height: Int,
    val frames: Int,
    val backend: RenderBackend,
    val view: RenderView = RenderView.Lit,
    val camera: String? = null,
)

/** RGBA pixels, four bytes each, row 0 at the top. */
internal class RenderedImage(val width: Int, val height: Int, val rgba: ByteArray) {
    /** Writes this image to [file] as a PNG, alpha included. */
    fun writePng(file: File) {
        file.absoluteFile.parentFile?.mkdirs()
        file.writeBytes(png())
    }

    /** This image as a PNG, alpha included. */
    fun png(): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (pixel in 0 until width * height) {
            val at = pixel * CHANNELS
            fun byte(offset: Int) = rgba[at + offset].toInt() and BYTE
            image.setRGB(pixel % width, pixel / width, (byte(ALPHA) shl ALPHA_SHIFT) or (byte(0) shl RED_SHIFT) or (byte(1) shl GREEN_SHIFT) or byte(2))
        }
        val bytes = ByteArrayOutputStream()
        if (!ImageIO.write(image, "png", bytes)) throw CommandFailure("no PNG writer")
        return bytes.toByteArray()
    }

    private companion object {
        const val CHANNELS = 4
        const val ALPHA = 3
        const val BYTE = 0xFF
        const val ALPHA_SHIFT = 24
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
    }
}

/**
 * Plays one of [project]'s scenes headless, as a played project runs it, and reads back what its
 * primary camera sees. The scene is loaded as the project's entry scene would be, with Core's
 * capabilities and [ProjectRenderPlan], so the picture is the one a game built on the project shows
 * for the 3D scene. A scene canvas drawn over it isn't included.
 */
internal class ProjectRender(private val project: ProjectScenes) {
    fun render(request: RenderRequest): RenderedImage = runBlocking {
        val name = project.relative(request.scene)
        val loaded = try {
            loadProject(sceneAsEntry(name), physicsWorld = { createJoltPhysicsWorld() })
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            // Loading reports a project it can't play with several kinds of exception.
            throw CommandFailure("$name can't be played: ${error.message}", error)
        }
        loaded.use {
            val game = app { scene("render") { runProject(loaded) } }
            open(request, game).use { session ->
                try {
                    draw(game, session.renderer, request, name)
                } finally {
                    session.renderer.waitIdle()
                    game.dispose()
                }
            }
        }
    }

    private suspend fun draw(game: AwakeAppLifecycle, renderer: Renderer, request: RenderRequest, name: String): RenderedImage {
        game.ready(renderer)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        request.camera?.let { camera -> useCamera(runtime.world, camera, name) }
        runtime.world.debugSettings().apply {
            renderDebugView = request.view.debugView
            showWireframe = request.view.wireframe
        }
        repeat(request.frames) { game.update(STEP, request.width.toFloat(), request.height.toFloat()) }
        val frame = runtime.readbackAttachment(request.width, request.height, FramebufferAttachment.Color0)
        if (!frame.available) throw CommandFailure("$name has nothing to render: ${frame.reason}")
        val rgba = ByteArray(frame.width * frame.height * RGBA)
        for (pixel in 0 until frame.width * frame.height) {
            for (channel in 0 until RGBA) {
                val value = if (channel < frame.channels) frame.values[pixel * frame.channels + channel] else 1f
                rgba[pixel * RGBA + channel] = (value.coerceIn(0f, 1f) * MAX_BYTE + HALF).toInt().toByte()
            }
        }
        return RenderedImage(frame.width, frame.height, rgba)
    }

    /** Makes the camera on the entity called [camera] the one the picture is taken with. */
    private fun useCamera(world: World, camera: String, scene: String) {
        val cameras = buildList { world.queryEach<Camera> { entity, component -> add(world.get<Name>(entity)?.value to component) } }
        val chosen = cameras.firstOrNull { it.first == camera }?.second
            ?: throw CommandFailure("$scene has no camera '$camera'; it has ${cameras.mapNotNull { it.first }.joinToString().ifEmpty { "none named" }}")
        cameras.forEach { (_, component) -> component.isPrimary = component === chosen }
    }

    /** The project's files, with [scene] as the manifest's entry scene, which is the one a project loads. */
    private fun sceneAsEntry(scene: String): AssetSource {
        val files = FileSystemAssetSource(createPlatformFileSystem(project.root.absolutePath))
        return AssetSource { path ->
            val read = files.read(path)
            if (path.value.removePrefix("/") != PROJECT_MANIFEST) {
                read
            } else {
                read.map { bytes ->
                    val manifest = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
                    JsonObject(manifest + ("entryScene" to JsonPrimitive(scene))).toString().encodeToByteArray()
                }
            }
        }
    }

    private suspend fun open(request: RenderRequest, game: AwakeAppLifecycle): HeadlessRenderSession {
        val surface = HeadlessSurface(request.width, request.height)
        return try {
            when (request.backend) {
                RenderBackend.Vulkan -> {
                    val renderer = HeadlessVulkan(game).boot(surface)
                    object : HeadlessRenderSession {
                        override val renderer: Renderer = renderer

                        override fun close() = renderer.destroy()
                    }
                }
                RenderBackend.WebGpu -> {
                    val session = webGpuHeadlessPlan(ProjectRenderPlan, surface = surface)
                    val frames = OffscreenFrames(session.renderer, request.width, request.height)
                    object : HeadlessRenderSession {
                        override val renderer: Renderer = frames

                        override fun close() {
                            frames.destroyFrameTarget()
                            session.close()
                        }
                    }
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
            // A missing driver or native library surfaces as an error or an exception, depending on the backend.
            val other = RenderBackend.entries.first { it != request.backend }.label
            throw CommandFailure("${request.backend.label} didn't start (${error.message ?: error}). Is a driver installed? Try --backend $other.", error)
        }
    }

    /** Vulkan with a windowless swapchain, drawing [ProjectRenderPlan]. */
    private class HeadlessVulkan(game: AwakeAppLifecycle) : VulkanEngine(game, ProjectRenderPlan) {
        suspend fun boot(surface: HeadlessSurface): Renderer = createBackendResources(surface).renderer
    }

    /**
     * WebGPU's headless renderer has no presentation loop, so each frame draws to a texture. Content
     * features, such as a terrain's surface, attach through the renderer underneath.
     */
    private class OffscreenFrames(private val backend: Renderer, width: Int, height: Int) :
        Renderer by backend,
        GpuDrawPreparationSource,
        ContentFeatureHost by (backend as ContentFeatureHost) {
        override val gpuDrawPreparer = (backend as GpuDrawPreparationSource).gpuDrawPreparer
        private val target = backend.createRenderTarget(width, height)

        override fun draw(input: GpuPassInput) = backend.renderToTexture(target, input)

        fun destroyFrameTarget() {
            backend.waitIdle()
            target.destroy()
        }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val RGBA = 4
        const val MAX_BYTE = 255f
        const val HALF = 0.5f
    }
}
