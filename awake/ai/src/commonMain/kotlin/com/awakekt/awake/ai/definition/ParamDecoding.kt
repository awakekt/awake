/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonObject

/**
 * Reads [params] with [serializer], or fails with a message for an author. An unknown or missing
 * parameter is named here rather than by the serialization library, whose message talks about its
 * own configuration.
 */
internal fun decodeParams(serializer: KSerializer<*>, params: JsonObject, owner: Any): Result<Any> {
    val descriptor = serializer.descriptor
    val names = (0 until descriptor.elementsCount).map(descriptor::getElementName)
    val checksFields = descriptor.kind == StructureKind.CLASS || descriptor.kind == StructureKind.OBJECT
    val unknown = params.keys.firstOrNull { it !in names }
    val missing = names.indices.firstOrNull { !descriptor.isElementOptional(it) && names[it] !in params }
    val problem = when {
        !checksFields -> null
        unknown != null -> {
            val takes = if (names.isEmpty()) "it takes none" else "it takes ${names.joinToString()}"
            "unknown parameter '$unknown'${didYouMean(unknown, names)}; $takes."
        }
        missing != null -> "missing parameter '${names[missing]}'; $owner needs it."
        else -> null
    }
    if (problem != null) return Result.failure(IllegalArgumentException(problem))
    return try {
        Result.success(AiJson.data.decodeFromJsonElement(serializer, params)!!)
    } catch (e: IllegalArgumentException) {
        // SerializationException is an IllegalArgumentException, as is a parameter class's own require.
        Result.failure(IllegalArgumentException(e.message?.lineSequence()?.first() ?: "parameters are not valid.", e))
    }
}

/** " (did you mean 'x'?)" when one of [known] is a few edits from [name], else "". */
internal fun didYouMean(name: String, known: Collection<String>): String {
    val close = known.map { it to editDistance(name, it) }
        .filter { (candidate, distance) -> distance <= maxOf(2, candidate.length / 2) }
        .minByOrNull { it.second }
    return close?.let { " (did you mean '${it.first}'?)" } ?: ""
}

private fun editDistance(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
        }
        previous = current
    }
    return previous[b.length]
}

/** Mixes [salt] into [seed], so neighbouring salts give unrelated seeds. */
internal fun mixSeed(seed: Long, salt: Long): Long {
    var z = seed + (salt + 1) * -0x61c8864680b583ebL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
}
