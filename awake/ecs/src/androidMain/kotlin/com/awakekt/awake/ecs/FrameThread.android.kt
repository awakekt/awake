/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

internal actual fun currentThreadIdentity(): Any = Thread.currentThread()

// Off until the Android window host turns it on in a debuggable app: without a Context, this can't tell.
internal actual fun frameThreadChecksByDefault(): Boolean = false
