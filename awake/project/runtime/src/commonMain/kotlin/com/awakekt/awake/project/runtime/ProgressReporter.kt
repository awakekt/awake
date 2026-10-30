/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

/**
 * Passes progress, a share from 0 to 1, on to [sink] so that it never decreases and never repeats: a
 * value that is not above the last one reported is dropped, and one outside 0..1 is clamped.
 */
internal class ProgressReporter(private val sink: (Float) -> Unit) {
    private var last = -1f

    fun report(progress: Float) {
        val value = progress.coerceIn(0f, 1f)
        if (value <= last) return
        last = value
        sink(value)
    }
}
