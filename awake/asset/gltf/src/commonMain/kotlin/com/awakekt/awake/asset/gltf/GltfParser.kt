/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import com.awakekt.awake.asset.gltf.GltfParser.parse
import com.awakekt.awake.asset.gltf.GltfParser.parseScene
import com.awakekt.awake.asset.gltf.GltfParser.parseSkinned
import com.awakekt.awake.core.animation.Bone
import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.resolve
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f

/**
 * Minimal glTF 2.0 mesh importer -- JSON structure + embedded (base64 data-URI) buffers.
 * Its scope is deliberately narrow:
 * - **External `.bin`/image files** are supported via the `externalResources` parameter --
 *   a `uri -> bytes` map the caller pre-fetches (e.g. with `readResourceBytes`, which is
 *   `suspend`, so this parser stays synchronous). Use [externalUris] to find out which URIs
 *   a document references before parsing it.
 * - **[parse] reads only the first mesh's first primitive** -- its `POSITION`/`NORMAL`/
 *   `COLOR_0`/`TEXCOORD_0`/`JOINTS_0`/`WEIGHTS_0` attributes, its index accessor, and (if
 *   present) its material's base color/metallic-roughness/normal/occlusion/emissive texture
 *   image bytes. Reading stops at the still-encoded image bytes per channel; no other material
 *   fields (normal `scale`, occlusion `strength`) are read. [parseScene] walks the full node
 *   hierarchy for static meshes; [parseSkinned] walks it for skeletal skinning/animation (skins +
 *   animation clips too).
 * - **Only `FLOAT` vertex attributes and `UNSIGNED_BYTE`/`UNSIGNED_SHORT`/`UNSIGNED_INT`
 *   indices** are decoded -- glTF also allows signed `BYTE`/`SHORT` and normalized integer
 *   attributes, neither of which real exporters emit for `POSITION`/`NORMAL`/`COLOR_0` in
 *   practice.
 *
 * **Untrusted input.** Every parse treats its bytes as untrusted: GLB chunk lengths, buffer and
 * bufferView ranges, accessor counts, strides and offsets, sparse substitutions, index values,
 * base64 payloads and the node hierarchy are all checked against the input before anything is
 * allocated or read, and the parse runs under [GltfLimits]. A file that fails any check throws
 * [GltfFormatException], naming what is wrong and where; no other exception escapes a parse of a
 * malformed file.
 */
object GltfParser {
    /** Loads a glTF asset and its relative `.bin`/image sidecars through Core [AssetSource]. */
    suspend fun load(path: AssetPath, source: AssetSource, limits: GltfLimits = GltfLimits.Default): Result<GltfMesh> =
        runCatching {
            val json = source.read(path).getOrThrow().decodeToString()
            val external = loadExternalResources(json, path, source, limits).getOrThrow()
            parse(json, external, limits)
        }

    /**
     * Fetches all non-data URIs referenced by [json], resolved relative to [assetPath]. A URI is a
     * percent-encoded RFC 3986 reference, as glTF requires, so `Part%20Foot.png` reads the file
     * `Part Foot.png`. The result is keyed by each URI as the document writes it.
     */
    suspend fun loadExternalResources(
        json: String,
        assetPath: AssetPath,
        source: AssetSource,
        limits: GltfLimits = GltfLimits.Default,
    ): Result<Map<String, ByteArray>> = runCatching {
        externalUris(json, limits).associateWith { uri ->
            source.read(assetPath.resolve(percentDecoded(uri))).getOrThrow()
        }
    }

    /**
     * [uri] with its percent-encoded bytes decoded as UTF-8, so `Part%20Foot.png` is
     * `Part Foot.png` and `caf%C3%A9.png` is `café.png`. A `%` that isn't followed by two hex
     * digits stays as it is, and so does `+`, which only form encoding treats as a space.
     */
    internal fun percentDecoded(uri: String): String {
        if ('%' !in uri) return uri
        val encoded = uri.encodeToByteArray()
        val decoded = ByteArray(encoded.size)
        var read = 0
        var written = 0
        while (read < encoded.size) {
            val high = if (encoded[read] == PERCENT && read + 2 <= encoded.lastIndex) hexValue(encoded[read + 1]) else -1
            val low = if (high >= 0) hexValue(encoded[read + 2]) else -1
            if (low >= 0) {
                decoded[written++] = (high * HEX_RADIX + low).toByte()
                read += ESCAPE_LENGTH
            } else {
                decoded[written++] = encoded[read++]
            }
        }
        return decoded.decodeToString(0, written)
    }

    private fun hexValue(byte: Byte): Int = when (val char = byte.toInt().toChar()) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + DECIMAL_DIGITS
        in 'A'..'F' -> char - 'A' + DECIMAL_DIGITS
        else -> -1
    }

    private const val PERCENT = '%'.code.toByte()
    private const val HEX_RADIX = 16
    private const val DECIMAL_DIGITS = 10
    private const val ESCAPE_LENGTH = 3

    /**
     * Parses the first mesh from a glTF JSON string.
     *
     * @param json The glTF JSON content.
     * @param externalResources Map of external URIs to their pre-fetched byte content.
     * @param limits The bounds the parse runs under.
     * @throws GltfFormatException If the document is malformed or exceeds [limits].
     */
    fun parse(
        json: String,
        externalResources: Map<String, ByteArray> = emptyMap(),
        limits: GltfLimits = GltfLimits.Default,
    ): GltfMesh = gltfFailuresTyped {
        val context = GltfReadContext(limits)
        val document = decodeDocument(json, limits)
        val mesh = document.meshes.firstOrNull() ?: malformed("meshes", "is empty; the document has no mesh")
        val primitive = mesh.primitives.firstOrNull() ?: malformed("meshes[0].primitives", "is empty")
        val buffers = decodeBuffers(document, externalResources, context)
        GltfDocumentReader(document, buffers, externalResources, context).readPrimitive(primitive, "meshes[0].primitives[0]")
    }

    /**
     * Extracts every external URI from a glTF JSON string that needs to be pre-fetched
     * (e.g. via `readResourceBytes`, relative to the `.gltf`'s own resource path) and pass
     * back in as `externalResources` before calling [parse]/[parseScene]/[parseSkinned].
     *
     * @param json The glTF JSON content.
     * @param limits The bounds the parse runs under.
     * @return List of external URIs.
     * @throws GltfFormatException If [json] is not a glTF document or exceeds [limits].
     */
    fun externalUris(json: String, limits: GltfLimits = GltfLimits.Default): List<String> = gltfFailuresTyped {
        val document = decodeDocument(json, limits)
        val bufferUris = document.buffers.mapNotNull { it.uri }
        val imageUris = document.images.mapNotNull { it.uri }
        (bufferUris + imageUris).filterNot { it.startsWith("data:") }.distinct()
    }

    /**
     * Parses a glTF Binary (GLB) container -- header + JSON chunk + optional BIN chunk --
     * walks its `scenes`/`nodes` graph, and returns every mesh reachable from the default
     * scene with each primitive's owning node's composed world transform attached. Unlike
     * [parse], this reads every mesh/primitive in the document (not just the first), and
     * resolves buffers with no `uri` (i.e. glTF's "use the GLB BIN chunk" convention) in
     * addition to base64 data URIs.
     *
     * @param bytes The GLB file bytes.
     * @param externalResources Map of external URIs to their pre-fetched byte content.
     * @param limits The bounds the parse runs under.
     * @throws GltfFormatException If the file is malformed or exceeds [limits].
     */
    fun parseScene(
        bytes: ByteArray,
        externalResources: Map<String, ByteArray> = emptyMap(),
        limits: GltfLimits = GltfLimits.Default,
    ): LoadedScene = gltfFailuresTyped {
        val context = GltfReadContext(limits)
        val glb = readGlbContainer(bytes, context)
        val document = decodeDocument(glb.json, limits)
        val buffers = decodeBuffers(document, externalResources, context, glb)
        document.validateNodeGraph(limits)
        val reader = GltfDocumentReader(document, buffers, externalResources, context)
        val loadedMeshes = mutableListOf<LoadedMesh>()

        // The node graph is checked: each node is visited once, and the recursion is at most maxNodeDepth deep.
        fun visit(nodeIndex: Int, parentWorld: Mat4) {
            val node = document.nodes[nodeIndex]
            val world = Mat4.multiplyColumnMajor(parentWorld, nodeLocalTransform(node))
            node.mesh?.let { meshIndex ->
                val meshDef = document.meshes[meshIndex]
                val primitives = meshDef.primitives.mapIndexed { primitiveIndex, primitive ->
                    reader.readPrimitive(primitive, "meshes[$meshIndex].primitives[$primitiveIndex]").placedAt(world)
                }
                loadedMeshes += LoadedMesh(meshDef.name ?: "", primitives)
            }
            node.children.forEach { visit(it, world) }
        }

        document.sceneRoots().forEach { visit(it, Mat4()) }
        LoadedScene(loadedMeshes)
    }

    /**
     * Parses a plain-JSON embedded-buffer glTF (same input shape as [parse] -- NOT a GLB
     * container, see [parse]'s own doc comment) into every mesh/node/skin/animation the document
     * has, for skeletal skinning/animation playback. Unlike [parse] (first mesh/primitive only)
     * this reads the whole node hierarchy -- a skin's joints are node indices, so the demo layer
     * needs the real scene graph, not just one primitive's raw attributes -- and every primitive
     * of every mesh ([LoadedSkinnedScene.primitives]), because an exporter writes one primitive
     * per material. Mesh nodes with no skin in the default scene are listed too
     * ([LoadedSkinnedScene.rigidNodes]); see [bindRigidNode] for drawing one with the skin.
     *
     * @param json The glTF JSON content.
     * @param externalResources Map of external URIs to their pre-fetched byte content.
     * @param limits The bounds the parse runs under.
     * @throws GltfFormatException If the document is malformed or exceeds [limits].
     */
    fun parseSkinned(
        json: String,
        externalResources: Map<String, ByteArray> = emptyMap(),
        limits: GltfLimits = GltfLimits.Default,
    ): LoadedSkinnedScene = gltfFailuresTyped {
        val context = GltfReadContext(limits)
        val document = decodeDocument(json, limits)
        val buffers = decodeBuffers(document, externalResources, context)
        document.validateNodeGraph(limits)
        val reader = GltfDocumentReader(document, buffers, externalResources, context)

        val bones = document.nodes.map { node ->
            Bone(
                translation = node.translation?.let { Vec3f(it[0], it[1], it[2]) } ?: Vec3f(0f, 0f, 0f),
                rotation = node.rotation?.let { Quat(it[0], it[1], it[2], it[3]) } ?: Quat(0f, 0f, 0f, 1f),
                scale = node.scale?.let { Vec3f(it[0], it[1], it[2]) } ?: Vec3f(1f, 1f, 1f),
                matrix = node.matrix?.let(::columnMajor),
                children = node.children,
                name = node.name,
            )
        }
        val primitives = document.meshes.mapIndexed { meshIndex, meshDef ->
            if (meshDef.primitives.isEmpty()) malformed("meshes[$meshIndex].primitives", "is empty")
            meshDef.primitives.mapIndexed { primitiveIndex, primitive ->
                reader.readPrimitive(primitive, "meshes[$meshIndex].primitives[$primitiveIndex]")
            }
        }
        val skins = document.skins.indices.map(reader::readSkin)
        val clips = document.animations.indices.map(reader::readAnimation)
        val rootNodes = document.sceneRoots()
        val inScene = reachableNodes(document, rootNodes)
        val rigidNodes = document.nodes.mapIndexedNotNull { index, node ->
            val meshIndex = node.mesh
            if (meshIndex != null && node.skin == null && index in inScene) RigidNodeRef(index, meshIndex) else null
        }
        val skinnedNodes = skinnedNodes(document, primitives, skins.map { it.joints.size })

        LoadedSkinnedScene(
            skeleton = Skeleton(bones, rootNodes),
            meshes = primitives.map { it.first() },
            skins = skins,
            clips = clips,
            skinnedNodes = skinnedNodes,
            primitives = primitives,
            rigidNodes = rigidNodes,
        )
    }

    /**
     * Every node with both a mesh and a skin, after checking that each joint index the mesh's vertices
     * use names one of the skin's [jointCounts] joints, so a skinning shader never reads past the palette.
     */
    private fun skinnedNodes(
        document: GltfDocument,
        primitives: List<List<GltfMesh>>,
        jointCounts: List<Int>,
    ): List<SkinnedNodeRef> {
        val highestJoint = primitives.map { mesh -> mesh.maxOf { primitive -> primitive.jointIndices?.maxOrNull() ?: -1 } }
        return document.nodes.mapIndexedNotNull { index, node ->
            val meshIndex = node.mesh ?: return@mapIndexedNotNull null
            val skinIndex = node.skin ?: return@mapIndexedNotNull null
            if (highestJoint[meshIndex] >= jointCounts[skinIndex]) {
                malformed(
                    "nodes[$index]",
                    "mesh $meshIndex uses joint ${highestJoint[meshIndex]}, but skin $skinIndex has ${jointCounts[skinIndex]} joints",
                )
            }
            SkinnedNodeRef(index, meshIndex, skinIndex)
        }
    }

    /** Every node index reachable from [roots] through `children`, each visited once. */
    private fun reachableNodes(document: GltfDocument, roots: List<Int>): Set<Int> {
        val reached = mutableSetOf<Int>()
        val pending = ArrayDeque(roots)
        while (pending.isNotEmpty()) {
            val index = pending.removeLast()
            if (reached.add(index)) pending.addAll(document.nodes[index].children)
        }
        return reached
    }

    /** This node's local transform -- [GltfNode.matrix] verbatim (glTF matrices are already
     * column-major 16 floats, matching [Mat4.data]'s own layout) when present, otherwise
     * composed from TRS (missing components default to identity translation/rotation/scale
     * per the glTF 2.0 spec). Its sizes were checked by [validateNodeGraph]. */
    private fun nodeLocalTransform(node: GltfNode): Mat4 {
        node.matrix?.let { return columnMajor(it) }
        val t = node.translation ?: listOf(0f, 0f, 0f)
        val r = node.rotation ?: listOf(0f, 0f, 0f, 1f)
        val s = node.scale ?: listOf(1f, 1f, 1f)
        return Mat4.fromTrs(
            translation = Vec3f(t[0], t[1], t[2]),
            rotation = Quat(r[0], r[1], r[2], r[3]),
            scale = Vec3f(s[0], s[1], s[2]),
        )
    }

    private fun columnMajor(values: List<Float>): Mat4 = Mat4().apply { values.forEachIndexed { i, value -> data[i] = value } }
}

/** This primitive as [GltfParser.parseScene] returns it: interleaved and placed at [world]. */
private fun GltfMesh.placedAt(world: Mat4): LoadedPrimitive = LoadedPrimitive(
    toInterleavedPositionColorUv(),
    indices,
    world,
    baseColorImageBytes,
    metallicRoughnessImageBytes,
    normalImageBytes,
    occlusionImageBytes,
    emissiveImageBytes,
    baseColorFactor,
    metallicFactor,
    roughnessFactor,
    emissiveFactor,
    alphaMode,
)
