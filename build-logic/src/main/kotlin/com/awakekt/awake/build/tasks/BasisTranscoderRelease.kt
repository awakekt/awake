/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import java.security.MessageDigest

/**
 * The Basis Universal transcoder Core ships, pinned by SHA-256: upstream's prebuilt release v2_50
 * (`webgl/transcoder/build/` at tag `v2_50`, commit `9bebe16726b3a61c8c213eeee3b7cffb462ef34e`), byte
 * for byte, as `NOTICE.md` and the provenance registry record.
 *
 * One list for both users of the pin: `:awake:asset:texture-ktx-loader`'s `verifyBasisTranscoder` checks
 * its vendored copy against it, and the `basis-transcoder` plugin checks the copy it puts beside a web
 * app. To update, change the files, the tag and commit in `NOTICE.md` and the provenance registry, and
 * these digests together.
 */
object BasisTranscoderRelease {
    const val TAG = "v2_50"

    /** File name under `basis/` to its SHA-256, in lowercase hex. */
    val digests: Map<String, String> = mapOf(
        "basis_transcoder.js" to "720dd9bd09c7cada6d87f1b7b70cec713df04da88cd641ac3212559353834dc8",
        "basis_transcoder.wasm" to "a0f65d4a30ecb3269d01ead7d0a3477d2b0208146d083625a90623f473f6c139",
    )

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
