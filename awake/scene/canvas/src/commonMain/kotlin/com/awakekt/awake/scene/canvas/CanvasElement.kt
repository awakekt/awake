/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ui.canvas.consumeLink as capabilityConsumeLink

/** Forwards to `awake:ui:canvas`'s [consumeLink][com.awakekt.awake.ui.canvas.consumeLink]. */
fun CanvasElement.consumeLink(): String? = capabilityConsumeLink()
