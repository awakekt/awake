/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldstream

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.TerrainDriver
import com.awakekt.awake.world.AsyncWorldCellStreamListener
import com.awakekt.awake.world.CompositeCellStreamListener
import com.awakekt.awake.scene.world.StreamObserver
import com.awakekt.awake.world.WorldCellCoord
import com.awakekt.awake.world.WorldPartitionConfig
import com.awakekt.awake.scene.world.WorldPartitionSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Commercial Awake Studio turnkey [TerrainDriver] delivering asynchronous multi-cell world streaming.
 *
 * Automatically manages spatial cell partitioning, background asynchronous mesh generation
 * via [MeshCellStreamer], deferred GPU mesh retirement, and optional cell physics collider
 * streaming via [PhysicsCellStreamer].
 *
 * @property config World partition parameters (cell size, loading and unloading radii).
 * @property material Optional pre-created material; if null, resolved from runtime by [materialName].
 * @property materialName Material key used when [material] is not supplied (defaults to "lit-shadow").
 * @property cullMode Backface culling mode for spawned terrain cell meshes.
 * @property physicsStreamer Optional physics collider streamer (e.g. from [heightFieldCellStreamer]).
 * @property loadScope Optional coroutine scope for asynchronous cell generation. If null, a managed
 * scope on [Dispatchers.Default] is used and cancelled upon [dispose].
 * @property geometryFor Asynchronous cell geometry generator executed off the frame thread.
 */
class WorldstreamTerrainDriver(
    val config: WorldPartitionConfig = WorldPartitionConfig(),
    val material: Material? = null,
    val materialName: String = "lit-shadow",
    val cullMode: CullMode = CullMode.Back,
    val physicsStreamer: PhysicsCellStreamer? = null,
    val loadScope: CoroutineScope? = null,
    val geometryFor: (suspend (WorldCellCoord) -> MeshGeometry?)? = null,
) : TerrainDriver {

    private var attached = false
    private var meshStreamer: MeshCellStreamer? = null
    private var partitionSystem: WorldPartitionSystem? = null
    private var observerEntity: Entity? = null
    private var internalScope: CoroutineScope? = null

    override val isMounted: Boolean
        get() = attached

    override fun attach(runtime: SceneAppLifecycleRuntime) {
        if (attached) return

        val mat = material ?: runtime.requireMaterial(materialName)
        val streamer = MeshCellStreamer(
            renderer = runtime.renderer,
            material = mat,
            cellSize = config.cellSize,
            cullMode = cullMode,
            geometryFor = geometryFor ?: { null },
        )
        meshStreamer = streamer

        val combinedListener: AsyncWorldCellStreamListener = if (physicsStreamer != null) {
            CompositeCellStreamListener(streamer, physicsStreamer)
        } else {
            streamer
        }

        val effectiveScope = loadScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default).also {
            internalScope = it
        }

        partitionSystem = WorldPartitionSystem(
            config = config,
            asyncStreamListener = combinedListener,
            loadScope = effectiveScope,
        )

        // Spawn observer entity so WorldPartitionSystem automatically tracks the camera
        val entity = runtime.world.create()
        runtime.world.add(entity, Transform(position = Vec3f(0f, 0f, 0f)))
        runtime.world.add(entity, StreamObserver)
        observerEntity = entity

        attached = true
    }

    override fun update(runtime: SceneAppLifecycleRuntime, cameraPosition: Vec3f) {
        if (!attached) return

        observerEntity?.let { entity ->
            runtime.world.add(entity, Transform(position = cameraPosition))
        }

        // Apply completed background tile loads and compute active streaming cell window
        partitionSystem?.update(runtime.world, DEFAULT_DELTA)

        // Step mesh streamer to retire out-of-flight GPU buffers safely without driver stalls
        meshStreamer?.update(runtime.world, DEFAULT_DELTA)
    }

    override fun dispose(runtime: SceneAppLifecycleRuntime?) {
        if (runtime != null) {
            observerEntity?.let { entity ->
                runtime.world.destroy(entity)
                observerEntity = null
            }
            meshStreamer?.dispose(runtime.world)
            physicsStreamer?.dispose(runtime.world)
        }
        meshStreamer = null
        partitionSystem = null
        internalScope?.cancel()
        internalScope = null
        attached = false
    }

    private companion object {
        const val DEFAULT_DELTA = 0.016f
    }
}
