/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

internal actual fun hostNanoTime(): Long = System.nanoTime()

internal actual fun hostCurrentTimeMillis(): Long = System.currentTimeMillis()
