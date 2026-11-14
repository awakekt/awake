/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

import com.awakekt.awake.core.graphics2d.UiDrawPrimitive

/**
 * A frame [ComposeHost.skipUnchangedComposition] would have skipped although its output changed,
 * found by [ComposeHost.verifySkippedCompositions]: the content read state the host does not observe.
 *
 * @property primitiveIndex Index of the first draw primitive that differs.
 * @property skipped What the skipped frame would have drawn there, or null past its end.
 * @property composed What composing drew there, or null past its end.
 */
data class StaleSkip(
    val primitiveIndex: Int,
    val skipped: UiDrawPrimitive?,
    val composed: UiDrawPrimitive?,
)

/** The first index at which [a] and [b] differ, or -1 when they are equal. */
internal fun firstDifference(a: List<UiDrawPrimitive>, b: List<UiDrawPrimitive>): Int {
    val shared = minOf(a.size, b.size)
    for (index in 0 until shared) if (a[index] != b[index]) return index
    return if (a.size == b.size) -1 else shared
}
