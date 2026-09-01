/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.render.renderer.CullMode
import io.github.awakelab.awake.scene.core.Name
import io.github.awakelab.awake.scene.core.transform.SpinControl
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.rendering.Light
import io.github.awakelab.awake.scene.rendering.mesh.PbrMaterial
import kotlin.math.PI
import io.github.awakelab.awake.scene.rendering.Camera as SceneCameraComponent

data class SceneNodeHandle<T>(
    val name: String?,
    val value: T,
    val children: List<SceneNodeHandle<T>>,
)

interface SceneInstantiationAdapter<Node, Instance> {
    fun createNode(node: SceneNode, parent: Node?): Node
    fun attachName(node: Node, name: String)
    fun attachTransform(node: Node, transform: SceneTransform, parent: Node?)
    fun attachComponent(node: Node, component: SceneComponent)
    fun complete(roots: List<SceneNodeHandle<Node>>): Instance
}

class AwakeWorldSceneAdapter(
    private val world: World = World(),
) : SceneInstantiationAdapter<Entity, Scene> {
    private val renderableRequests = ArrayList<SceneRenderableRequest>()

    /**
     * Named nodes, for the components that reference another node rather than carrying a value.
     *
     * First wins on a duplicate name, which is arbitrary but has to be *something*; a scene with
     * two nodes of the same name has already lost the ability to say which one it means.
     */
    private val entitiesByName = HashMap<String, Entity>()

    /** Node references to resolve once every node exists, which is not until [complete]. */
    private val entityLinks = ArrayList<EntityLink>()

    override fun createNode(node: SceneNode, parent: Entity?): Entity = world.create()

    override fun attachName(node: Entity, name: String) {
        world.add(node, Name(name))
        entitiesByName.getOrPut(name) { node }
    }

    override fun attachTransform(node: Entity, transform: SceneTransform, parent: Entity?) {
        world.add(node, transform.toComponent(parent))
    }

    // Exhaustive over the sealed SceneComponent: a new authored component stops this compiling
    // until it is mapped, instead of round-tripping through JSON and being dropped on load.
    override fun attachComponent(node: Entity, component: SceneComponent) {
        when (component) {
            is SceneCamera -> world.add(node, component.toComponent())
            is SceneLight -> world.add(node, component.toComponent())
            is ScenePbrMaterial -> world.add(node, component.toComponent())
            is SceneSpinControl -> world.add(node, component.toComponent())
            is ScenePatrol -> world.add(node, component.toComponent())
            // The behaviour is attached now and its target filled in by complete(): the node it
            // names may not exist yet, and a component that only half-exists until then would be
            // visible to any system that ran in between.
            is SceneChase -> {
                val chase = component.toComponent()
                world.add(node, chase)
                component.target?.let { entityLinks += EntityLink(it) { target -> chase.target = target } }
            }
            is SceneFlee -> {
                val flee = component.toComponent()
                world.add(node, flee)
                component.threat?.let { entityLinks += EntityLink(it) { threat -> flee.threat = threat } }
            }
            // Mesh and material are live GPU handles the loader can't build; the caller resolves
            // these against its own renderer (see SceneRenderableRequest).
            is SceneMeshRenderer -> {
                renderableRequests += SceneRenderableRequest(node, component)
            }
        }
    }

    override fun complete(roots: List<SceneNodeHandle<Entity>>): Scene {
        entityLinks.forEach { link ->
            val target = requireNotNull(entitiesByName[link.name]) {
                "Cannot load scene: a component references node \"${link.name}\", which does not exist."
            }
            link.assign(target)
        }
        return Scene(
            world = world,
            roots = roots.map { it.toSceneNodeInstance() },
            renderableRequests = renderableRequests.toList(),
        )
    }

    private class EntityLink(val name: String, val assign: (Entity) -> Unit)
}

internal fun SceneTransform.toComponent(parent: Entity?): Transform = Transform(
    position = position.toVec3(),
    rotation = rotation.toVec3(),
    scale = scale.toVec3(),
    parent = parent,
)

internal fun SceneCamera.toComponent(): SceneCameraComponent = SceneCameraComponent(
    lens = Lens(
        eye = eye.toVec3(),
        center = center.toVec3(),
        up = up.toVec3(),
        fovYRadians = degreesToRadians(fovYDegrees),
        near = near,
        far = far,
    ),
    isPrimary = primary,
)

internal fun SceneLight.toComponent(): Light = Light(
    color = color.toVec3(),
    intensity = intensity,
    direction = direction.toVec3(),
    range = range,
    type = when (type) {
        SceneLight.Type.Directional -> Light.Type.Directional
        SceneLight.Type.Point -> Light.Type.Point
    },
)

internal fun SceneMeshRenderer.CullMode.toCullMode(): CullMode = when (this) {
    SceneMeshRenderer.CullMode.None -> CullMode.None
    SceneMeshRenderer.CullMode.Back -> CullMode.Back
    SceneMeshRenderer.CullMode.Front -> CullMode.Front
}

internal fun ScenePbrMaterial.toComponent(): PbrMaterial = PbrMaterial(
    metallic = metallic,
    roughness = roughness,
)

internal fun SceneSpinControl.toComponent(): SpinControl = SpinControl().also {
    it.radians = radians
    it.speed = speed
}

internal fun SceneVec3.toVec3(): Vec3f = Vec3f(x, y, z)

private fun degreesToRadians(degrees: Float): Float = degrees * (PI.toFloat() / 180f)

private fun SceneNodeHandle<Entity>.toSceneNodeInstance(): SceneNodeInstance = SceneNodeInstance(
    name = name,
    entity = value,
    children = children.map { it.toSceneNodeInstance() },
)
