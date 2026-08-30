/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.core.components

import io.github.awakelab.awake.ecs.Poolable

data class Name(
    var value: String = "",
) : Poolable {
    override fun reset() {
        value = ""
    }
}
