/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import com.awakekt.awake.core.io.BinaryReader
import kotlinx.serialization.json.Json

// glTF Binary (GLB) container -- see https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#glb-file-format
private const val GLB_MAGIC = 0x46546C67L
private const val GLB_VERSION = 2L
private const val GLB_HEADER_SIZE = 12
private const val GLB_CHUNK_HEADER_SIZE = 8
private const val GLB_CHUNK_ALIGNMENT = 4
private const val GLB_CHUNK_TYPE_JSON = 0x4E4F534AL
private const val GLB_CHUNK_TYPE_BIN = 0x004E4942L
private const val HEX_RADIX = 16

private val GltfJson = Json { ignoreUnknownKeys = true }

/** A GLB file's JSON chunk text and its BIN chunk bytes, `null` when it has none (every buffer then has its own uri). */
internal class GlbContents(val json: String, val bin: ByteArray?)

/**
 * Reads a GLB file's 12-byte header and its chunks. Every length is read unsigned, checked against the
 * bytes that follow it and checked for 4-byte alignment before the chunk is touched, so a lying length
 * fails instead of looping or reading out of bounds. Chunks of other types are skipped.
 */
internal fun readGlbContainer(bytes: ByteArray, context: GltfReadContext): GlbContents {
    val totalLength = glbLength(bytes)
    val reader = BinaryReader(bytes)
    var json: String? = null
    var bin: ByteArray? = null
    var offset = GLB_HEADER_SIZE.toLong()
    var chunk = 0
    while (offset < totalLength) {
        val location = "glb.chunks[$chunk]"
        if (totalLength - offset < GLB_CHUNK_HEADER_SIZE) {
            malformed(location, "its 8-byte header at byte $offset runs past the declared length $totalLength")
        }
        val length = reader.readUnsignedIntLeAt(offset.toInt())
        val type = reader.readUnsignedIntLeAt(offset.toInt() + Int.SIZE_BYTES)
        val start = offset + GLB_CHUNK_HEADER_SIZE
        if (length > totalLength - start) {
            malformed("$location.length", "is $length, but only ${totalLength - start} bytes follow its header")
        }
        if (length % GLB_CHUNK_ALIGNMENT != 0L) malformed("$location.length", "is $length, not a multiple of 4")
        val end = (start + length).toInt()
        when (type) {
            GLB_CHUNK_TYPE_JSON -> {
                if (json != null) malformed(location, "is a second JSON chunk")
                if (length > context.limits.maxJsonBytes) {
                    malformed("$location.length", "is $length, past the JSON limit of ${context.limits.maxJsonBytes} bytes")
                }
                json = bytes.decodeToString(start.toInt(), end)
            }

            GLB_CHUNK_TYPE_BIN -> {
                if (bin != null) malformed(location, "is a second BIN chunk")
                context.charge(location, length)
                bin = bytes.copyOfRange(start.toInt(), end)
            }
        }
        offset = end.toLong()
        chunk++
    }
    return GlbContents(json ?: malformed("glb", "has no JSON chunk"), bin)
}

/** The file length a GLB header declares, after checking its magic, version and that the length fits [bytes]. */
private fun glbLength(bytes: ByteArray): Long {
    if (bytes.size < GLB_HEADER_SIZE) {
        malformed("glb.header", "the file is ${bytes.size} bytes, shorter than the $GLB_HEADER_SIZE-byte GLB header")
    }
    val reader = BinaryReader(bytes)
    val magic = reader.readUnsignedIntLeAt(0)
    if (magic != GLB_MAGIC) malformed("glb.header.magic", "is 0x${magic.toString(HEX_RADIX)}, not 0x46546c67 ('glTF')")
    val version = reader.readUnsignedIntLeAt(Int.SIZE_BYTES)
    if (version != GLB_VERSION) malformed("glb.header.version", "is $version; only GLB version 2 is read")
    val totalLength = reader.readUnsignedIntLeAt(Int.SIZE_BYTES * 2)
    if (totalLength < GLB_HEADER_SIZE || totalLength > bytes.size) {
        malformed("glb.header.length", "is $totalLength, but the file is ${bytes.size} bytes")
    }
    return totalLength
}

/** [json] decoded as a [GltfDocument], after checking its size against [limits]. */
internal fun decodeDocument(json: String, limits: GltfLimits): GltfDocument {
    if (json.length > limits.maxJsonBytes) {
        malformed("json", "is ${json.length} characters, past the limit of ${limits.maxJsonBytes}")
    }
    return try {
        GltfJson.decodeFromString(GltfDocument.serializer(), json)
    } catch (invalid: IllegalArgumentException) {
        // kotlinx.serialization's SerializationException, and NumberFormatException, are both IllegalArgumentExceptions.
        throw GltfFormatException("json", "is not a glTF document: ${invalid.message}", invalid)
    }
}

/**
 * Each of [document]'s buffers' bytes: a base64 data URI, a file from [externalResources], or, when
 * [glbBin] is given, the GLB's BIN chunk for a buffer with no uri. A buffer holding fewer bytes than its
 * `byteLength` declares fails.
 */
internal fun decodeBuffers(
    document: GltfDocument,
    externalResources: Map<String, ByteArray>,
    context: GltfReadContext,
    glb: GlbContents? = null,
): List<ByteArray> = document.buffers.mapIndexed { index, buffer ->
    val location = "buffers[$index]"
    val uri = buffer.uri
    val bytes = when {
        uri == null && glb != null -> glb.bin ?: malformed(location, "has no uri, and the GLB file has no BIN chunk")
        uri == null -> malformed("$location.uri", "is missing; only a buffer stored in a GLB's BIN chunk may omit it")
        uri.startsWith("data:") -> decodeDataUri(uri, "$location.uri", context)
        else -> externalResources[uri] ?: malformed(
            "$location.uri",
            "'$uri' is an external file that was not passed in externalResources; fetch it (see GltfParser.externalUris) " +
                "and pass its bytes in before parsing",
        )
    }
    if (buffer.byteLength < 0 || buffer.byteLength > bytes.size) {
        malformed("$location.byteLength", "is ${buffer.byteLength}, but the buffer holds ${bytes.size} bytes")
    }
    bytes
}
