/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

import java.util.Locale

/**
 * [value] as the generated source writes a float: six decimals and a decimal point.
 *
 * Formatted with [Locale.ROOT], not the machine's locale. A German or French default writes
 * `0,927735`, which inside `floatArrayOf(...)` is two elements, so the same command would write
 * different source on a machine with that locale.
 */
internal fun emLiteral(value: Float): String = "%.6f".format(Locale.ROOT, value)
