/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

/**
 * Which showcase is running, and which one the UI has asked for next.
 *
 * A request rather than a direct call, because activating a showcase closes the current scene and
 * instantiates another one: doing that from a click handler would tear down the world in the
 * middle of composing a frame that is reading it. The driver system picks the request up at the
 * top of the next update, which is a point where nothing is mid-traversal.
 */
internal class ShowcaseSelection(initialId: String) {
    var current: String = initialId
        private set

    private var pending: String? = null

    /** Asks for [id] next frame. A request for what is already running is dropped. */
    fun request(id: String) {
        if (id != current) pending = id
    }

    /** The requested showcase, taken exactly once, or null when nothing changed. */
    fun consumeRequest(): String? {
        val next = pending ?: return null
        pending = null
        current = next
        return next
    }
}
