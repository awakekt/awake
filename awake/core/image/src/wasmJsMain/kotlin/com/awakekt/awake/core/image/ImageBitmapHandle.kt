/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.image

import kotlin.js.ExperimentalWasmJsInterop

/** A browser `ImageBitmap`, the decoded image `createImageBitmap` resolves to. */
internal external interface ImageBitmapHandle : JsAny {
    val width: Int
    val height: Int

    fun close()
}
