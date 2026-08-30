/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics

import kotlin.jvm.JvmInline

/** Wraps a backend's native body id (jolt-jni's `Body`/`BodyID` is an `int`, widened to
 * `Long` here so this handle's representation isn't tied to one backend's native width) --
 * a `value class`, not a raw `Long`, since this is new public API surface. */
@JvmInline
value class BodyHandle(val id: Long)
