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
import com.awakekt.awake.scene.physics.SceneConvexHullShape
import com.awakekt.awake.scene.physics.SceneMeshShape
import com.awakekt.awake.scene.physics.ScenePhysicsBody
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads the model behind every `mesh` and `convex_hull` collision shape in [scene] from [files],
 * once per model, and returns the triangles [MeshColliderSystem] builds bodies from. Placements of
 * one reference share one shape. Throws [IllegalArgumentException] naming the model and the node when
 * a model can't be read or has no such primitive.
 */
suspend fun loadCollisionMeshes(scene: SceneDocument, files: AssetSource): CollisionMeshSource {
    val models = HashMap<String, LoadedScene>()
    val shapes = HashMap<Pair<String, Int?>, MeshShape>()
    for ((node, modelRef) in scene.nodes.modelCollisionRefs(parent = null)) {
        val (meshPath, primitive) = modelRef
        val key = meshPath to primitive
        if (key in shapes) continue
        val model = models.getOrPut(meshPath) {
            runCatching { readGltfScene(meshPath, files) }.getOrElse { cause ->
                if (cause is CancellationException) throw cause
                throw IllegalArgumentException("Can't load $meshPath, the collision mesh of node $node: ${cause.message}", cause)
            }
        }
        shapes[key] = model.collisionShape(meshPath, primitive, node)
    }
    return CollisionMeshSource { mesh, primitive -> shapes[mesh to primitive] }
}

/** Every mesh and convex_hull collision shape under these nodes, with its node's path as scene validation writes it. */
private fun List<SceneNode>.modelCollisionRefs(parent: String?): List<Pair<String, Pair<String, Int?>>> =
    flatMapIndexed { index, node ->
        val segment = node.name?.takeIf { it.isNotBlank() } ?: "#$index"
        val path = if (parent == null) segment else "$parent/$segment"
        node.components.mapNotNull { component ->
            when (val shape = (component as? ScenePhysicsBody)?.shape) {
                is SceneMeshShape -> shape.mesh to shape.primitive
                is SceneConvexHullShape -> shape.mesh to shape.primitive
                else -> null
            }
        }.map { path to it } + node.children.modelCollisionRefs(path)
    }

/** The chosen primitives' positions, moved by their nodes within the model, as one mesh. */
private fun LoadedScene.collisionShape(meshPath: String, primitive: Int?, node: String): MeshShape {
    val all = meshes.flatMap { it.primitives }
    val chosen = primitive?.let { index ->
        listOf(
            requireNotNull(all.getOrNull(index)) {
                "$meshPath has ${all.size} primitives, so node $node can't collide with primitive $index"
            },
        )
    } ?: all
    require(chosen.isNotEmpty()) { "$meshPath has no triangles for the collision mesh of node $node" }
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
