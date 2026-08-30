/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.image

interface Bitmap {
    val width: Int
    val height: Int
    val channel: Int
    val pixels: IntArray
}

class DefaultBitmap(
    override val width: Int,
    override val height: Int,
    override val channel: Int,
    override val pixels: IntArray,
) : Bitmap

expect suspend fun createBitmap(bytes: ByteArray): Bitmap
