/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.inputs

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnFieldLabel
import com.awakekt.awake.ui.shadcn.components.ShadcnRangeSlider

private class RangeState {
    var start = 25f
    var end = 75f
}

internal val RangeSliderPage = ShowcasePage(
    id = "range-slider",
    title = "Range Slider",
    category = ShowcaseCategory.Inputs,
    description = "A slider with two thumbs on one track that selects a bounded range.",
    usageCode = """ShadcnRangeSlider(state.start, state.end, onStartChange = ..., onEndChange = ...)""",
    previewHeight = 360,
    notes = listOf("One shared track; the primary fill spans between the two thumbs."),
    hero = {
        val state = remember { RangeState() }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShadcnFieldLabel("Range ${state.start.toInt()} - ${state.end.toInt()}")
            ShadcnRangeSlider(
                start = state.start,
                end = state.end,
                min = 0f,
                max = 100f,
                modifier = Modifier.fillMaxWidth(),
                onStartChange = { state.start = it },
                onEndChange = { state.end = it },
            )
        }
    },
)
