/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

/**
 * The one way a [GltfParser] parse fails: the input is malformed, breaks a glTF 2.0 rule the parser
 * relies on, asks for more than its [GltfLimits] allow, or references an external file the caller did
 * not pass in. A malformed file never surfaces as a raw `IndexOutOfBoundsException`,
 * `NumberFormatException` or serialization error, never hangs and never allocates past
 * [GltfLimits.maxDecodedBytes].
 *
 * It is an [IllegalArgumentException], so a caller that already catches that keeps working.
 *
 * @property location Where the problem is, as a path into the input: `glb.chunks[1]`,
 *   `accessors[3].count`, `nodes[7].children[0]`, `buffers[0].uri`, or `json` for the JSON text itself.
 * @property problem What is wrong there.
 * @param cause The error that exposed the problem, when one did.
 */
class GltfFormatException(
    val location: String,
    val problem: String,
    cause: Throwable? = null,
) : IllegalArgumentException("glTF $location: $problem", cause)

/**
 * The bounds a parse of untrusted glTF input runs under. Every count, offset and length a file declares
 * is checked against the bytes actually present, so these limits only matter for a file that is valid
 * but larger than a caller wants to accept, or one that repeats a large reference many times. Exceeding
 * one throws [GltfFormatException]. [Default] fits any asset a game ships.
 *
 * @property maxJsonBytes Largest JSON text, in bytes for a GLB's JSON chunk and in characters for a
 *   `.gltf` string.
 * @property maxAccessorCount Largest `count` one accessor may declare.
 * @property maxDecodedBytes Total bytes one parse may allocate for decoded buffers, the GLB BIN chunk,
 *   accessor arrays and image bytes. It bounds a file that points many meshes at one large accessor.
 * @property maxNodeDepth Deepest node hierarchy, counting a root as depth 1.
 */
data class GltfLimits(
    val maxJsonBytes: Int = DEFAULT_MAX_JSON_BYTES,
    val maxAccessorCount: Int = DEFAULT_MAX_ACCESSOR_COUNT,
    val maxDecodedBytes: Long = DEFAULT_MAX_DECODED_BYTES,
    val maxNodeDepth: Int = DEFAULT_MAX_NODE_DEPTH,
) {
    init {
        require(maxJsonBytes > 0) { "maxJsonBytes must be positive, was $maxJsonBytes." }
        require(maxAccessorCount > 0) { "maxAccessorCount must be positive, was $maxAccessorCount." }
        require(maxDecodedBytes > 0) { "maxDecodedBytes must be positive, was $maxDecodedBytes." }
        require(maxNodeDepth > 0) { "maxNodeDepth must be positive, was $maxNodeDepth." }
    }

    /** The limits a parse uses when the caller passes none. */
    companion object {
        /** 64 MiB of JSON, 64 Mi elements per accessor, 1 GiB decoded, nodes 256 deep. */
        val Default: GltfLimits = GltfLimits()

        private const val DEFAULT_MAX_JSON_BYTES = 64 shl 20
        private const val DEFAULT_MAX_ACCESSOR_COUNT = 1 shl 26
        private const val DEFAULT_MAX_DECODED_BYTES = 1L shl 30
        private const val DEFAULT_MAX_NODE_DEPTH = 256
    }
}

/** Throws [GltfFormatException] for [problem] at [location]. */
internal fun malformed(location: String, problem: String): Nothing = throw GltfFormatException(location, problem)

/** One parse's [limits] and the decoded bytes it has allocated so far. */
internal class GltfReadContext(val limits: GltfLimits) {
    private var decodedBytes = 0L

    /** Records [bytes] about to be allocated for [location], failing first if that passes [GltfLimits.maxDecodedBytes]. */
    fun charge(location: String, bytes: Long) {
        if (bytes > limits.maxDecodedBytes - decodedBytes) {
            malformed(
                location,
                "needs $bytes more bytes, past the ${limits.maxDecodedBytes}-byte decode limit " +
                    "($decodedBytes already decoded)",
            )
        }
        decodedBytes += bytes
    }

    /** An array length for [count] elements of [width] values each, charged at [bytesPerValue] each. */
    fun arrayLength(location: String, count: Int, width: Int, bytesPerValue: Int = Int.SIZE_BYTES): Int {
        val length = count.toLong() * width
        if (length > Int.MAX_VALUE) malformed(location, "needs $length values, more than one array can hold")
        charge(location, length * bytesPerValue)
        return length.toInt()
    }
}

/**
 * Runs [block], turning any error a malformed document could still raise past the explicit checks into
 * [GltfFormatException], so a parse fails with one documented type.
 */
// Deliberately broad: this is the net under the explicit checks, and it rethrows anything it does not type.
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> gltfFailuresTyped(block: () -> T): T = try {
    block()
} catch (failure: RuntimeException) {
    throw when (failure) {
        is GltfFormatException -> failure
        is IllegalArgumentException,
        is IllegalStateException,
        is IndexOutOfBoundsException,
        is NoSuchElementException,
        is ArithmeticException,
        -> GltfFormatException("document", "could not be read: ${failure.message}", failure)

        else -> failure
    }
}
