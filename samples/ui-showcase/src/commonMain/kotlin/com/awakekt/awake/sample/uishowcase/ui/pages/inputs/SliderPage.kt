/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.inputs

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnFieldLabel
import com.awakekt.awake.ui.shadcn.components.ShadcnSlider

private class SliderValue(initial: Float) {
    var value: Float = initial
}

internal val SliderPage = ShowcasePage(
    id = "slider",
    title = "Slider",
    category = ShowcaseCategory.Inputs,
    description = "An input where the user selects a value from within a given range.",
    usageCode = """ShadcnSlider(value = 50f, min = 0f, max = 100f, steps = 4, onValueChange = { })""",
    referenceExample = "registry/new-york-v4/examples/slider-demo.tsx",
    previewHeight = 380,
    notes = listOf("Track fill with thumb drag interaction."),
    hero = {
        val value = remember { SliderValue(60f) }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShadcnFieldLabel("Volume (${value.value.toInt()}%)")
            ShadcnSlider(
                value.value,
                min = 0f,
                max = 100f,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { value.value = it },
            )
        }
    },
    states = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("Low" to 10f, "Mid" to 50f, "High" to 95f).forEach { (label, initial) ->
                val value = remember(label) { SliderValue(initial) }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShadcnFieldLabel("$label (${value.value.toInt()}%)")
                    ShadcnSlider(
                        value.value,
                        min = 0f,
                        max = 100f,
                        modifier = Modifier.fillMaxWidth(),
                        onValueChange = { value.value = it },
                    )
                }
            }
            val stepped = remember { SliderValue(50f) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShadcnFieldLabel("Stepped (${stepped.value.toInt()}%)")
                ShadcnSlider(
                    stepped.value,
                    min = 0f,
                    max = 100f,
                    steps = 4,
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { stepped.value = it },
                )
            }
            ShadcnFieldLabel("Disabled")
            ShadcnSlider(50f, min = 0f, max = 100f, modifier = Modifier.fillMaxWidth(), enabled = false)
        }
    },
)
