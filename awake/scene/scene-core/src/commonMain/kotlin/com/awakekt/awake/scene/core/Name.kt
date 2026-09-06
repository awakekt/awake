/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core

import com.awakekt.awake.ecs.Poolable

data class Name(
    var value: String = "",
) : Poolable {
    override fun reset() {
        value = ""
    }
}
