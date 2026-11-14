/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

/**
 * A failure because content declares a format version newer than this build reads, as a scene
 * exported by a newer editor or a content index cut by a newer engine does. An app tells the player
 * to update rather than offering a retry that fails the same way; [isTooNew] asks a whole cause chain.
 *
 * The engine's own version exceptions implement it, such as the scene schema version exception, the
 * content index and bundle format exception and the project manifest one, so an app checks one type
 * instead of matching exception text.
 */
interface ContentTooNew {
    /** The version the content declares. */
    val foundVersion: Int

    /** The newest version of that format this build reads. */
    val supportedVersion: Int
}

/**
 * Whether [failure], or any cause under it, is [ContentTooNew]: the content is newer than this build,
 * so the fix is an update, not a retry. A cause chain that loops back on itself is read once.
 */
fun isTooNew(failure: Throwable): Boolean {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = failure
    while (current != null && seen.add(current)) {
        if (current is ContentTooNew) return true
        current = current.cause
    }
    return false
}
