/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import com.awakekt.awake.asset.gltf.GltfMesh
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Turns [MeshLodSettings.jointImportance], keyed by joint name, into per-vertex simplifier importance. */
@Suppress("ReturnCount") // Each early out is a mesh with no skin or no weighted joint.
internal object JointImportance {
    /**
     * The names of the joints a skinned [mesh] is bound to, indexed as its `JOINTS_0` values are: the
     * joints of the skin on the first node that draws it. Empty when no node draws it skinned.
     */
    fun jointNames(root: JsonObject, nodes: List<JsonElement>, mesh: Int): List<String?> {
        val skin = nodes.firstNotNullOfOrNull { node ->
            val fields = node as? JsonObject
            if (fields?.get("mesh")?.jsonPrimitive?.intOrNull == mesh) fields["skin"]?.jsonPrimitive?.intOrNull else null
        } ?: return emptyList()
        val joints = (root["skins"]?.jsonArray?.getOrNull(skin) as? JsonObject)?.get("joints")?.jsonArray ?: return emptyList()
        return joints.map { joint ->
            val node = joint.jsonPrimitive.intOrNull?.let(nodes::getOrNull) as? JsonObject
            node?.get("name")?.jsonPrimitive?.contentOrNull
        }
    }

    /** Each vertex's importance: its joint weights times each joint's importance, or null when none applies. */
    fun perVertex(mesh: GltfMesh, names: List<String?>, importance: Map<String, Float>): FloatArray? {
        val joints = mesh.jointIndices ?: return null
        val weights = mesh.jointWeights ?: return null
        val byJoint = names.map { name -> name?.let(importance::get) ?: 0f }
        if (byJoint.all { it == 0f }) return null
        val influences = joints.size / (mesh.positions.size / POSITION_COMPONENTS)
        return FloatArray(mesh.positions.size / POSITION_COMPONENTS) { vertex ->
            var sum = 0f
            for (i in 0 until influences) {
                val slot = vertex * influences + i
                sum += weights[slot] * (byJoint.getOrNull(joints[slot]) ?: 0f)
            }
            sum
        }
    }
}
