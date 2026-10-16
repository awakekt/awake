/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.pipeline

import io.ygdrasil.webgpu.BindGroup
import io.ygdrasil.webgpu.RenderPassEncoder
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A bind group is set on the browser's pass as `setBindGroup(index, group)`: wgpu4k's own call also
 * passes a new offsets array, which Kotlin/Wasm keeps in a WeakMap, one entry per call.
 */
class BindGroupCallTest {
    @Test
    fun aBindGroupIsSetWithoutAnOffsetsArray() {
        val pass = recordingPass()
        val group = jsObject()

        RenderPassEncoder(pass.unsafeCast()).bindGroup(2u, BindGroup(group.unsafeCast()))

        assertEquals("2:true:2", calls(pass, group))
    }
}

@JsFun("() => { const calls = []; return { calls, setBindGroup: (...args) => calls.push(args) }; }")
private external fun recordingPass(): JsAny

@JsFun("() => ({})")
private external fun jsObject(): JsAny

/** Each call as `index:group matched:argument count`. */
@JsFun("(pass, group) => pass.calls.map(a => a[0] + ':' + (a[1] === group) + ':' + a.length).join(',')")
private external fun calls(pass: JsAny, group: JsAny): String
