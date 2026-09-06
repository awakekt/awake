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
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextarea
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

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
