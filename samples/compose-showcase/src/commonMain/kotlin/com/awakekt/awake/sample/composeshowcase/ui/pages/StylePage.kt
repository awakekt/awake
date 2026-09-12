/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.sample.composeshowcase.ui.pages

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.style.MutableStyleState
import com.awakekt.awake.compose.foundation.style.Style
import com.awakekt.awake.compose.foundation.style.StyleState
import com.awakekt.awake.compose.foundation.style.checked
import com.awakekt.awake.compose.foundation.style.disabled
import com.awakekt.awake.compose.foundation.style.focused
import com.awakekt.awake.compose.foundation.style.hovered
import com.awakekt.awake.compose.foundation.style.pressed
import com.awakekt.awake.compose.foundation.style.selected
import com.awakekt.awake.compose.foundation.style.styleable
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.sample.composeshowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.composeshowcase.ui.ShowcasePage

private val BaseStyle = Style {
    background(Color.fromHex(0x334155))
    border(1.dp, Color.fromHex(0x64748B))
    cornerRadius(8.dp)
    contentPadding(horizontal = 14.dp, vertical = 8.dp)
    hovered { background(Color.fromHex(0x475569)) }
    pressed { background(Color.fromHex(0x1E293B)) }
    focused { border(2.dp, Color.fromHex(0x38BDF8)) }
    disabled { alpha(0.4f) }
    selected { background(Color.fromHex(0x2563EB)) }
    checked { border(2.dp, Color.fromHex(0x4ADE80)) }
}

internal val StylePage = ShowcasePage(
    id = "styles",
    title = "Styles",
    category = ShowcaseCategory.Styles,
    description = "State-aware foundation styles resolved into ordinary modifiers before layout.",
    notes = listOf(
        "Styles can branch on hovered, pressed, focused, disabled, selected, and checked state.",
        "Later rules win when they write the same property.",
        "contentPadding is inside the painted surface; external padding sits outside it.",
    ),
    demo = {
        val selectedState = remember { MutableStyleState(isSelected = true) }
        val checkedState = remember { MutableStyleState(isChecked = true) }
        val disabledState = remember { MutableStyleState(isEnabled = false) }
        val toggled = remember { MutableStyleState() }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DemoSectionLabel("StyleState matrix")
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(10.dp)) {
                styledLabel("Default", StyleState.Default)
                styledLabel("Selected", selectedState)
                styledLabel("Checked", checkedState)
                styledLabel("Disabled", disabledState)
            }

            DemoSectionLabel("Interactive selected state")
            Row(horizontalArrangement = Arrangement.spacedByHorizontal(10.dp)) {
                Box(
                    modifier = Modifier
                        .styleable(toggled, BaseStyle)
                        .clickable {
                            toggled.isSelected = !toggled.isSelected
                            toggled.isChecked = !toggled.isChecked
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (toggled.isSelected) "Selected + checked" else "Click to select",
                        style = TextStyle.Default.copy(color = Color.White),
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Color.fromHex(0xF1F5F9))
                        .padding(10.dp),
                ) {
                    Text(
                        "styleable(state, style)",
                        style = TextStyle.Default.copy(color = Color.fromHex(0x334155)),
                    )
                }
            }

            DemoSectionLabel("Content padding versus external padding")
            Box(Modifier.background(Color.fromHex(0xE2E8F0)).padding(12.dp)) {
                Box(
                    modifier = Modifier.styleable(
                        StyleState.Default,
                        Style {
                            background(Color.fromHex(0x0F766E))
                            cornerRadius(8.dp)
                            externalPadding(horizontal = 16.dp, vertical = 4.dp)
                            contentPadding(horizontal = 18.dp, vertical = 10.dp)
                        },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("outer gap + inner inset", style = TextStyle.Default.copy(color = Color.White))
                }
            }
        }
    },
)

context(composer: com.awakekt.awake.compose.runtime.Composer)
private fun styledLabel(label: String, state: StyleState) {
    Box(
        modifier = Modifier.width(118.dp).styleable(state, BaseStyle),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle.Default.copy(color = Color.White))
    }
}
