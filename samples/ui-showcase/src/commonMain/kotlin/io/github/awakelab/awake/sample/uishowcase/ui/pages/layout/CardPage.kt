/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.foundation.text.rememberTextFieldState
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCardContent
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCardFooter
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCardHeader
import io.github.awakelab.awake.ui.shadcn.components.ShadcnInput
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.components.shadcnLabel

internal val CardPage = ShowcasePage(
    id = "card",
    title = "Card",
    category = ShowcaseCategory.Layout,
    description = "Displays a card with header, content, and footer.",
    usageCode = """
        ShadcnCard(Modifier.width(360.dp)) {
            ShadcnCardHeader {
                Column(Modifier.weight(1f)) {
                    ShadcnText("Login to your account", variant = ShadcnTextVariant.H3)
                    ShadcnText("Enter your email below to login", variant = ShadcnTextVariant.Muted)
                }
                ShadcnButton("Sign Up", variant = ShadcnButtonVariant.Link)
            }
            ShadcnCardContent {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    shadcnLabel("Email")
                    ShadcnInput(email)
                    shadcnLabel("Password")
                    ShadcnInput(password)
                }
            }
            ShadcnCardFooter {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShadcnButton("Login", modifier = Modifier.fillMaxWidth())
                    ShadcnButton("Login with Google", modifier = Modifier.fillMaxWidth(), variant = ShadcnButtonVariant.Outline)
                }
            }
        }
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/card-demo.tsx",
    previewHeight = 420,
    notes = listOf(
        "Encapsulated content surface with card and border tokens.",
        "CardAction's upstream CSS-grid trailing placement is approximated with a Row -- this engine has no grid primitive.",
    ),
    hero = {
        val email = rememberTextFieldState()
        val password = rememberTextFieldState()
        ShadcnCard(modifier = Modifier.width(360.dp)) {
            ShadcnCardHeader {
                Column(Modifier.weight(1f)) {
                    ShadcnText("Login to your account", variant = ShadcnTextVariant.H3)
                    ShadcnText("Enter your email below to login to your account", variant = ShadcnTextVariant.Muted)
                }
                ShadcnButton("Sign Up", variant = ShadcnButtonVariant.Link)
            }
            ShadcnCardContent {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        shadcnLabel("Email")
                        ShadcnInput(email, placeholder = "m@example.com")
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        shadcnLabel("Password")
                        ShadcnInput(password)
                    }
                }
            }
            ShadcnCardFooter {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShadcnButton("Login", modifier = Modifier.fillMaxWidth())
                    ShadcnButton("Login with Google", modifier = Modifier.fillMaxWidth(), variant = ShadcnButtonVariant.Outline)
                }
            }
        }
    },
)
