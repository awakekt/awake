/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.gltf

import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.glb
import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.putInt
import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.skinnedJson
import com.awakekt.awake.asset.gltf.GltfMalformedFixtures.triangleJson
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Seeded mutations of valid files. Each mutant must either parse or fail with [GltfFormatException]:
 * any other exception, an out-of-memory error or a stack overflow fails the test, and so does the whole
 * run taking longer than its time bound.
 */
class GltfFuzzTest {
    private val timeBound = 30.seconds

    @Test
    fun mutatedGlbBytesParseOrFailTyped() {
        val random = Random(SEED)
        val original = glb()
        val start = TimeSource.Monotonic.markNow()
        repeat(ITERATIONS) { iteration ->
            val mutant = mutateBytes(original, random)
            parsesOrFailsTyped("GLB mutant $iteration") { GltfParser.parseScene(mutant) }
        }
        assertTrue(start.elapsedNow() < timeBound, "$ITERATIONS GLB mutants took ${start.elapsedNow()}")
    }

    @Test
    fun mutatedJsonNumbersParseOrFailTyped() {
        val random = Random(SEED + 1)
        val skinned = skinnedJson()
        val triangle = triangleJson()
        val start = TimeSource.Monotonic.markNow()
        repeat(ITERATIONS) { iteration ->
            val skinnedMutant = mutateNumbers(skinned, random)
            parsesOrFailsTyped("skinned JSON mutant $iteration") { GltfParser.parseSkinned(skinnedMutant) }
            val triangleMutant = mutateNumbers(triangle, random)
            parsesOrFailsTyped("GLB JSON mutant $iteration") { GltfParser.parseScene(glb(triangleMutant)) }
        }
        assertTrue(start.elapsedNow() < timeBound, "${ITERATIONS * 2} JSON mutants took ${start.elapsedNow()}")
    }

    private fun parsesOrFailsTyped(label: String, parse: () -> Unit) {
        try {
            parse()
        } catch (expected: GltfFormatException) {
            // The documented failure.
        } catch (other: Throwable) {
            fail("$label threw ${other::class.simpleName}: ${other.message}", other)
        }
    }

    /** One to four byte-level edits: a random byte, an interesting 32-bit value, or a truncation. */
    private fun mutateBytes(original: ByteArray, random: Random): ByteArray {
        var bytes = original.copyOf()
        repeat(random.nextInt(1, 5)) {
            when (random.nextInt(3)) {
                0 -> bytes[random.nextInt(bytes.size)] = random.nextInt(256).toByte()
                1 -> if (bytes.size >= 4) putInt(bytes, random.nextInt(bytes.size / 4) * 4, INTERESTING_INTS.random(random))
                else -> bytes = bytes.copyOf(random.nextInt(bytes.size + 1))
            }
        }
        return bytes
    }

    /** One to three JSON numbers replaced by values at the edges of what their fields accept. */
    private fun mutateNumbers(json: String, random: Random): String {
        val numbers = NUMBER.findAll(json).toList()
        val chosen = List(random.nextInt(1, 4)) { numbers.random(random) }.distinct().sortedByDescending { it.range.first }
        return chosen.fold(json) { text, match -> text.replaceRange(match.range, INTERESTING_NUMBERS.random(random)) }
    }

    private companion object {
        const val SEED = 904
        const val ITERATIONS = 2000
        val NUMBER = Regex("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")
        val INTERESTING_INTS = listOf(0, 1, 4, 44, 0x10000, 0x7FFFFFFF, Int.MIN_VALUE, -1, -8, 0x4E4F534A, 0x004E4942)
        val INTERESTING_NUMBERS = listOf(
            "0", "1", "2", "3", "-1", "4", "7", "16", "252", "253", "255", "65535", "268435456",
            "2147483647", "-2147483648", "4294967295", "1e20", "0.5", "5120", "5125",
        )
    }
}
