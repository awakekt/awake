/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.terrain.TerrainSurfaceReference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass

/**
 * A terrain in a scene document: its height samples and, optionally, the surface model that
 * shades it.
 *
 * Heights are embedded row-major, `z * width + x`, the same layout as [Heightmap].
 *
 * @property width Number of heightmap samples along the X axis.
 * @property depth Number of heightmap samples along the Z axis.
 * @property scaleX Horizontal grid spacing scale along the X axis.
 * @property scaleY Height elevation scaling factor along the Y axis.
 * @property scaleZ Horizontal grid spacing scale along the Z axis.
 * @property samples Row-major height elevation sample values.
 * @property tilingScale Texture UV coordinate tiling frequency.
 * @property isVisible Whether this terrain is submitted for rendering.
 * @property surface Optional custom surface shading model descriptor.
 * @property collider Whether the terrain generates a static physics heightfield collider.
 */
@Serializable
@SerialName("terrain")
data class SceneTerrain(
    @PropertyRange(min = 2.0) val width: Int,
    @PropertyRange(min = 2.0) val depth: Int,
    @PropertyRange(min = 0.0, exclusiveMin = true) val scaleX: Float = 1f,
    @PropertyRange(min = 0.0, exclusiveMin = true) val scaleY: Float = 1f,
    @PropertyRange(min = 0.0, exclusiveMin = true) val scaleZ: Float = 1f,
    val samples: List<Float>,
    val tilingScale: Float = 16f,
    val isVisible: Boolean = true,
    val surface: SceneTerrainSurface? = null,
    val collider: Boolean = false,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    /** The checks [Heightmap] enforces, reported here so a bad scene fails validation, not loading. */
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (width < 2 || depth < 2) add(SceneValidationIssue(path, "terrain needs at least 2 x 2 samples; was $width x $depth"))
        if (samples.size.toLong() != width.toLong() * depth) {
            add(SceneValidationIssue(path, "terrain.samples holds ${samples.size} values for $width x $depth"))
        }
        if (!samples.all(Float::isFinite)) add(SceneValidationIssue(path, "terrain.samples must be finite"))
        if (listOf(scaleX, scaleY, scaleZ).any { !it.isFinite() || it <= 0f }) {
            add(SceneValidationIssue(path, "terrain scale must be finite and positive"))
        }
        if (collider && (width != depth || width < MIN_COLLIDER_SAMPLES)) {
            add(SceneValidationIssue(path, "terrain.collider needs a square terrain of at least $MIN_COLLIDER_SAMPLES samples a side"))
        }
    }
}

/**
 * Names the surface model a [TerrainSurfaceProvider] resolves. Core reads only [provider]; the
 * [payload] belongs to that provider and is preserved unchanged when no provider is installed.
 *
 * @property provider Unique provider key identifying the surface model.
 * @property version Payload schema version number.
 * @property payload Custom configuration payload for the surface provider.
 */
@Serializable
data class SceneTerrainSurface(
    val provider: String,
    val version: Int = 1,
    val payload: JsonElement = JsonObject(emptyMap()),
)

/**
 * Binding definition connecting [SceneTerrain] descriptors to ECS [TerrainComponent] components.
 */
object TerrainBinding : SceneComponentBinding<TerrainComponent, SceneTerrain> {
    override val componentClass: KClass<TerrainComponent> = TerrainComponent::class
    override val schemaClass: KClass<SceneTerrain> = SceneTerrain::class
    override val serializer = SceneTerrain.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneTerrain,
        context: SceneResolutionContext,
    ) {
        world.add(
            entity,
            TerrainComponent(
                heightmap = Heightmap(
                    samples = component.samples.toFloatArray(),
                    width = component.width,
                    depth = component.depth,
                    scale = Vec3f(component.scaleX, component.scaleY, component.scaleZ),
                ),
                tilingScale = component.tilingScale,
                isVisible = component.isVisible,
                collider = component.collider,
            ),
        )
        component.surface?.let { world.add(entity, TerrainSurfaceReference(it.provider, it.version, it.payload)) }
    }

    override fun export(world: World, entity: Entity, component: TerrainComponent): SceneTerrain {
        val heightmap = component.heightmap
        val scale = heightmap.scale
        return SceneTerrain(
            width = heightmap.width,
            depth = heightmap.depth,
            scaleX = scale.x,
            scaleY = scale.y,
            scaleZ = scale.z,
            samples = heightmap.copySamples().toList(),
            tilingScale = component.tilingScale,
            isVisible = component.isVisible,
            surface = world.get<TerrainSurfaceReference>(entity)?.let {
                SceneTerrainSurface(it.provider, it.version, it.payload)
            },
            collider = component.collider,
        )
    }
}

/** A heightfield collider's smallest side: two of Jolt's two-sample blocks. */
private const val MIN_COLLIDER_SAMPLES = 4
