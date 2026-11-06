/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

internal actual fun currentThreadIdentity(): Any = Thread.currentThread()

// An Android debug app turns the checks on itself: the runtime has no cheap way to tell a debug build.
internal actual fun frameThreadChecksByDefault(): Boolean = false
