/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import com.awakekt.awake.core.geometry.NormalizedInt
import com.awakekt.awake.core.io.BinaryReader

private const val COMPONENT_TYPE_BYTE = 5120
private const val COMPONENT_TYPE_UNSIGNED_BYTE = 5121
private const val COMPONENT_TYPE_SHORT = 5122
private const val COMPONENT_TYPE_UNSIGNED_SHORT = 5123
private const val COMPONENT_TYPE_UNSIGNED_INT = 5125
private const val COMPONENT_TYPE_FLOAT = 5126

// glTF 2.0 schema bounds for bufferView.byteStride.
private const val MIN_BYTE_STRIDE = 4
private const val MAX_BYTE_STRIDE = 252

private const val JOINTS_PER_VERTEX = 4

private val TYPE_COMPONENTS = mapOf("SCALAR" to 1, "VEC2" to 2, "VEC3" to 3, "VEC4" to 4, "MAT4" to 16)

/** A bufferView checked to lie inside its buffer. */
private class CheckedView(val bytes: ByteArray, val byteOffset: Int, val byteLength: Int, val byteStride: Int?)

/**
 * Where an accessor's elements are: [count] [dense] elements (all zero when it is null), then [sparse]'s
 * substitutions. Every offset it yields has been checked to lie inside its buffer.
 */
private class AccessorLayout(
    val count: Int,
    val components: Int,
    val componentSize: Int,
    val dense: DenseLayout?,
    val sparse: SparseLayout?,
)

/** Element `e` starts at [base] + e × [stride] in [bytes]. */
private class DenseLayout(val bytes: ByteArray, val base: Int, val stride: Int)

/** Sparse substitutions: element [elements]`[k]` takes the tightly packed value `k` at [base] in [bytes]. */
private class SparseLayout(val elements: IntArray, val bytes: ByteArray, val base: Int)

/**
 * Reads accessors out of [buffers]. Each accessor, its bufferView and any sparse substitutions are
 * checked against the bytes actually present, in `Long` arithmetic, and every array is charged to
 * [context] before it is allocated, so a malformed accessor fails with [GltfFormatException] instead of
 * reading out of bounds or allocating what its `count` claims.
 */
internal class GltfAccessorReader(
    private val document: GltfDocument,
    private val buffers: List<ByteArray>,
    private val context: GltfReadContext,
) {
    /**
     * Accessor [index] as [components] floats per element: `FLOAT` as it is, normalized `BYTE`/
     * `UNSIGNED_BYTE`/`SHORT`/`UNSIGNED_SHORT` (a quantized export) decoded through [NormalizedInt].
     * [referrer] is the location that names the accessor.
     */
    fun floats(index: Int, components: Int, referrer: String): FloatArray {
        val accessor = accessorAt(index, referrer)
        val location = "accessors[$index]"
        when (accessor.componentType) {
            COMPONENT_TYPE_FLOAT -> Unit
            COMPONENT_TYPE_BYTE, COMPONENT_TYPE_UNSIGNED_BYTE, COMPONENT_TYPE_SHORT, COMPONENT_TYPE_UNSIGNED_SHORT ->
                if (!accessor.normalized) {
                    malformed(
                        "$location.normalized",
                        "is false for integer componentType ${accessor.componentType}; only normalized integer " +
                            "attributes are read as floats",
                    )
                }

            else -> malformed(
                "$location.componentType",
                "is ${accessor.componentType}; a float attribute must be FLOAT or a normalized BYTE, " +
                    "UNSIGNED_BYTE, SHORT or UNSIGNED_SHORT",
            )
        }
        val layout = layout(index, accessor, components)
        val result = FloatArray(context.arrayLength(location, layout.count, components))
        layout.forEachComponent { destination, bytes, offset ->
            result[destination] = decodeFloat(accessor.componentType, bytes, offset)
        }
        return result
    }

    /** Index accessor [index]: `UNSIGNED_BYTE`/`UNSIGNED_SHORT`/`UNSIGNED_INT` scalars, each below [vertexCount]. */
    fun indices(index: Int, vertexCount: Int, referrer: String): IntArray {
        val accessor = accessorAt(index, referrer)
        val location = "accessors[$index]"
        requireUnsigned(accessor, location, "an index accessor", allowInt = true)
        val layout = layout(index, accessor, components = 1)
        val result = IntArray(context.arrayLength(location, layout.count, 1))
        layout.forEachComponent { destination, bytes, offset ->
            val vertex = decodeUnsigned(accessor.componentType, bytes, offset)
            if (vertex >= vertexCount) {
                malformed(location, "element $destination is vertex $vertex, but the primitive has $vertexCount vertices")
            }
            result[destination] = vertex.toInt()
        }
        return result
    }

    /**
     * A `JOINTS_0` accessor (`ubyte4` or `ushort4`), one entry per component widened to `Int`, matching
     * [com.awakekt.awake.core.geometry.VertexAttributeFormat.UInt4]'s own 4-bytes-per-component layout.
     */
    fun joints(index: Int, referrer: String): IntArray {
        val accessor = accessorAt(index, referrer)
        val location = "accessors[$index]"
        requireUnsigned(accessor, location, "a JOINTS_0 accessor", allowInt = false)
        val layout = layout(index, accessor, JOINTS_PER_VERTEX)
        val result = IntArray(context.arrayLength(location, layout.count, JOINTS_PER_VERTEX))
        layout.forEachComponent { destination, bytes, offset ->
            result[destination] = decodeUnsigned(accessor.componentType, bytes, offset).toInt()
        }
        return result
    }

    /** The declared element count of accessor [index], checked to be in range. */
    fun count(index: Int, referrer: String): Int = accessorAt(index, referrer).count

    /** A copy of bufferView [index]'s bytes, as an embedded image stores them. */
    fun bufferViewBytes(index: Int, referrer: String): ByteArray {
        val view = bufferView(index, referrer)
        context.charge("bufferViews[$index]", view.byteLength.toLong())
        return view.bytes.copyOfRange(view.byteOffset, view.byteOffset + view.byteLength)
    }

    private fun accessorAt(index: Int, referrer: String): GltfAccessor = document.accessors.getOrNull(index)
        ?: malformed(referrer, "is accessor $index, but the document has ${document.accessors.size} accessors")

    private fun layout(index: Int, accessor: GltfAccessor, components: Int): AccessorLayout {
        val location = "accessors[$index]"
        val typeComponents = TYPE_COMPONENTS[accessor.type]
            ?: malformed("$location.type", "is '${accessor.type}', not SCALAR, VEC2, VEC3, VEC4 or MAT4")
        if (typeComponents != components) {
            malformed("$location.type", "is ${accessor.type}, but its use needs $components components per element")
        }
        if (accessor.count < 0) malformed("$location.count", "is negative (${accessor.count})")
        if (accessor.count > context.limits.maxAccessorCount) {
            malformed("$location.count", "is ${accessor.count}, past the limit of ${context.limits.maxAccessorCount}")
        }
        if (accessor.byteOffset < 0) malformed("$location.byteOffset", "is negative (${accessor.byteOffset})")
        val componentSize = componentSize(accessor.componentType)
        val elementSize = components * componentSize
        val view = accessor.bufferView?.let { bufferView(it, "$location.bufferView") }
        val stride = view?.byteStride ?: elementSize
        if (view != null) {
            val needed = accessor.byteOffset.toLong() +
                if (accessor.count == 0) 0L else stride.toLong() * (accessor.count - 1) + elementSize
            if (needed > view.byteLength) {
                malformed(
                    location,
                    "${accessor.count} elements of $elementSize bytes, $stride apart from byte ${accessor.byteOffset}, " +
                        "need $needed bytes, but bufferView ${accessor.bufferView} holds ${view.byteLength}",
                )
            }
        }
        return AccessorLayout(
            count = accessor.count,
            components = components,
            componentSize = componentSize,
            dense = view?.let { DenseLayout(it.bytes, it.byteOffset + accessor.byteOffset, stride) },
            sparse = accessor.sparse?.let { sparseLayout(it, accessor.count, elementSize, "$location.sparse") },
        )
    }

    private fun sparseLayout(sparse: GltfAccessorSparse, accessorCount: Int, elementSize: Int, location: String): SparseLayout {
        if (sparse.count !in 1..accessorCount) {
            malformed("$location.count", "is ${sparse.count}; it must be between 1 and the accessor's count $accessorCount")
        }
        val indexType = sparse.indices.componentType
        val indexSize = when (indexType) {
            COMPONENT_TYPE_UNSIGNED_BYTE, COMPONENT_TYPE_UNSIGNED_SHORT, COMPONENT_TYPE_UNSIGNED_INT -> componentSize(indexType)
            else -> malformed("$location.indices.componentType", "is $indexType, not UNSIGNED_BYTE, UNSIGNED_SHORT or UNSIGNED_INT")
        }
        val indicesBase = sparseRegion(sparse.indices.bufferView, sparse.indices.byteOffset, sparse.count, indexSize, "$location.indices")
        val valuesBase = sparseRegion(sparse.values.bufferView, sparse.values.byteOffset, sparse.count, elementSize, "$location.values")
        val indexBytes = bufferView(sparse.indices.bufferView, "$location.indices.bufferView").bytes
        var previous = -1L
        val elements = IntArray(context.arrayLength("$location.indices", sparse.count, 1)) { entry ->
            val element = decodeUnsigned(indexType, indexBytes, indicesBase + entry * indexSize)
            if (element <= previous || element >= accessorCount) {
                malformed(
                    "$location.indices",
                    "entry $entry is element $element; sparse indices must increase strictly and stay below $accessorCount",
                )
            }
            previous = element
            element.toInt()
        }
        val valueBytes = bufferView(sparse.values.bufferView, "$location.values.bufferView").bytes
        return SparseLayout(elements, valueBytes, valuesBase)
    }

    /** The absolute offset of [count] packed items of [itemSize] bytes at [byteOffset] in bufferView [viewIndex], checked to fit. */
    private fun sparseRegion(viewIndex: Int, byteOffset: Int, count: Int, itemSize: Int, location: String): Int {
        val view = bufferView(viewIndex, "$location.bufferView")
        if (byteOffset < 0) malformed("$location.byteOffset", "is negative ($byteOffset)")
        val needed = byteOffset.toLong() + count.toLong() * itemSize
        if (needed > view.byteLength) {
            malformed(location, "$count entries of $itemSize bytes from byte $byteOffset need $needed bytes, but bufferView $viewIndex holds ${view.byteLength}")
        }
        return view.byteOffset + byteOffset
    }

    private fun bufferView(index: Int, referrer: String): CheckedView {
        val view = document.bufferViews.getOrNull(index)
            ?: malformed(referrer, "is bufferView $index, but the document has ${document.bufferViews.size} bufferViews")
        val location = "bufferViews[$index]"
        val buffer = buffers.getOrNull(view.buffer)
            ?: malformed("$location.buffer", "is buffer ${view.buffer}, but the document has ${buffers.size} buffers")
        if (view.byteOffset < 0) malformed("$location.byteOffset", "is negative (${view.byteOffset})")
        if (view.byteLength < 0) malformed("$location.byteLength", "is negative (${view.byteLength})")
        val end = view.byteOffset.toLong() + view.byteLength
        if (end > buffer.size) {
            malformed(location, "bytes [${view.byteOffset}, $end) run past the end of buffer ${view.buffer} (${buffer.size} bytes)")
        }
        val stride = view.byteStride
        if (stride != null && stride !in MIN_BYTE_STRIDE..MAX_BYTE_STRIDE) {
            malformed("$location.byteStride", "is $stride; it must be between $MIN_BYTE_STRIDE and $MAX_BYTE_STRIDE")
        }
        return CheckedView(buffer, view.byteOffset, view.byteLength, stride)
    }
}

/**
 * Calls [read] for each component of each element: dense data first, then the sparse substitutions,
 * with the component's index in the flattened result and its byte offset in `bytes`.
 */
private inline fun AccessorLayout.forEachComponent(read: (destination: Int, bytes: ByteArray, offset: Int) -> Unit) {
    dense?.let { layout ->
        for (element in 0 until count) {
            val elementBase = layout.base + element * layout.stride
            for (component in 0 until components) {
                read(element * components + component, layout.bytes, elementBase + component * componentSize)
            }
        }
    }
    sparse?.let { substitutions ->
        val elementSize = components * componentSize
        substitutions.elements.forEachIndexed { entry, element ->
            val valueBase = substitutions.base + entry * elementSize
            for (component in 0 until components) {
                read(element * components + component, substitutions.bytes, valueBase + component * componentSize)
            }
        }
    }
}

private fun requireUnsigned(accessor: GltfAccessor, location: String, what: String, allowInt: Boolean) {
    val type = accessor.componentType
    val allowed = type == COMPONENT_TYPE_UNSIGNED_BYTE || type == COMPONENT_TYPE_UNSIGNED_SHORT ||
        (allowInt && type == COMPONENT_TYPE_UNSIGNED_INT)
    if (!allowed) {
        val types = if (allowInt) "UNSIGNED_BYTE, UNSIGNED_SHORT or UNSIGNED_INT" else "UNSIGNED_BYTE or UNSIGNED_SHORT"
        malformed("$location.componentType", "is $type; $what must be $types")
    }
}

private fun componentSize(componentType: Int): Int = when (componentType) {
    COMPONENT_TYPE_BYTE, COMPONENT_TYPE_UNSIGNED_BYTE -> 1
    COMPONENT_TYPE_SHORT, COMPONENT_TYPE_UNSIGNED_SHORT -> 2
    COMPONENT_TYPE_UNSIGNED_INT, COMPONENT_TYPE_FLOAT -> 4
    else -> malformed("componentType", "$componentType is not a glTF component type")
}

/** A float or normalized-integer component; the caller has checked [componentType] is one of them. */
private fun decodeFloat(componentType: Int, bytes: ByteArray, offset: Int): Float {
    val reader = BinaryReader(bytes)
    return when (componentType) {
        COMPONENT_TYPE_FLOAT -> reader.readFloatLeAt(offset)
        COMPONENT_TYPE_BYTE -> NormalizedInt.signedByte(bytes[offset].toInt())
        COMPONENT_TYPE_UNSIGNED_BYTE -> NormalizedInt.unsignedByte(bytes[offset].toInt() and 0xFF)
        COMPONENT_TYPE_SHORT -> NormalizedInt.signedShort(reader.readShortLeAt(offset))
        else -> NormalizedInt.unsignedShort(reader.readUnsignedShortLeAt(offset))
    }
}

/** An unsigned byte, short or int component; the caller has checked [componentType] is one of them. */
private fun decodeUnsigned(componentType: Int, bytes: ByteArray, offset: Int): Long = when (componentType) {
    COMPONENT_TYPE_UNSIGNED_BYTE -> (bytes[offset].toInt() and 0xFF).toLong()
    COMPONENT_TYPE_UNSIGNED_SHORT -> BinaryReader(bytes).readUnsignedShortLeAt(offset).toLong()
    else -> BinaryReader(bytes).readUnsignedIntLeAt(offset)
}
