/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.blocks

import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnForm
import com.awakekt.awake.ui.shadcn.components.ShadcnFormField
import com.awakekt.awake.ui.shadcn.components.ShadcnInput

internal val FormPage = ShowcasePage(
    id = "form",
    title = "Form",
    category = ShowcaseCategory.Blocks,
    description = "Validated form composition with per-field error states, helper text, and submit gating.",
    usageCode = """
ShadcnForm(onSubmit = { ... }) {
    ShadcnFormField(
        label = "Username",
        description = "This is your public display name.",
        error = usernameError,
    ) {
        ShadcnInput(state = usernameState, placeholder = "shadcn")
    }
    ShadcnButton("Submit")
}
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/form-demo.tsx",
    previewHeight = 400,
    hero = {
        val usernameState = remember { TextFieldState("shadcn") }
        val emailState = remember { TextFieldState("") }

        ShadcnForm(onSubmit = {}) {
            ShadcnFormField(
                label = "Username",
                description = "This is your public display name.",
            ) {
                ShadcnInput(state = usernameState, placeholder = "shadcn")
            }

            ShadcnFormField(
                label = "Email Address",
                error = "Please enter a valid email address.",
            ) {
                ShadcnInput(state = emailState, placeholder = "m@example.com")
            }

            ShadcnButton("Submit")
        }
    },
)
