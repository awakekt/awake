/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.gltf

import io.github.awakelab.awake.core.animation.AnimationClip
import io.github.awakelab.awake.core.animation.AnimationLibrary
import io.github.awakelab.awake.core.animation.Skeleton
import io.github.awakelab.awake.core.animation.Skin

/**
 * One glTF node that carries both a mesh and a skin reference -- the "find the thing to render
 * and animate" entry point every skinned-scene consumer starts from. [boneIndex] indexes
 * [LoadedSkinnedScene.skeleton]'s bones, [meshIndex]/[skinIndex] index [LoadedSkinnedScene.meshes]/
 * `.skins`. glTF-specific (a node/mesh/skin cross-reference is part of glTF's own scene-graph
 * schema, not something an engine-neutral [io.github.awakelab.awake.core.animation.Bone]
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
 * Everything [SkinnedMeshDemo][io.github.awakelab.awake.sample.scene3d.demos.SkinnedMeshDemo]
 * needs to render and animate a skinned glTF asset -- [skeleton] is the whole node hierarchy
 * (so joint-global transforms can be walked), every mesh definition (keyed by
 * [GltfDocument.meshes] index, each already carrying its own [GltfMesh.jointIndices]/
 * [GltfMesh.jointWeights]), every skin, every animation clip -- all in the engine-neutral shapes
 * [io.github.awakelab.awake.core.animation] defines, not glTF's own JSON schema -- and
 * [skinnedNodes], the glTF-specific mesh+skin cross-references a consumer needs to find which
 * bone/mesh/skin triple to actually render. See [GltfParser.parseSkinned].
 */
data class LoadedSkinnedScene(
    /** The skeletal hierarchy. */
    val skeleton: Skeleton,
    /** The list of meshes in the scene. */
    val meshes: List<GltfMesh>,
    /** The list of skins in the scene. */
    val skins: List<Skin>,
    /** The list of animation clips in the scene. */
    val clips: List<AnimationClip>,
    /** The list of skinned node references. */
    val skinnedNodes: List<SkinnedNodeRef>,
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
