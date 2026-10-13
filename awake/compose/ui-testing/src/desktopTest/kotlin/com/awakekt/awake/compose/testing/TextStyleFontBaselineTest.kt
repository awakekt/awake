/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test

/**
 * A bitmap-font label beside the default Roboto one, with only `TextStyle.font` telling them
 * apart.
 *
 * Before `Text` read [TextStyle.font], both labels drew in the ambient `LocalFont` atlas and this
 * baseline would have shown two identical Roboto labels.
 */
class TextStyleFontBaselineTest {

    @Test
    fun aBitmapStyleFontRendersBesideTheDefaultFont() {
        composeFrame(240, 48) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedByHorizontal(16.dp)) {
                Text("Bitmap", style = TextStyle(size = 20.sp, font = UiFonts.bitmap()))
                Text("Roboto", style = TextStyle(size = 20.sp))
            }
        }.assertMatchesBaseline("text-style-font", 240, 48)
    }
}
