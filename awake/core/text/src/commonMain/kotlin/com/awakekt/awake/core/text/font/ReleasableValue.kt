/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.concurrent.Volatile

/**
 * A value built on first read and kept until [release], after which the next read builds it again: a
 * `lazy` that can let its value go. Two threads reading at once may each build it; [build] must give
 * equal values, as a font's atlas pixels are.
 */
internal class ReleasableValue<T : Any>(private val build: () -> T) {
    @Volatile
    private var value: T? = null

    fun get(): T = value ?: build().also { value = it }

    fun release() {
        value = null
    }
}
