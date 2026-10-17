/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

private const val MATRIX_ELEMENTS = 16
private const val VEC3_ELEMENTS = 3
private const val QUAT_ELEMENTS = 4

/**
 * Checks this document's node graph before anything walks it: every mesh, skin and child reference in
 * range, every transform the right size, every node with at most one parent, no cycle, no chain deeper
 * than [GltfLimits.maxNodeDepth], and every scene's roots distinct parentless nodes. A walk from the
 * scene roots then visits each node once and recurses no deeper than the limit.
 */
internal fun GltfDocument.validateNodeGraph(limits: GltfLimits) {
    val parents = IntArray(nodes.size) { -1 }
    nodes.forEachIndexed { index, node ->
        validateNodeFields(index, node)
        node.children.forEachIndexed { slot, child ->
            val location = "nodes[$index].children[$slot]"
            if (child !in nodes.indices) malformed(location, "is node $child, but the document has ${nodes.size} nodes")
            if (parents[child] >= 0) malformed(location, "node $child already has parent ${parents[child]}; a node has at most one")
            parents[child] = index
        }
    }
    validateDepths(parents, limits.maxNodeDepth)
    scenes.forEachIndexed { sceneIndex, scene ->
        val seen = HashSet<Int>()
        scene.nodes.forEachIndexed { slot, root ->
            val location = "scenes[$sceneIndex].nodes[$slot]"
            if (root !in nodes.indices) malformed(location, "is node $root, but the document has ${nodes.size} nodes")
            if (parents[root] >= 0) malformed(location, "node $root is a child of node ${parents[root]}, so it cannot be a root")
            if (!seen.add(root)) malformed(location, "lists node $root twice")
        }
    }
}

/** The root nodes of the scene to load: [GltfDocument.scene], or the first scene when it names none. */
internal fun GltfDocument.sceneRoots(): List<Int> {
    val index = scene ?: return scenes.firstOrNull()?.nodes.orEmpty()
    return scenes.getOrNull(index)?.nodes ?: malformed("scene", "is $index, but the document has ${scenes.size} scenes")
}

private fun GltfDocument.validateNodeFields(index: Int, node: GltfNode) {
    val location = "nodes[$index]"
    node.mesh?.let { if (it !in meshes.indices) malformed("$location.mesh", "is mesh $it, but the document has ${meshes.size} meshes") }
    node.skin?.let { if (it !in skins.indices) malformed("$location.skin", "is skin $it, but the document has ${skins.size} skins") }
    requireSize(node.matrix, MATRIX_ELEMENTS, "$location.matrix")
    requireSize(node.translation, VEC3_ELEMENTS, "$location.translation")
    requireSize(node.rotation, QUAT_ELEMENTS, "$location.rotation")
    requireSize(node.scale, VEC3_ELEMENTS, "$location.scale")
}

private fun requireSize(values: List<Float>?, size: Int, location: String) {
    if (values != null && values.size != size) malformed(location, "has ${values.size} values, not $size")
}

/** Walks each node up through [parents], failing on a cycle or a chain deeper than [maxDepth]. Linear: each depth is found once. */
private fun validateDepths(parents: IntArray, maxDepth: Int) {
    val depths = IntArray(parents.size)
    val onPath = BooleanArray(parents.size)
    val path = ArrayList<Int>()
    for (start in parents.indices) {
        var current = start
        while (current >= 0 && depths[current] == 0) {
            if (onPath[current]) malformed("nodes[$current].children", "node $current is its own ancestor; the hierarchy has a cycle")
            onPath[current] = true
            path += current
            current = parents[current]
        }
        var depth = if (current >= 0) depths[current] else 0
        for (step in path.indices.reversed()) {
            val node = path[step]
            depth += 1
            if (depth > maxDepth) malformed("nodes[$node]", "is $depth levels deep, past the limit of $maxDepth")
            depths[node] = depth
            onPath[node] = false
        }
        path.clear()
    }
}
