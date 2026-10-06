/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.gltf.LoadedScene
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.math.transformPosition
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.gltf.readGltfScene
import com.awakekt.awake.scene.physics.CollisionMeshSource
import com.awakekt.awake.scene.physics.MeshColliderSystem
import com.awakekt.awake.scene.physics.SceneMeshShape
import com.awakekt.awake.scene.physics.ScenePhysicsBody
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads the model behind every `mesh` collision shape in [scene] from [files], once per model, and
 * returns the triangles [MeshColliderSystem] builds bodies from. Placements of one reference share
 * one shape. Throws [IllegalArgumentException] naming the model and the node when a model can't be
 * read or has no such primitive.
 */
suspend fun loadCollisionMeshes(scene: SceneDocument, files: AssetSource): CollisionMeshSource {
    val models = HashMap<String, LoadedScene>()
    val shapes = HashMap<Pair<String, Int?>, MeshShape>()
    for ((node, shape) in scene.nodes.meshShapes(parent = null)) {
        val key = shape.mesh to shape.primitive
        if (key in shapes) continue
        val model = models.getOrPut(shape.mesh) {
            runCatching { readGltfScene(shape.mesh, files) }.getOrElse { cause ->
                if (cause is CancellationException) throw cause
                throw IllegalArgumentException("Can't load ${shape.mesh}, the collision mesh of node $node: ${cause.message}", cause)
            }
        }
        shapes[key] = model.collisionShape(shape, node)
    }
    return CollisionMeshSource { mesh, primitive -> shapes[mesh to primitive] }
}

/** Every mesh collision shape under these nodes, with its node's path as scene validation writes it. */
private fun List<SceneNode>.meshShapes(parent: String?): List<Pair<String, SceneMeshShape>> =
    flatMapIndexed { index, node ->
        val segment = node.name?.takeIf { it.isNotBlank() } ?: "#$index"
        val path = if (parent == null) segment else "$parent/$segment"
        node.components.mapNotNull { (it as? ScenePhysicsBody)?.shape as? SceneMeshShape }.map { path to it } +
            node.children.meshShapes(path)
    }

/** The chosen primitives' positions, moved by their nodes within the model, as one mesh. */
private fun LoadedScene.collisionShape(shape: SceneMeshShape, node: String): MeshShape {
    val all = meshes.flatMap { it.primitives }
    val chosen = shape.primitive?.let { index ->
        listOf(
            requireNotNull(all.getOrNull(index)) {
                "${shape.mesh} has ${all.size} primitives, so node $node can't collide with primitive $index"
            },
        )
    } ?: all
    require(chosen.isNotEmpty()) { "${shape.mesh} has no triangles for the collision mesh of node $node" }
    val stride = VertexFormat.PositionColorUv.strideFloats
    val vertices = FloatArray(chosen.sumOf { it.vertices.size / stride } * 3)
    val indices = IntArray(chosen.sumOf { it.indices.size })
    var vertex = 0
    var index = 0
    for (primitive in chosen) {
        val first = vertex
        for (source in 0 until primitive.vertices.size / stride) {
            val at = source * stride
            val position = primitive.localTransform.transformPosition(
                Vec4(primitive.vertices[at], primitive.vertices[at + 1], primitive.vertices[at + 2], 1f),
            )
            vertices[vertex * 3] = position.x
            vertices[vertex * 3 + 1] = position.y
            vertices[vertex * 3 + 2] = position.z
            vertex++
        }
        for (corner in primitive.indices) indices[index++] = corner + first
    }
    return MeshShape(vertices, indices)
}
