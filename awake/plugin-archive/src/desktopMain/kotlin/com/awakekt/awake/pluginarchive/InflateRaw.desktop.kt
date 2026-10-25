/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import java.util.zip.Inflater

internal actual suspend fun inflateRaw(input: ByteArray, expectedSize: Int): ByteArray {
    val inflater = Inflater(true)
    try {
        // With `nowrap`, Inflater wants one byte past the stream (see its class doc).
        inflater.setInput(input.copyOf(input.size + 1))
        val output = ByteArray(expectedSize)
        var produced = 0
        while (produced < expectedSize) {
            val count = inflater.inflate(output, produced, expectedSize - produced)
            if (count == 0 && inflater.isStalled()) break
            produced += count
        }
        check(produced == expectedSize) { "inflates to $produced bytes, not the declared $expectedSize" }
        if (!inflater.finished()) {
            check(inflater.inflate(ByteArray(1)) == 0 && inflater.finished()) { "inflates past its declared $expectedSize bytes" }
        }
        return output
    } finally {
        inflater.end()
    }
}

/** No more output can come: the stream ended, ran out of input, or wants a preset dictionary. */
private fun Inflater.isStalled(): Boolean = finished() || needsInput() || needsDictionary()
