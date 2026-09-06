/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

/**
 * The pointer shape a frame asks the platform host to display.
 *
 * A request, not a call: whatever produced the frame records what it wants, and the desktop entry
 * point applies it. Nothing here reaches a windowing API.
 *
 * Lives beside [Input] rather than in a UI module because both UI engines need to express it and
 * the render backend needs to read it -- and a type that three modules share cannot sit inside the
 * one being deleted. It was `ui.context.UiCursor`, which made `:awake:backend:vulkan` depend on
 * `ui-core` for a five-value enum with no dependencies of its own.
 */
enum class PointerCursor {
    Default,
    ResizeHorizontal,
    ResizeVertical,
    Pointer,
    Text,
}
