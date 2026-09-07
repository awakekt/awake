/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames

/** Current Awake scene document schema version integer (`1`). */
const val SCENE_SCHEMA_VERSION: Int = 1

/**
 * Root serializable representation of an Awake scene document (`.scene.json`).
 *
 * @property version Scene document schema version integer.
 * @property name Optional human-readable scene title.
 * @property nodes Hierarchy of top-level scene nodes.
 * @property extensions Associated scene extension data records.
 */
@Serializable
data class SceneDocument(
    val version: Int = SCENE_SCHEMA_VERSION,
    val name: String? = null,
    val nodes: List<SceneNode> = emptyList(),
    val extensions: List<SceneExtensionRecord> = emptyList(),
)

/**
 * Serializable node in a scene hierarchy.
 *
 * @property name Optional node identifier name.
 * @property transform Local 3D transform spatial orientation.
 * @property components Attached serializable components.
 * @property children Nested child nodes in the hierarchy.
 * @property prefabGuid Associated prefab GUID if instantiated from a prefab.
 * @property overrides Property overrides applied over the linked prefab.
 */
@Serializable
data class SceneNode(
    val name: String? = null,
    val transform: SceneTransform = SceneTransform(),
    val components: List<SceneComponent> = emptyList(),
    val children: List<SceneNode> = emptyList(),
    val prefabGuid: String? = null,
    val overrides: List<ScenePropertyOverride> = emptyList(),
)

/**
 * Property override key-value pair for prefab instances.
 *
 * @property targetPath Property path identifier (e.g. `components[0].color`).
 * @property value Serialized string value to override.
 */
@Serializable
data class ScenePropertyOverride(
    val targetPath: String,
    val value: String,
)

/**
 * Serializable 3D local transform spatial descriptor.
 *
 * @property position Local translation vector.
 * @property rotation Local Euler angles rotation vector in degrees.
 * @property scale Local scaling vector.
 */
@Serializable
data class SceneTransform(
    val position: SceneVec3 = SceneVec3(),
    val rotation: SceneVec3 = SceneVec3(),
    val scale: SceneVec3 = SceneVec3(1f, 1f, 1f),
)

/**
 * Serializable 3-component floating-point vector.
 *
 * @property x X component.
 * @property y Y component.
 * @property z Z component.
 */
@Serializable
data class SceneVec3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
)

/**
 * Sealed base interface for all serializable scene components.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("component")
sealed interface SceneComponent

/**
 * Generic custom extension component holding unparsed JSON payloads.
 *
 * @property type Unique custom component discriminator type identifier.
 * @property payload Associated JSON element payload.
 */
@Serializable
@SerialName("custom")
data class SceneCustomComponent(
    val type: String,
    val payload: JsonElement,
) : SceneComponent

/**
 * Serializable camera component.
 *
 * @property eye World-space camera eye position.
 * @property center World-space look-at center point.
 * @property up Camera up vector.
 * @property fovYDegrees Vertical field of view in degrees.
 * @property near Near clipping plane distance.
 * @property far Far clipping plane distance.
 * @property primary Whether this camera acts as the primary viewport camera.
 */
@Serializable
@SerialName("camera")
data class SceneCamera(
    val eye: SceneVec3 = SceneVec3(0f, 0f, 5f),
    val center: SceneVec3 = SceneVec3(0f, 0f, 0f),
    val up: SceneVec3 = SceneVec3(0f, 1f, 0f),
    val fovYDegrees: Float = 60f,
    val near: Float = 0.1f,
    val far: Float = 100f,
    val primary: Boolean = true,
) : SceneComponent

/**
 * Serializable light source component.
 *
 * @property color Light RGB color vector.
 * @property intensity Radiance/luminance multiplier.
 * @property type Light source emission type.
 * @property direction World-space emission direction vector for directional lights.
 * @property range Maximum falloff distance for point lights.
 */
@Serializable
@SerialName("light")
data class SceneLight(
    val color: SceneVec3 = SceneVec3(1f, 1f, 1f),
    val intensity: Float = 1f,
    val type: Type = Type.Point,
    val direction: SceneVec3 = SceneVec3(0.4f, 0.8f, 0.4f),
    val range: Float = 10f,
) : SceneComponent {
    /** Light source emission type enumeration. */
    @Serializable
    enum class Type {
        /** Infinite directional sun light. */
        Directional,

        /** Omnidirectional point light source. */
        Point,
    }
}

/**
 * Serializable PBR material parameter component.
 *
 * @property metallic Metallic factor between 0.0 (dielectric) and 1.0 (metallic).
 * @property roughness Roughness factor between 0.0 (smooth) and 1.0 (rough).
 */
@Serializable
@SerialName("pbr_material")
data class ScenePbrMaterial(
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
) : SceneComponent

/**
 * Serializable mesh renderer component linking a geometry mesh and material.
 *
 * @property mesh Mesh asset resource identifier or primitive shape name.
 * @property material Material asset resource identifier.
 * @property cullMode GPU face culling mode.
 */
@Serializable
@SerialName("mesh_renderer")
data class SceneMeshRenderer(
    val mesh: String,
    val material: String,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("cull_mode", "cullMode")
    val cullMode: CullMode = CullMode.None,
) : SceneComponent {
    /** GPU face culling mode enumeration. */
    enum class CullMode {
        /** Do not cull any faces. */
        None,

        /** Cull back-facing polygons. */
        Back,

        /** Cull front-facing polygons. */
        Front,
    }
}

/**
 * Serializable continuous spin rotation control component.
 *
 * @property radians Initial rotation angle in radians.
 * @property speed Rotation speed multiplier.
 */
@Serializable
@SerialName("spin_control")
data class SceneSpinControl(
    val radians: Float = 0f,
    val speed: Float = 1f,
) : SceneComponent

/**
 * Serializable link component referencing an external prefab asset.
 *
 * @property prefabGuid Prefab asset unique identifier GUID.
 * @property isRoot Whether this node acts as the root of the prefab hierarchy.
 */
@Serializable
@SerialName("prefab_link")
data class ScenePrefabLink(
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("prefab_guid", "prefabGuid")
    val prefabGuid: String,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("is_root", "isRoot")
    val isRoot: Boolean = true,
) : SceneComponent

/**
 * Serializable patrol route AI behavior component.
 *
 * @property stops Waypoint stop position vectors in world space.
 * @property style Route traversal style (Loop, PingPong, Once).
 * @property dwellSeconds Dwell pause time at each waypoint in seconds.
 * @property speed Movement speed in units per second.
 * @property repathInterval Repath update interval in seconds.
 * @property waypointRadius Arrival threshold radius around waypoints.
 */
@Serializable
@SerialName("patrol")
data class ScenePatrol(
    val stops: List<SceneVec3> = emptyList(),
    val style: Style = Style.Loop,
    val dwellSeconds: Float = 1f,
    val speed: Float = 1.8f,
    val repathInterval: Float = 1f,
    val waypointRadius: Float = 0.3f,
) : SceneComponent {
    /** Traversal style enumeration for patrol routes. */
    @Serializable
    enum class Style {
        /** Continuously loop back to the first waypoint. */
        Loop,

        /** Reverse direction upon reaching either end of the route. */
        PingPong,

        /** Traverses route once and stops at the final waypoint. */
        Once,
    }
}

/**
 * Serializable chase target AI behavior component.
 *
 * @property target Entity node name to chase.
 * @property speed Movement chase speed in units per second.
 * @property repathInterval Repath interval in seconds.
 * @property waypointRadius Arrival threshold distance.
 */
@Serializable
@SerialName("chase")
data class SceneChase(
    val target: String? = null,
    val speed: Float = 2.5f,
    val repathInterval: Float = 0.5f,
    val waypointRadius: Float = 0.3f,
) : SceneComponent

/**
 * Serializable flee threat AI behavior component.
 *
 * @property threat Entity node name to flee from.
 * @property panicRadius Threat distance threshold that triggers fleeing.
 * @property safeRadius Safe distance threshold where fleeing stops.
 * @property fleeDistance Distance to run away when fleeing.
 * @property speed Movement flee speed in units per second.
 * @property repathInterval Repath interval in seconds.
 * @property waypointRadius Arrival threshold distance.
 */
@Serializable
@SerialName("flee")
data class SceneFlee(
    val threat: String? = null,
    val panicRadius: Float = 6f,
    val safeRadius: Float = 12f,
    val fleeDistance: Float = 10f,
    val speed: Float = 3.5f,
    val repathInterval: Float = 0.4f,
    val waypointRadius: Float = 0.3f,
) : SceneComponent
