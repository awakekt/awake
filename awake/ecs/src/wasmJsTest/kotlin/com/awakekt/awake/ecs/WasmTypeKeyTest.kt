/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A `KClass` on Kotlin/Wasm hashes a qualified name it builds anew on every call, so a type lookup
 * keyed by it paid a string build and a full hash. The key hashes the simple name instead.
 */
class WasmTypeKeyTest {

    private class Position

    @Test
    fun aTypeKeyHashesTheSimpleName() {
        assertEquals("Position".hashCode(), componentTypeKey<Position>().hashCode())
        assertEquals("Position".hashCode(), componentTypeKeyOf(Position::class).hashCode())
    }
}
