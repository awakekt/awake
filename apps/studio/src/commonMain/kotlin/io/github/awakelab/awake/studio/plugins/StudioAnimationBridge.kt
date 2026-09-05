/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.plugins

import io.github.awakelab.awake.core.animation.AnimationPlayer

/**
 * Bridges the active runtime animation player to editor components like the Timeline dock tab.
 */
object StudioAnimationBridge {
    var activePlayer: AnimationPlayer? = null
}
