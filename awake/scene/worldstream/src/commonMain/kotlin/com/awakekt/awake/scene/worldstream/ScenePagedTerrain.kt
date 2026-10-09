/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldstream

import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.terrain.PagedTerrain
import com.awakekt.awake.terrain.TerrainPage
import com.awakekt.awake.terrain.TerrainPageStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Scene-owned configuration; the project resolves [index] and installs the selected surface provider. */
@Serializable
@SerialName("paged_terrain")
data class ScenePagedTerrain(
    val index: String,
    /** Maximum resident cell count. */
    val capacity: Int = 64,
    /** Requested cell radius around the observer. */
    val radius: Int = 2,
    /** Maximum live asynchronous cell readers. */
    val maxConcurrentReads: Int = 2,
    /** Maximum region-upload bytes per GPU frame slot update. */
    val maxUploadBytes: Int = 8 * 1024 * 1024,
    /** Whether to create static heightfield bodies for nearby resident cells. */
    val collider: Boolean = false,
    /** Cell radius with static collision around the observer. */
    val collisionRadius: Int = 1,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (index.isBlank()) add(SceneValidationIssue(path, "paged_terrain requires an index path"))
        if (capacity !in 2..65534) add(SceneValidationIssue(path, "capacity must be 2..65534"))
        if (radius !in 0..64) add(SceneValidationIssue(path, "radius must be 0..64"))
        if (maxConcurrentReads !in 1..capacity) add(SceneValidationIssue(path, "maxConcurrentReads must be 1..capacity"))
        if (maxUploadBytes <= 0) add(SceneValidationIssue(path, "maxUploadBytes must be positive"))
        if (collisionRadius !in 0..radius) add(SceneValidationIssue(path, "collisionRadius must be 0..radius"))
    }

    /** Scope, reader, observer and failure handling are code-only integration dependencies. */
    fun streamer(terrain: PagedTerrain, scope: CoroutineScope, read: suspend (TerrainPageCoord) -> TerrainPage?, onFailure: (TerrainPageCoord, Throwable) -> Unit = { _, _ -> }): TerrainPageStreamer {
        require(validate("paged_terrain").isEmpty())
        require(terrain.capacity == capacity && terrain.maxUploadBytes == maxUploadBytes)
        return TerrainPageStreamer(terrain, scope, read, radius, maxConcurrentReads, onFailure)
    }

    /** Binds the configured collision policy and observer to the scene runtime. */
    fun system(streamer: TerrainPageStreamer, observer: () -> Vec3f, physics: PhysicsSystem? = null): PagedTerrainSystem {
        require(!collider || physics != null) { "Paged terrain collision requires the scene's PhysicsSystem." }
        return PagedTerrainSystem(streamer, observer, if (collider) physics else null, collisionRadius)
    }
}

/** Retains the authored configuration without embedding a whole-world height or control map. */
data class PagedTerrainReference(
    /** Authored page-index reference and streaming settings. */
    val configuration: ScenePagedTerrain,
)

/** Register alongside the consuming project's scene bindings. Resolution is an explicit asset lifecycle. */
object PagedTerrainBinding : SceneComponentBinding<PagedTerrainReference, ScenePagedTerrain> {
    override val componentClass = PagedTerrainReference::class
    override val schemaClass = ScenePagedTerrain::class
    override val serializer = ScenePagedTerrain.serializer()
    override fun attachTyped(world: World, entity: Entity, component: ScenePagedTerrain, context: SceneResolutionContext) {
        world.add(entity, PagedTerrainReference(component))
    }
    override fun export(world: World, entity: Entity, component: PagedTerrainReference): ScenePagedTerrain = component.configuration
}
