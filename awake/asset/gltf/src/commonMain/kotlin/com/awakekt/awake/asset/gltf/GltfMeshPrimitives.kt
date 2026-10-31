/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

private const val GLB_MAGIC_BYTES = 4

/**
 * Every primitive of every mesh in a glTF asset, decoded, in document order: `result[mesh][primitive]`.
 * No node transform is applied, so each primitive is in its mesh's own space. Reads a GLB container or a
 * `.gltf` JSON document, whichever [bytes] holds. A tool that rewrites an asset, such as a LOD cooker,
 * reads the vertex streams through this and keeps the document's own JSON for everything else.
 *
 * @param bytes A GLB file, or a `.gltf` document's UTF-8 JSON.
 * @param externalResources Map of external URIs to their pre-fetched byte content.
 * @param limits The bounds the parse runs under.
 * @throws GltfFormatException If the asset is malformed or exceeds [limits].
 */
fun GltfParser.parseMeshPrimitives(
    bytes: ByteArray,
    externalResources: Map<String, ByteArray> = emptyMap(),
    limits: GltfLimits = GltfLimits.Default,
): List<List<GltfMesh>> = gltfFailuresTyped {
    val context = GltfReadContext(limits)
    val glb = if (isGlb(bytes)) readGlbContainer(bytes, context) else null
    val document = decodeDocument(glb?.json ?: bytes.decodeToString(), limits)
    val buffers = decodeBuffers(document, externalResources, context, glb)
    val reader = GltfDocumentReader(document, buffers, externalResources, context)
    document.meshes.mapIndexed { meshIndex, mesh ->
        mesh.primitives.mapIndexed { primitiveIndex, primitive ->
            reader.readPrimitive(primitive, "meshes[$meshIndex].primitives[$primitiveIndex]")
        }
    }
}

/** Whether [bytes] starts with the GLB magic `glTF`. */
private fun isGlb(bytes: ByteArray): Boolean =
    bytes.size >= GLB_MAGIC_BYTES && bytes[0] == 'g'.code.toByte() && bytes[1] == 'l'.code.toByte() &&
        bytes[2] == 'T'.code.toByte() && bytes[3] == 'F'.code.toByte()
