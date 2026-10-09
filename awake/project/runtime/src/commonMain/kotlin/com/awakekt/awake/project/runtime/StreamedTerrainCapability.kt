/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaderpack.pagedTerrainContentFeature
import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.terrain.PagedHeightmap
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.kit.terrainlayers.PagedTerrainLayers
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.worldstream.PagedTerrainBinding
import com.awakekt.awake.scene.worldstream.PagedTerrainSystem
import com.awakekt.awake.scene.worldstream.ScenePagedTerrain
import com.awakekt.awake.terrain.PagedTerrain
import com.awakekt.awake.terrain.TerrainPage
import com.awakekt.awake.terrain.TerrainPageHeightReader
import com.awakekt.awake.terrain.TerrainPageIndexCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * A scene's `paged_terrain`: one clipmap over the cells its page index names, streamed around the
 * primary camera, with static heightfield collision near the camera when the component asks for it
 * and the host gives a physics world. An index that names a palette draws with terrain layers; one
 * without draws the neutral paged surface. A scene streams at most one.
 */
internal object StreamedTerrainCapability : SceneCapability {
    override val id = "com.awakekt.awake.streamed-terrain"
    override val components = listOf(PagedTerrainBinding)

    /** The scene's paged terrain, as [loadStreamedTerrain] reads it. */
    val Terrain = SceneContentKey<StreamedTerrainAssets>("streamed terrain")

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        val config = scene.pagedTerrain() ?: return
        content[Terrain] = loadStreamedTerrain(config, files)
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        val config = scene.pagedTerrain() ?: return
        // A fixed system, so it runs before the physics step that builds the bodies of its collision cells.
        plan.fixed("paged-terrain") { services ->
            val assets = requireNotNull(services.content[Terrain]) {
                "The scene has a paged_terrain; pass the content loadSceneContent reads in SceneHostServices"
            }
            val physics = if (config.collider && services.physics != null) plan.physicsSystem(services) else null
            // A headless host, such as a game server, streams the collision cells and draws nothing.
            val draw = if (services.hasRenderer) services.renderer as? ContentFeatureHost else null
            StreamedTerrainSystem(assets, draw, physics)
        }
    }
}

/**
 * A scene's paged terrain, loaded: its configuration, the residency it streams into, the draw that
 * reads that residency, and how to read one cell from the project's files.
 */
internal class StreamedTerrainAssets(
    val config: ScenePagedTerrain,
    val terrain: PagedTerrain,
    val content: ContentFeatureSource,
    val read: suspend (TerrainPageCoord) -> TerrainPage?,
)

/** The scene's one `paged_terrain`, or null; more than one is refused. */
internal fun SceneDocument.pagedTerrain(): ScenePagedTerrain? {
    val found = nodes.flatMap { it.pagedTerrains() }
    require(found.size <= 1) { "A scene streams one paged_terrain, not ${found.size}" }
    return found.firstOrNull()
}

private fun SceneNode.pagedTerrains(): List<ScenePagedTerrain> =
    components.filterIsInstance<ScenePagedTerrain>() + children.flatMap { it.pagedTerrains() }

/**
 * Reads [config]'s page index from [files] and what its cells share: the coarse fallback, and for an
 * index that names a palette, the palette and coarse surface. Cells themselves are read later, as
 * the camera reaches them. Throws [IllegalArgumentException] for settings or files that cannot load.
 */
@Suppress("TooGenericExceptionCaught") // An asset source reports a missing or unreadable file in its own exception type.
internal suspend fun loadStreamedTerrain(config: ScenePagedTerrain, files: AssetSource): StreamedTerrainAssets {
    val issues = config.validate("paged_terrain")
    require(issues.isEmpty()) { issues.joinToString("; ") { it.message } }
    val path = AssetPath(config.index)
    return try {
        val index = TerrainPageIndexCodec.decode(files.read(path).getOrThrow())
        if (index.palette != null) {
            val layers = PagedTerrainLayers.load(files, path, config.capacity, config.maxUploadBytes)
            StreamedTerrainAssets(config, layers.terrain, layers.content, layers::read)
        } else {
            val reader = TerrainPageHeightReader(index, path, files)
            val terrain = PagedTerrain(PagedHeightmap(index.layout(), reader.fallback()), config.capacity, maxUploadBytes = config.maxUploadBytes)
            StreamedTerrainAssets(config, terrain, pagedTerrainContentFeature(terrain)) { coord -> reader.read(coord)?.let { TerrainPage(it) } }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (problem: Exception) {
        throw IllegalArgumentException("paged_terrain ${config.index}: ${problem.message}", problem)
    }
}

/**
 * Streams [assets]' cells around the primary camera, draws them through [host], and keeps static
 * collision near the camera through [physics], the scene's own physics step, when that is given.
 *
 * Cells read off the frame thread; the drawing attaches from [update] and appears on a later frame.
 * [close] stops reading and detaches the drawing. Collision entities stay with the world, whose
 * physics world frees their bodies.
 */
internal class StreamedTerrainSystem(
    private val assets: StreamedTerrainAssets,
    private val host: ContentFeatureHost?,
    physics: PhysicsSystem?,
) : System, AutoCloseable {
    private val log = Logger("streamed-terrain")
    private val reads = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val attaching = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val attached = Channel<AttachedContentFeature>(capacity = 1)
    private var feature: AttachedContentFeature? = null
    private var attachStarted = false
    private var closed = false
    private val eye = Vec3f(0f, 0f, 0f)
    private val streamer = assets.config.streamer(assets.terrain, reads, assets.read) { coord, error ->
        log.error(error) { "paged_terrain ${assets.config.index}: cell (${coord.x}, ${coord.z}) failed to load and uses the coarse fallback" }
    }
    private val streaming = PagedTerrainSystem(streamer, { eye }, physics, assets.config.collisionRadius)

    override fun update(world: World, delta: Float) {
        if (closed) return
        if (host != null && !attachStarted) startAttach(host)
        attached.tryReceive().getOrNull()?.let { feature = it }
        val camera = primaryCamera(world) ?: return
        val lensEye = camera.lens.eye
        eye.x = lensEye.x
        eye.y = lensEye.y
        eye.z = lensEye.z
        streaming.update(world, delta)
    }

    override fun close() {
        if (closed) return
        closed = true
        attaching.cancel()
        attached.close()
        while (true) {
            val pending = attached.tryReceive().getOrNull() ?: break
            pending.detach()
        }
        feature?.detach()
        feature = null
        streamer.close()
        reads.cancel()
    }

    // TooGenericExceptionCaught: a terrain that fails to attach must not stop the frame.
    @Suppress("TooGenericExceptionCaught")
    private fun startAttach(host: ContentFeatureHost) {
        attachStarted = true
        attaching.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val attachedFeature = host.attachContentFeature(assets.content)
                // A system closed while this attached has closed the channel: detach straight away.
                if (attached.trySend(attachedFeature).isFailure) attachedFeature.detach()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.error(failure) { "paged_terrain ${assets.config.index} could not be attached and is not drawn." }
            }
        }
    }

    private fun primaryCamera(world: World): Camera? {
        val family = world.family<Camera>()
        val cameras = family.components()
        for (index in 0 until family.size) {
            if (cameras[index].isPrimary) return cameras[index]
        }
        return null
    }
}
