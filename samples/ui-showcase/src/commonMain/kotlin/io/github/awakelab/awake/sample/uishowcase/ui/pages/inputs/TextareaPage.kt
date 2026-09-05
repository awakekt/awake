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
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextarea
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

internal val TextareaPage = ShowcasePage(
    id = "text-area",
    title = "Text Area",
    category = ShowcaseCategory.Inputs,
    description = "Multi-line expandable text input field for longform text entry.",
    usageCode = """
        val bio = rememberTextFieldState()

        shadcnMuted("Multi-line expandable text input field for longform content.")
        Spacer(Modifier.height(8.dp))
        ShadcnCard(modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp), contentPadding = 0.dp) {
            Column(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShadcnText("Text Area Preview")
                ShadcnFieldLabel("Biography")
                shadcnTextarea(bio)
            }
        }
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/textarea-demo.tsx",
    previewHeight = 460,
    notes = listOf("Scrollable multiline input with focus ring boundary."),
    hero = {
        val bio = rememberTextFieldState()
        shadcnMuted("Multi-line expandable text input field for longform content.")
        Spacer(Modifier.height(8.dp))
        ShadcnCard(
            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
            contentPadding = 0.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShadcnText("Text Area Preview")
                ShadcnFieldLabel("Biography")
                ShadcnTextarea(bio)
            }
        }
    },
)
