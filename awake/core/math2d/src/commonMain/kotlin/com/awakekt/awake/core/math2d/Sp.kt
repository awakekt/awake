/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math2d

import kotlin.jvm.JvmInline

/**
 * Scale-independent authored text size. Pixel conversion belongs to the UI runtime.
 *
 * @property value Authored text size magnitude.
 */
@JvmInline
value class Sp(val value: Float)

/** Converts this [Float] value to scale-independent text pixels ([Sp]). */
val Float.sp: Sp get() = Sp(this)

/** Converts this [Int] value to scale-independent text pixels ([Sp]). */
val Int.sp: Sp get() = Sp(toFloat())
