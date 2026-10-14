/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

// awake:ui:canvas now owns these types as the scene-free canvas capability; these aliases keep
// code written against awake:scene:canvas compiling. A later PR deprecates them once Core's own
// imports have moved to awake:ui:canvas.

typealias CanvasAlign = com.awakekt.awake.ui.canvas.CanvasAlign
typealias CanvasAnchor = com.awakekt.awake.ui.canvas.CanvasAnchor
typealias CanvasBinding = com.awakekt.awake.ui.canvas.CanvasBinding
typealias CanvasDirection = com.awakekt.awake.ui.canvas.CanvasDirection
typealias CanvasElement = com.awakekt.awake.ui.canvas.CanvasElement
typealias CanvasElementKind = com.awakekt.awake.ui.canvas.CanvasElementKind
typealias CanvasFillImageMode = com.awakekt.awake.ui.canvas.CanvasFillImageMode
typealias CanvasGradient = com.awakekt.awake.ui.canvas.CanvasGradient
typealias CanvasImage = com.awakekt.awake.ui.canvas.CanvasImage
typealias CanvasJustify = com.awakekt.awake.ui.canvas.CanvasJustify
typealias CanvasLayout = com.awakekt.awake.ui.canvas.CanvasLayout
typealias CanvasShadow = com.awakekt.awake.ui.canvas.CanvasShadow
typealias CanvasStateStyle = com.awakekt.awake.ui.canvas.CanvasStateStyle
typealias CanvasStyle = com.awakekt.awake.ui.canvas.CanvasStyle
typealias CanvasTextOutline = com.awakekt.awake.ui.canvas.CanvasTextOutline
typealias CanvasTextShadow = com.awakekt.awake.ui.canvas.CanvasTextShadow
