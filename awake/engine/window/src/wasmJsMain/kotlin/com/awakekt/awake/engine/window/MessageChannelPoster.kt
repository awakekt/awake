/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.engine.window

/**
 * Posts a task through a `MessageChannel`: an ordinary task, which the browser runs in its turn with
 * other tasks such as a worker's messages, not ahead of them as it does animation-frame callbacks.
 * One task waits at a time, as [PostedFrameLoop] posts.
 */
internal class MessageChannelPoster {
    private var task: (() -> Unit)? = null
    private val send: JsAny = messageChannelSender(::run)

    fun post(next: () -> Unit) {
        task = next
        callSender(send)
    }

    private fun run() {
        val next = task ?: return
        task = null
        next()
    }
}

@JsFun("(run) => { const c = new MessageChannel(); c.port1.onmessage = () => run(); return () => c.port2.postMessage(0); }")
private external fun messageChannelSender(run: () -> Unit): JsAny

@JsFun("(send) => send()")
private external fun callSender(send: JsAny)
