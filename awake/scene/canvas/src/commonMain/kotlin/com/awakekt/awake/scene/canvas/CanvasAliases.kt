/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

// awake:ui:canvas owns these types as the scene-free canvas capability. These deprecated aliases
// keep code written against awake:scene:canvas compiling until it imports awake:ui:canvas.

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasAlign"))
typealias CanvasAlign = com.awakekt.awake.ui.canvas.CanvasAlign

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasAnchor"))
typealias CanvasAnchor = com.awakekt.awake.ui.canvas.CanvasAnchor

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasBinding"))
typealias CanvasBinding = com.awakekt.awake.ui.canvas.CanvasBinding

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasDirection"))
typealias CanvasDirection = com.awakekt.awake.ui.canvas.CanvasDirection

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasElement"))
typealias CanvasElement = com.awakekt.awake.ui.canvas.CanvasElement

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasElementKind"))
typealias CanvasElementKind = com.awakekt.awake.ui.canvas.CanvasElementKind

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasFillImageMode"))
typealias CanvasFillImageMode = com.awakekt.awake.ui.canvas.CanvasFillImageMode

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasGradient"))
typealias CanvasGradient = com.awakekt.awake.ui.canvas.CanvasGradient

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasImage"))
typealias CanvasImage = com.awakekt.awake.ui.canvas.CanvasImage

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasJustify"))
typealias CanvasJustify = com.awakekt.awake.ui.canvas.CanvasJustify

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasLayout"))
typealias CanvasLayout = com.awakekt.awake.ui.canvas.CanvasLayout

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasShadow"))
typealias CanvasShadow = com.awakekt.awake.ui.canvas.CanvasShadow

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasStateStyle"))
typealias CanvasStateStyle = com.awakekt.awake.ui.canvas.CanvasStateStyle

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasStyle"))
typealias CanvasStyle = com.awakekt.awake.ui.canvas.CanvasStyle

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasTextOutline"))
typealias CanvasTextOutline = com.awakekt.awake.ui.canvas.CanvasTextOutline

@Deprecated("Moved to awake:ui:canvas.", ReplaceWith("com.awakekt.awake.ui.canvas.CanvasTextShadow"))
typealias CanvasTextShadow = com.awakekt.awake.ui.canvas.CanvasTextShadow
