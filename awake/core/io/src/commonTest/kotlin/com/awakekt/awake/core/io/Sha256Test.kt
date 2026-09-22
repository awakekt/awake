/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class Sha256Test {
    @Test
    fun hashesKnownVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.digestHex(""),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.digestHex("abc"),
        )
    }

    @Test
    fun hashesReadSessionWithoutChangingTheDigest() = runTest {
        val bytes = ByteArray(DEFAULT_CHUNK_SIZE * 2 + 19) { (it % 251).toByte() }
        val fileSystem = InMemoryFileSystem()
        val path = FilePath.of("large.bin")
        fileSystem.write(path, bytes).getOrThrow()

        val sessionDigest = Sha256.digestHex(fileSystem.openRead(path).getOrThrow(), chunkSize = 17)

        assertEquals(Sha256.digestHex(bytes), sessionDigest)
    }

    @Test
    fun incrementalStreamMatchesWholeInputDigest() {
        val stream = Sha256.Stream()
        stream.update("first".encodeToByteArray())
        stream.update("-second".encodeToByteArray())

        assertEquals(Sha256.digestHex("first-second"), stream.finishHex())
    }
}
