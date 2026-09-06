/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.layout

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.rememberTextFieldState
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnAvatar
import com.awakekt.awake.ui.shadcn.components.ShadcnBadge
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnInput
import com.awakekt.awake.ui.shadcn.components.ShadcnItem
import com.awakekt.awake.ui.shadcn.components.ShadcnLabel
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

internal val CardPage = ShowcasePage(
    id = "card",
    title = "Card",
    category = ShowcaseCategory.Layout,
    description = "Displays a card with header, content, and footer.",
    usageCode = """
        ShadcnCard(Modifier.width(360.dp)) {
            header {
                Column(Modifier.weight(1f)) {
                    ShadcnText("Login to your account", variant = ShadcnTextVariant.H3)
                }
            }
            content { ... }
        }
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/card-demo.tsx",
    previewHeight = 650,
    notes = listOf(
        "Encapsulated content surface with card and border tokens.",
        "CardAction's upstream CSS-grid trailing placement is approximated with a Row -- this engine has no grid primitive.",
    ),
    hero = {
        val email = rememberTextFieldState()
        val password = rememberTextFieldState()

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ShadcnCard(modifier = Modifier.width(360.dp)) {
                header {
                    Column(Modifier.weight(1f)) {
                        ShadcnText("Login to your account", variant = ShadcnTextVariant.H3)
                        ShadcnText(
                            "Enter your email below to login to your account",
                            variant = ShadcnTextVariant.Muted,
                        )
                    }
                    ShadcnButton("Sign Up", variant = ShadcnButtonVariant.Link)
                }
                content {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ShadcnLabel("Email")
                            ShadcnInput(email, placeholder = "m@example.com")
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ShadcnLabel("Password")
                            ShadcnInput(password)
                        }
                    }
                }
                footer {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ShadcnButton("Login", modifier = Modifier.fillMaxWidth())
                        ShadcnButton(
                            "Login with Google",
                            modifier = Modifier.fillMaxWidth(),
                            variant = ShadcnButtonVariant.Outline,
                        )
                    }
                }
            }

            ShadcnCard(modifier = Modifier.width(360.dp)) {
                header {
                    Column(Modifier.weight(1f)) {
                        ShadcnText("Security Overview", variant = ShadcnTextVariant.H3)
                        ShadcnText(
                            "Manage authentication preferences",
                            variant = ShadcnTextVariant.Muted,
                        )
                    }
                }
                content {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShadcnItem(
                            title = "Two-Factor Auth",
                            description = "Secure login with 2FA",
                            leading = { ShadcnAvatar("2F") },
                            trailing = {
                                ShadcnButton(
                                    "Enable",
                                    size = ShadcnButtonSizeVariant.Sm,
                                )
                            },
                        )
                        ShadcnItem(
                            title = "API Secret Keys",
                            description = "Manage active secret tokens",
                            leading = { ShadcnAvatar("AK") },
                            trailing = { ShadcnBadge("Active") },
                        )
                    }
                }
            }
        }
    },
)
