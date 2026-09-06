/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind

/**
 * Draws a backend texture into this node's bounds.
 *
 * The material is opaque to the UI: a backend hands one out and takes it back, and nothing here
 * knows what it is. That is the same contract `ui-core`'s `textureQuad` had, and it is what lets a
 * camera preview or an orientation gizmo be a render-to-texture pass the UI merely places.
 *
 * `drawTexture` rather than `emit`: a caller cannot position an emitted primitive, because the
 * origin and transform the scope maps against are private to it.
 */
context(_: Composer)
fun TextureQuad(material: Any, modifier: Modifier = Modifier) {
    Spacer(modifier.drawBehind { drawTexture(material) })
}
