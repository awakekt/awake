/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement

actual fun writeSceneDocument(fileName: String, json: String): String {
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = "data:application/json;charset=utf-8," + encodeURIComponent(json)
    anchor.download = fileName
    anchor.click()
    return "download: $fileName"
}

@OptIn(ExperimentalWasmJsInterop::class)
// `value` is consumed inside the js("...") string literal, which is opaque to Kotlin's static
// analyser -- the suppress is a false positive specific to the Wasm js() interop mechanism.
@Suppress("UnusedParameter")
private fun encodeURIComponent(value: String): String = js("encodeURIComponent(value)")
