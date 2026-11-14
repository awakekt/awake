/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlinx.browser.window
import org.w3c.dom.Storage

/**
 * [CrashStorage] over the page's `localStorage`, so a session a tab kill cut short is still there
 * when the page loads again. Keys are stored as given; [CrashRecorder.keyPrefix] keeps them apart
 * from the app's own.
 *
 * Browser storage can be missing or refuse a write: a sandboxed frame or a privacy setting throws
 * on access, and a full origin throws on write. Every call is guarded: a read then answers null and
 * a write or removal does nothing, so crash recording never stops the app. [available] tells the app
 * whether anything will persist.
 */
class LocalStorageCrashStorage : CrashStorage {
    /** Whether the page's `localStorage` can be reached at all. */
    val available: Boolean get() = storage() != null

    override fun read(key: String): String? = runCatching { storage()?.getItem(key) }.getOrNull()

    override fun write(key: String, value: String) {
        runCatching { storage()?.setItem(key, value) }
    }

    override fun remove(key: String) {
        runCatching { storage()?.removeItem(key) }
    }

    // Asked each time: a frame's storage can be blocked or cleared while the page runs.
    private fun storage(): Storage? = runCatching { window.localStorage }.getOrNull()
}
