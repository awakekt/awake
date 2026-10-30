/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.pluginarchive

import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.js.Promise

internal actual suspend fun inflateRaw(input: ByteArray, expectedSize: Int): ByteArray {
    val source = Int8Array(input.size)
    for (index in input.indices) source[index] = input[index]
    val inflated = inflateWithDecompressionStream(source, expectedSize).await<Int8Array>()
    return ByteArray(inflated.length) { inflated[it] }
}

// Reads the browser's deflate-raw stream chunk by chunk and stops as soon as it passes `limit`, so a
// small entry claiming a small size cannot inflate a huge buffer before the check.
@Suppress("UNUSED_PARAMETER")
private fun inflateWithDecompressionStream(input: Int8Array, limit: Int): Promise<JsAny?> = js(
    """
    (async () => {
        const reader = new Blob([input]).stream().pipeThrough(new DecompressionStream('deflate-raw')).getReader();
        const out = new Int8Array(limit);
        let size = 0;
        for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            if (size + value.length > limit) {
                await reader.cancel();
                throw new Error('inflates past its declared ' + limit + ' bytes');
            }
            out.set(new Int8Array(value.buffer, value.byteOffset, value.length), size);
            size += value.length;
        }
        if (size !== limit) throw new Error('inflates to ' + size + ' bytes, not the declared ' + limit);
        return out;
    })()
    """,
)
