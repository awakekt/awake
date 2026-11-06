/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

// One thread: every call is on the frame thread, so a bound guard never throws.
internal actual fun currentThreadIdentity(): Any = Unit

internal actual fun frameThreadChecksByDefault(): Boolean = false
