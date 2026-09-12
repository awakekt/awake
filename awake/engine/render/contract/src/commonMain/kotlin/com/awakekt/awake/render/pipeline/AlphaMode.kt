/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

/** Fragment coverage behavior used by depth and shadow caster pipelines. */
enum class AlphaMode {
    /** Every covered fragment writes depth. */
    Opaque,

    /** The fragment shader discards coverage below its cutoff before writing depth. */
    Masked,
}
