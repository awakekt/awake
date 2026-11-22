/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Desktop JVM implementation of [ioDispatcher], backed by [Dispatchers.IO]. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
