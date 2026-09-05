/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.document

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames

const val SCENE_SCHEMA_VERSION: Int = 1

@Serializable
data class SceneDocument(
    val version: Int = SCENE_SCHEMA_VERSION,
    val name: String? = null,
    val nodes: List<SceneNode> = emptyList(),
    val extensions: List<SceneExtensionRecord> = emptyList(),
)

@Serializable
data class SceneNode(
    val name: String? = null,
    val transform: SceneTransform = SceneTransform(),
    val components: List<SceneComponent> = emptyList(),
    val children: List<SceneNode> = emptyList(),
    val prefabGuid: String? = null,
    val overrides: List<ScenePropertyOverride> = emptyList(),
)

@Serializable
data class ScenePropertyOverride(
    val targetPath: String,
    val value: String,
)

@Serializable
data class SceneTransform(
    val position: SceneVec3 = SceneVec3(),
    val rotation: SceneVec3 = SceneVec3(),
    val scale: SceneVec3 = SceneVec3(1f, 1f, 1f),
)

@Serializable
data class SceneVec3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("component")
sealed interface SceneComponent

@Serializable
@SerialName("custom")
data class SceneCustomComponent(
    val type: String,
    val payload: JsonElement,
) : SceneComponent

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

@Serializable
@SerialName("light")
data class SceneLight(
    val color: SceneVec3 = SceneVec3(1f, 1f, 1f),
    val intensity: Float = 1f,
    val type: Type = Type.Point,
    val direction: SceneVec3 = SceneVec3(0.4f, 0.8f, 0.4f),
    val range: Float = 10f,
) : SceneComponent {
    @Serializable
    enum class Type {
        Directional,
        Point,
    }
}

@Serializable
@SerialName("pbr_material")
data class ScenePbrMaterial(
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
) : SceneComponent

@Serializable
@SerialName("mesh_renderer")
data class SceneMeshRenderer(
    val mesh: String,
    val material: String,
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("cull_mode", "cullMode")
    val cullMode: CullMode = CullMode.None,
) : SceneComponent {
    enum class CullMode { None, Back, Front }
}

@Serializable
@SerialName("spin_control")
data class SceneSpinControl(
    val radians: Float = 0f,
    val speed: Float = 1f,
) : SceneComponent

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
    @Serializable
    enum class Style {
        Loop,
        PingPong,
        Once,
    }
}

@Serializable
@SerialName("chase")
data class SceneChase(
    val target: String? = null,
    val speed: Float = 2.5f,
    val repathInterval: Float = 0.5f,
    val waypointRadius: Float = 0.3f,
) : SceneComponent

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
