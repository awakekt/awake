/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.status

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnToastState
import com.awakekt.awake.ui.shadcn.components.ShadcnToaster

internal val ToastPage = ShowcasePage(
    id = "toast",
    title = "Toast",
    category = ShowcaseCategory.Status,
    description = "A succinct message that is displayed temporarily as a floating viewport overlay.",
    usageCode = """
        val toastState = remember { ShadcnToastState() }
        ShadcnButton("Show Toast", onClick = {
            toastState.show("Event has been created", title = "Scheduled: Catch up")
        })
        ShadcnToaster(toastState)
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/toast-demo.tsx",
    previewHeight = 320,
    notes = listOf(
        "ShadcnToaster renders in LayerKind.Toast and floats non-modally at the viewport edge.",
        "Ages automatically via LocalFrameClock and disappears without blocking underlying interactions.",
    ),
    hero = {
        val toastState = remember { ShadcnToastState() }
        Row(horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2)) {
            ShadcnButton(
                "Simple Toast",
                variant = ShadcnButtonVariant.Outline,
                onClick = {
                    toastState.show("Your message has been sent.")
                },
            )
            ShadcnButton(
                "With Title",
                variant = ShadcnButtonVariant.Default,
                onClick = {
                    toastState.show(
                        "Friday, February 10, 2026 at 5:57 PM",
                        title = "Event has been created",
                    )
                },
            )
            ShadcnButton(
                "Clear All",
                variant = ShadcnButtonVariant.Secondary,
                onClick = {
                    toastState.clear()
                },
            )
        }
        ShadcnToaster(toastState)
    },
)
