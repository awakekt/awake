/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.heightIn
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.text.rememberTextFieldState
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnFieldLabel
import io.github.awakelab.awake.ui.shadcn.components.ShadcnInput
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.shadcnTextarea

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
    },
)
