/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.compose.ui.platform.InputOwnership
import io.github.awakelab.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.core.input.PointerCursor

/**
 * A finished UI frame -- the fields `render()`'s remaining steps (staging, focus sync, cursor)
 * need, shaped like `FrameOutput` since every scene's UI is compose content now.
 */
internal data class SceneFrame(
    val primitives: List<UiDrawPrimitive>,
    val graphicsLayers: List<GraphicsLayerFrame>,
    val semantics: List<SemanticsNode>,
    val ownership: InputOwnership,
    val cursor: PointerCursor,
    val requestKeyboard: Boolean,
)
