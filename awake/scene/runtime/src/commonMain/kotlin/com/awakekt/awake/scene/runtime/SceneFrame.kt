/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.input.PointerCursor

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
