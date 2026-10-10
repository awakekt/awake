/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import org.w3c.dom.HTMLCanvasElement

/** [this] canvas's box on the page, in CSS pixels. */
internal fun HTMLCanvasElement.box(): CanvasBox =
    getBoundingClientRect().let { CanvasBox(it.left, it.top, it.width, it.height) }
