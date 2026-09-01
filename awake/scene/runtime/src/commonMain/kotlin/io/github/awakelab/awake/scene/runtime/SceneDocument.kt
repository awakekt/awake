/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.scene.rendering.Light
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** Bumped whenever a change to this model can't be read by the previous loader. Adding a new
 * [SceneComponent] does NOT need a bump: unknown entries are rejected loudly by
 * [SceneLoader.decode] rather than silently ignored, and old documents stay readable. */
const val SCENE_SCHEMA_VERSION: Int = 1

@Serializable
data class SceneDocument(
    val version: Int = SCENE_SCHEMA_VERSION,
    val name: String? = null,
    val nodes: List<SceneNode> = emptyList(),
    /** Provider-owned authored configuration. Unknown entries round-trip without interpretation. */
    val extensions: List<SceneExtensionRecord> = emptyList(),
)

@Serializable
data class SceneNode(
    val name: String? = null,
    // Stays a named field rather than a [SceneComponent]: every node has exactly one, and the
    // parent link is encoded by [children] nesting rather than by the transform itself.
    val transform: SceneTransform = SceneTransform(),
    val components: List<SceneComponent> = emptyList(),
    val children: List<SceneNode> = emptyList(),
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

/**
 * One authored component on a [SceneNode].
 *
 * Sealed so adding a component can't silently drop on load: the adapter's `when` over this
 * type stops compiling until the new case is mapped. The previous shape -- one nullable field
 * per component on [SceneNode] -- needed a matching line in `SceneLoader.instantiateNode`, and
 * omitting it round-tripped the data through JSON and then discarded it with no error.
 *
 * Closed polymorphism, so kotlinx resolves it at compile time with no `SerializersModule`.
 */
@Serializable
// "component", not the default "type": SceneLight already has its own `type` field, and
// kotlinx refuses to serialize a subclass whose property collides with the discriminator.
// Renaming that one field would only defer the clash to the next component with a `type`.
// Declared on the type rather than in SceneJson so a caller-supplied Json still round-trips.
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("component")
sealed interface SceneComponent

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
    // No clip-space field here -- that's a backend convention (Vulkan vs WebGPU), not
    // scene-authored content. The active Renderer.clipSpace supplies it at the moment a
    // projection matrix is built (see that property's own doc comment).
) : SceneComponent

@Serializable
@SerialName("light")
data class SceneLight(
    val color: SceneVec3 = SceneVec3(1f, 1f, 1f),
    val intensity: Float = 1f,
    val type: Type = Type.Point,
    /**
     * Which way a [Type.Directional] light points; ignored for a point light, which is placed by
     * its node's transform instead.
     *
     * Scenes have been authoring this key since directional lights existed and it was dropped on
     * the floor: the field was missing here, `SceneJson` ignores unknown keys, and every authored
     * sun therefore lit its scene from the engine's default angle. Defaults to that same angle,
     * so a scene that never set it renders exactly as before.
     */
    val direction: SceneVec3 = Light().direction.toSceneVec3(),
    /** Where a [Type.Point] light's contribution reaches zero, in world units. Ignored for a
     * directional light. Defaults to the component's own default rather than repeating the
     * number here, so raising it is a one-line change. */
    val range: Float = Light().range,
) : SceneComponent {
    @Serializable
    enum class Type {
        Directional,
        Point,
    }
}

@Serializable
@SerialName("meshRenderer")
data class SceneMeshRenderer(
    val mesh: String,
    val material: String,
    /** [io.github.awakelab.awake.render.renderer.CullMode]'s own doc comment covers the
     * trade-off -- `None` (default) preserves every existing scene's exact current behavior. */
    val cullMode: CullMode = CullMode.None,
) : SceneComponent {
    enum class CullMode { None, Back, Front }
}

@Serializable
@SerialName("spinControl")
data class SceneSpinControl(
    val radians: Float = 0f,
    val speed: Float = 1f,
) : SceneComponent

@Serializable
@SerialName("pbrMaterial")
data class ScenePbrMaterial(
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
) : SceneComponent
