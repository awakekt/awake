/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.texture.CompressedTextureFormat
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * A page that doesn't serve the Basis Universal transcoder still runs (#1261): the rejected script load
 * arrives as a `JsException`, and each texture is left to its source image.
 */
class MissingBasisTranscoderTest {

    @Test
    fun aRejectedTranscoderScriptLeavesTheTextureToItsSourceImage() = runTest {
        val files = AssetSource { path ->
            if (path.value == "textures/rock.ktx2") Result.success(RockKtx2.bytes) else Result.failure(NoSuchElementException(path.value))
        }
        val loader = CookedTextureLoader(files) {
            rejectedScriptLoad().await<JsAny?>()
            error("the script load was rejected")
        }

        assertNull(loader.load("textures/rock.png", setOf(CompressedTextureFormat.Bc7Rgba)))
        assertNull(loader.decode(RockKtx2.bytes))
    }
}

/** What a `<script>` the server answers with a 404 page rejects with: a JS `Error`, not a Kotlin exception. */
private fun rejectedScriptLoad(): Promise<JsAny?> = js("Promise.reject(new Error('the script was not found, or was refused'))")
