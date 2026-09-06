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
import com.awakekt.awake.compose.foundation.layout.heightIn
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.text.rememberTextFieldState
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnFieldLabel
import com.awakekt.awake.ui.shadcn.components.ShadcnInput
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextarea
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

internal val TextFieldPage = ShowcasePage(
    id = "text-input",
    title = "Text Field",
    category = ShowcaseCategory.Inputs,
    description = "Single-line keyboard-driven text input control with label and focus ring.",
    usageCode = """
        val name = rememberTextFieldState()
        val email = rememberTextFieldState()
        val bio = rememberTextFieldState()

        shadcnMuted("Single-line and multi-line keyboard-driven text input controls with focus ring bounds.")
        Spacer(Modifier.height(8.dp))
        ShadcnCard(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp), contentPadding = 0.dp) {
            Column(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShadcnText("Text Input & Area Interactive Preview")
                ShadcnFieldLabel("Full Name")
                ShadcnInput(name)
                ShadcnFieldLabel("Email Address")
                ShadcnInput(email)
                ShadcnFieldLabel("Biography")
                shadcnTextarea(bio)
            }
        }
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/input-demo.tsx",
    previewHeight = 520,
    notes = listOf("Supports live keyboard typing, arrow navigation, and clear actions."),
    hero = {
        val name = rememberTextFieldState()
        val email = rememberTextFieldState()
        val bio = rememberTextFieldState()
        shadcnMuted("Single-line and multi-line keyboard-driven text input controls with focus ring bounds.")
        Spacer(Modifier.height(8.dp))
        ShadcnCard(
            modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
            contentPadding = 0.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShadcnText("Text Input & Area Interactive Preview")
                ShadcnFieldLabel("Full Name")
                ShadcnInput(name)
                ShadcnFieldLabel("Email Address")
                ShadcnInput(email)
                ShadcnFieldLabel("Biography")
                ShadcnTextarea(bio)
            }
        }
    },
)
