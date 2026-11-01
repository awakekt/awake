/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Basis Universal transcoder in `src/wasmJsMain/resources/basis/` is upstream's own prebuilt
 * release, byte for byte: `webgl/transcoder/build/` at tag v2_50
 * (`9bebe16726b3a61c8c213eeee3b7cffb462ef34e`), as `NOTICE.md` and `source-provenance.json` record.
 *
 * To update it, copy the new release's two files unchanged, then update the tag, the commit and these
 * digests here, in the provenance registry and in `NOTICE.md`.
 */
class VendoredBasisTranscoderTest {

    @Test
    fun theVendoredFilesAreTheRecordedRelease() {
        for ((name, digest) in RELEASE) {
            assertEquals(digest, sha256(File(DIRECTORY, name)), "$name is not the recorded v2_50 file")
        }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private companion object {
        // Tests run from the module's directory.
        const val DIRECTORY = "src/wasmJsMain/resources/basis"
        val RELEASE = mapOf(
            "basis_transcoder.js" to "720dd9bd09c7cada6d87f1b7b70cec713df04da88cd641ac3212559353834dc8",
            "basis_transcoder.wasm" to "a0f65d4a30ecb3269d01ead7d0a3477d2b0208146d083625a90623f473f6c139",
        )
    }
}
