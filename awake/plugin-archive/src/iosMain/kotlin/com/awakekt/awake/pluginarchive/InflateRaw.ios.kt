/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.pluginarchive

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.posix.memset
import platform.zlib.ZLIB_VERSION
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun inflateRaw(input: ByteArray, expectedSize: Int): ByteArray {
    check(input.isNotEmpty()) { "has no deflate data" }
    // One spare byte: if zlib fills it, the stream inflates past its declared size.
    val output = ByteArray(expectedSize + 1)
    val produced = memScoped {
        val stream = alloc<z_stream>()
        memset(stream.ptr, 0, sizeOf<z_stream>().convert())
        // Negative window bits: raw DEFLATE, no zlib header, as zip stores it.
        check(inflateInit2_(stream.ptr, -15, ZLIB_VERSION, sizeOf<z_stream>().convert()) == Z_OK) { "zlib did not initialise" }
        try {
            input.usePinned { source ->
                output.usePinned { target ->
                    stream.next_in = source.addressOf(0).reinterpret()
                    stream.avail_in = input.size.convert()
                    stream.next_out = target.addressOf(0).reinterpret()
                    stream.avail_out = output.size.convert()
                    check(inflate(stream.ptr, Z_FINISH) == Z_STREAM_END) { "is corrupt, truncated or larger than declared" }
                }
            }
            output.size - stream.avail_out.toInt()
        } finally {
            inflateEnd(stream.ptr)
        }
    }
    check(produced == expectedSize) { "inflates to $produced bytes, not the declared $expectedSize" }
    return output.copyOf(expectedSize)
}
