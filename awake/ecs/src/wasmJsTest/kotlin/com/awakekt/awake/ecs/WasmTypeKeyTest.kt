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
 * keyed by it paid a string build and a full hash. The type table hashes the simple name instead.
 */
class WasmTypeKeyTest {

    private class Position

    @Test
    fun aTypeKeyHashesTheSimpleName() {
        assertEquals("Position".hashCode(), componentTypeKeyHash(componentTypeKey<Position>()))
        assertEquals("Position".hashCode(), componentTypeKeyHash(componentTypeKeyOf(Position::class)))
        assertEquals("Position".hashCode(), WasmTypeKey(Position::class).hashCode())
    }
}
