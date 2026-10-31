/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A glTF document being given LOD meshes. Only adds: index data goes into the GLB's BIN chunk, or a new
 * embedded buffer for a `.gltf` (or a GLB without one), and each index buffer gets its own bufferView and
 * accessor. Everything already in the document stays at its index, unchanged.
 */
internal class LodDocument(private val root: JsonObject, private val glb: GlbFile?) {
    val nodes: MutableList<JsonElement> = list("nodes")
    val meshes: MutableList<JsonElement> = list("meshes")
    private val buffers = list("buffers")
    private val bufferViews = list("bufferViews")
    private val accessors = list("accessors")
    private val appendToBin = glb?.bin != null && (buffers.firstOrNull() as? JsonObject)?.get("uri") == null
    private val bufferIndex = if (appendToBin) 0 else buffers.size
    private val baseOffset = if (appendToBin) aligned(glb!!.bin!!.size) else 0
    private var data = ByteArray(INITIAL_CAPACITY)
    private var size = 0

    /** Adds [indices] as an index accessor, 16-bit when [vertexCount] allows, and returns its index. */
    fun addIndices(indices: IntArray, vertexCount: Int): Int {
        val wide = vertexCount > UNSIGNED_SHORT_LIMIT
        val elementSize = if (wide) Int.SIZE_BYTES else Short.SIZE_BYTES
        size = aligned(size)
        val offset = size
        ensure(size + indices.size * elementSize)
        for (index in indices) for (i in 0 until elementSize) data[size++] = (index ushr (i * Byte.SIZE_BITS)).toByte()
        bufferViews += JsonObject(
            mapOf(
                "buffer" to JsonPrimitive(bufferIndex),
                "byteOffset" to JsonPrimitive(baseOffset + offset),
                "byteLength" to JsonPrimitive(indices.size * elementSize),
                "target" to JsonPrimitive(ELEMENT_ARRAY_BUFFER),
            ),
        )
        accessors += JsonObject(
            mapOf(
                "bufferView" to JsonPrimitive(bufferViews.lastIndex),
                "componentType" to JsonPrimitive(if (wide) UNSIGNED_INT else UNSIGNED_SHORT),
                "count" to JsonPrimitive(indices.size),
                "type" to JsonPrimitive("SCALAR"),
            ),
        )
        return accessors.lastIndex
    }

    fun addMesh(mesh: JsonObject): Int = meshes.add(mesh).let { meshes.lastIndex }

    fun addNode(node: JsonObject): Int = nodes.add(node).let { nodes.lastIndex }

    /** The finished asset, in the container it came in. */
    @OptIn(ExperimentalEncodingApi::class)
    fun encode(): ByteArray {
        val added = data.copyOf(size)
        val bin = glb?.bin
        if (appendToBin && bin != null) {
            val merged = bin.copyOf(baseOffset + added.size)
            added.copyInto(merged, baseOffset)
            buffers[0] = (buffers[0] as JsonObject).with("byteLength", JsonPrimitive(merged.size))
            return GlbFile(json(), merged).encode()
        }
        if (added.isNotEmpty()) {
            buffers += JsonObject(
                mapOf(
                    "uri" to JsonPrimitive("data:application/octet-stream;base64," + Base64.encode(added)),
                    "byteLength" to JsonPrimitive(added.size),
                ),
            )
        }
        return glb?.let { GlbFile(json(), it.bin).encode() } ?: json().encodeToByteArray()
    }

    private fun json(): String {
        val used = root["extensionsUsed"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val out = LinkedHashMap(root)
        out["extensionsUsed"] = JsonArray((used + GltfLods.EXTENSION).distinct().map(::JsonPrimitive))
        listOf("buffers" to buffers, "bufferViews" to bufferViews, "accessors" to accessors, "meshes" to meshes, "nodes" to nodes)
            .forEach { (key, list) -> if (list.isNotEmpty()) out[key] = JsonArray(list) }
        return Json.encodeToString(JsonObject.serializer(), JsonObject(out))
    }

    private fun list(key: String): MutableList<JsonElement> = root[key]?.jsonArray?.toMutableList() ?: mutableListOf()

    private fun ensure(capacity: Int) {
        if (capacity > data.size) data = data.copyOf(maxOf(capacity, data.size * 2))
    }

    private fun aligned(value: Int): Int = (value + ALIGNMENT - 1) / ALIGNMENT * ALIGNMENT

    private companion object {
        const val INITIAL_CAPACITY = 1024
        const val ALIGNMENT = 4
        const val UNSIGNED_SHORT_LIMIT = 65535
        const val UNSIGNED_SHORT = 5123
        const val UNSIGNED_INT = 5125
        const val ELEMENT_ARRAY_BUFFER = 34963
    }
}

/** This object with [key] set to [value]. */
internal fun JsonObject.with(key: String, value: JsonElement): JsonObject = JsonObject(LinkedHashMap(this).also { it[key] = value })
