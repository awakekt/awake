/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import com.awakekt.awake.core.animation.AnimationClip
import com.awakekt.awake.core.animation.AnimationLibrary
import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.inverse
import com.awakekt.awake.core.math.normalMatrix
import kotlin.math.sqrt

/**
 * One glTF node that carries both a mesh and a skin reference -- the "find the thing to render
 * and animate" entry point every skinned-scene consumer starts from. [boneIndex] indexes
 * [LoadedSkinnedScene.skeleton]'s bones, [meshIndex]/[skinIndex] index [LoadedSkinnedScene.meshes]/
 * `.skins`. glTF-specific (a node/mesh/skin cross-reference is part of glTF's own scene-graph
 * schema, not something an engine-neutral [com.awakekt.awake.core.animation.Bone]
 * carries) -- kept here rather than on `Bone` for that reason.
 */
data class SkinnedNodeRef(
    /** Index of the bone in the skeleton. */
    val boneIndex: Int,
    /** Index of the mesh in the document. */
    val meshIndex: Int,
    /** Index of the skin in the document. */
    val skinIndex: Int,
)

/**
 * One glTF node in the default scene that carries a mesh but no skin: a rigid piece, such as a prop
 * parented to a hand joint, drawn whole at its node's transform rather than deformed. [boneIndex]
 * indexes [LoadedSkinnedScene.skeleton]'s bones, [meshIndex] [LoadedSkinnedScene.primitives]. See
 * [bindRigidNode] to draw it with a skin, and [restTransform] for where it sits when no joint carries it.
 */
data class RigidNodeRef(
    /** Index of the node's bone in the skeleton. */
    val boneIndex: Int,
    /** Index of the mesh in the document. */
    val meshIndex: Int,
)

/**
 * Everything [SkinnedMeshDemo][com.awakekt.awake.sample.scene3d.demos.SkinnedMeshDemo]
 * needs to render and animate a skinned glTF asset -- [skeleton] is the whole node hierarchy
 * (so joint-global transforms can be walked), every mesh definition (keyed by
 * [GltfDocument.meshes] index, each already carrying its own [GltfMesh.jointIndices]/
 * [GltfMesh.jointWeights]), every skin, every animation clip -- all in the engine-neutral shapes
 * [com.awakekt.awake.core.animation] defines, not glTF's own JSON schema -- and
 * [skinnedNodes], the glTF-specific mesh+skin cross-references a consumer needs to find which
 * bone/mesh/skin triple to actually render. A mesh holds one primitive per material, so
 * [primitives] has all of them where [meshes] keeps each mesh's first; [rigidNodes] are the mesh
 * nodes with no skin. See [GltfParser.parseSkinned].
 */
data class LoadedSkinnedScene(
    /** The skeletal hierarchy. */
    val skeleton: Skeleton,
    /** Each mesh's first primitive, keyed by [GltfDocument.meshes] index; [primitives] has them all. */
    val meshes: List<GltfMesh>,
    /** The list of skins in the scene. */
    val skins: List<Skin>,
    /** The list of animation clips in the scene. */
    val clips: List<AnimationClip>,
    /** The list of skinned node references. */
    val skinnedNodes: List<SkinnedNodeRef>,
    /** Every primitive of each mesh, in file order, keyed by [GltfDocument.meshes] index like [meshes]. */
    val primitives: List<List<GltfMesh>> = meshes.map { listOf(it) },
    /** Every node in the default scene with a mesh and no skin, in file order. */
    val rigidNodes: List<RigidNodeRef> = emptyList(),
)

/**
 * The mesh/skin/clip triple for one [SkinnedNodeRef] -- [clip] is `null` when the asset has no
 * animation clips at all (a static-pose skinned mesh is valid glTF). Bundles what every
 * skinned-mesh consumer immediately looks up after [GltfParser.parseSkinned] (find the node,
 * resolve its mesh/skin, grab the first clip), so that four-line lookup isn't hand-duplicated at
 * every call site.
 */
data class LoadedSkinnedAsset(
    /** The skeletal hierarchy. */
    val skeleton: Skeleton,
    /** The resolved mesh. */
    val mesh: GltfMesh,
    /** The resolved skin. */
    val skin: Skin,
    /** The first animation clip, if any. */
    val clip: AnimationClip?,
)

/**
 * Resolves [node] (one of this scene's own [LoadedSkinnedScene.skinnedNodes]) into its mesh/
 * skin/first-clip triple.
 */
fun LoadedSkinnedScene.resolve(node: SkinnedNodeRef): LoadedSkinnedAsset = LoadedSkinnedAsset(
    skeleton = skeleton,
    mesh = meshes[node.meshIndex],
    skin = skins[node.skinIndex],
    clip = clips.firstOrNull(),
)

/**
 * Convenience for the common "one skinned mesh per file" case -- resolves the FIRST skinned
 * node, or `null` if the file has none.
 */
fun LoadedSkinnedScene.firstSkinnedAsset(): LoadedSkinnedAsset? = skinnedNodes.firstOrNull()?.let { resolve(it) }

/**
 * Converts this source-format scene's clips into the format-neutral runtime library. Source
 * names are retained when available; unnamed clips receive stable, file-order IDs.
 */
fun LoadedSkinnedScene.toAnimationLibrary(): AnimationLibrary = AnimationLibrary(
    skeleton = skeleton,
    clips = buildMap {
        clips.forEachIndexed { index, clip ->
            val id = clip.name?.takeIf(String::isNotBlank) ?: "clip_$index"
            require(put(id, clip) == null) { "glTF animation clips must have unique names; duplicate '$id'." }
        }
    },
)

/**
 * The rest-pose transform of bone [boneIndex] in the skeleton's space: its own local transform under
 * every ancestor's, as [com.awakekt.awake.core.animation.AnimationPose.jointPalette] composes a
 * joint's before a clip moves it. A rigid node that no joint carries ([bindRigidNode] returns null)
 * draws here.
 */
fun LoadedSkinnedScene.restTransform(boneIndex: Int): Mat4 =
    skeleton.ancestry(boneIndex).fold(Mat4()) { below, bone ->
        Mat4.multiplyColumnMajor(skeleton.bones[bone].localTransform(), below)
    }

/**
 * [node]'s primitives bound rigidly into [skin], or null when neither the node nor an ancestor is one
 * of [skin]'s joints.
 *
 * Each vertex is moved into the skin's bind space and weighted wholly to the nearest joint at or
 * above the node, so the joint palette that poses the skinned meshes carries the piece with that
 * joint: the vertex a palette entry `jointGlobal * inverseBind` draws is `jointGlobal * below * v`,
 * where `below` is the node's transform under its joint. Nodes between the joint and the node are
 * taken at rest; an animation of one of them, or of the node itself, does not move the piece.
 */
fun LoadedSkinnedScene.bindRigidNode(node: RigidNodeRef, skin: Skin): List<GltfMesh>? {
    val ancestry = skeleton.ancestry(node.boneIndex)
    val anchor = ancestry.indexOfFirst { it in skin.joints }
    if (anchor < 0) return null
    val joint = skin.joints.indexOf(ancestry[anchor])
    val below = ancestry.subList(0, anchor).fold(Mat4()) { under, bone ->
        Mat4.multiplyColumnMajor(skeleton.bones[bone].localTransform(), under)
    }
    val bind = requireNotNull(skin.inverseBindMatrices[joint].inverse()) {
        "glTF skin joint $joint (node ${ancestry[anchor]}) has a singular inverse bind matrix, so node " +
            "${node.boneIndex} cannot be bound to it."
    }
    val toBindSpace = Mat4.multiplyColumnMajor(bind, below)
    return primitives[node.meshIndex].map { it.boundRigidly(toBindSpace, joint) }
}

/** [bone] and then each ancestor up to its root, stopping at a repeat in a malformed, cyclic hierarchy. */
private fun Skeleton.ancestry(bone: Int): List<Int> {
    val parents = IntArray(bones.size) { -1 }
    bones.forEachIndexed { parent, node -> node.children.forEach { child -> if (child in parents.indices) parents[child] = parent } }
    val chain = mutableListOf<Int>()
    var current = bone
    while (current >= 0 && current !in chain) {
        chain += current
        current = parents[current]
    }
    return chain
}

/** This primitive moved by [transform], every vertex weighted wholly to skin joint [joint]. */
private fun GltfMesh.boundRigidly(transform: Mat4, joint: Int): GltfMesh {
    val normalTransform = transform.normalMatrix() ?: transform
    return copy(
        positions = transformed(positions, transform, w = 1f, normalize = false),
        normals = normals?.let { transformed(it, normalTransform, w = 0f, normalize = true) },
        jointIndices = IntArray(vertexCount * JOINTS_PER_VERTEX) { if (it % JOINTS_PER_VERTEX == 0) joint else 0 },
        jointWeights = FloatArray(vertexCount * JOINTS_PER_VERTEX) { if (it % JOINTS_PER_VERTEX == 0) 1f else 0f },
    )
}

/** Packed xyz [vectors], each multiplied by [transform] as `(x, y, z, w)`, unit length after if [normalize]. */
private fun transformed(vectors: FloatArray, transform: Mat4, w: Float, normalize: Boolean): FloatArray {
    val out = FloatArray(vectors.size)
    for (base in 0 until vectors.size - 2 step 3) {
        val x = vectors[base]
        val y = vectors[base + 1]
        val z = vectors[base + 2]
        var tx = transform.m00 * x + transform.m01 * y + transform.m02 * z + transform.m03 * w
        var ty = transform.m10 * x + transform.m11 * y + transform.m12 * z + transform.m13 * w
        var tz = transform.m20 * x + transform.m21 * y + transform.m22 * z + transform.m23 * w
        if (normalize) {
            val length = sqrt(tx * tx + ty * ty + tz * tz).takeIf { it > 0f } ?: 1f
            tx /= length
            ty /= length
            tz /= length
        }
        out[base] = tx
        out[base + 1] = ty
        out[base + 2] = tz
    }
    return out
}

private const val JOINTS_PER_VERTEX = 4
