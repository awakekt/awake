/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlinx.serialization.Serializable

/** When a View element draws its camera's picture again. See [CanvasView.refresh]. */
@Serializable
enum class CanvasViewRefresh {
    /** Every frame the View is shown: a picture of a world that moves. */
    Always,

    /**
     * Once when first shown, again when its box changes size, and on the frame after the host asks
     * (`invalidateCanvasViews`); between those the View keeps its last picture and draws nothing. For
     * a picture whose subject changes rarely, such as a HUD portrait.
     */
    OnRequest,
}
