/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.inputs

import com.awakekt.awake.compose.foundation.text.rememberTextFieldState
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnFieldLabel
import com.awakekt.awake.ui.shadcn.components.ShadcnInput
import com.awakekt.awake.ui.shadcn.components.ShadcnSelectTrigger
import com.awakekt.awake.ui.shadcn.components.ShadcnSwitch
import com.awakekt.awake.ui.shadcn.components.shadcnField
import com.awakekt.awake.ui.shadcn.components.shadcnFieldGroup
import com.awakekt.awake.ui.shadcn.components.shadcnFieldLegend
import com.awakekt.awake.ui.shadcn.components.shadcnFieldSeparator
import com.awakekt.awake.ui.shadcn.components.shadcnFieldSet

/** Which plan the hero demo's subscription dropdown currently shows. */
private class PlanIndex {
    var value: Int = 0
}

private class ReceiptsState {
    var value: Boolean = true
}

internal val FieldPage = ShowcasePage(
    id = "field",
    title = "Field",
    category = ShowcaseCategory.Inputs,
    description = "Label, description, validation, and control composed as one form field primitive.",
    usageCode = """shadcnFieldSet { shadcnFieldLegend("Billing"); shadcnFieldGroup { ... } }""",
    referenceExample = "registry/new-york-v4/examples/field-demo.tsx",
    previewHeight = 620,
    notes = listOf("FieldSet/FieldLegend/FieldGroup own the spacing rhythm; controls stay unaware of it."),
    hero = {
        val name = rememberTextFieldState()
        val email = rememberTextFieldState()
        val plan = remember { PlanIndex() }
        val receipts = remember { ReceiptsState() }
        val plans = listOf("Starter", "Pro", "Enterprise")

        shadcnFieldSet {
            shadcnFieldLegend("Billing details")
            shadcnFieldGroup {
                shadcnField {
                    ShadcnFieldLabel("Name on card")
                    ShadcnInput(name)
                }
                shadcnField {
                    ShadcnFieldLabel("Billing email")
                    ShadcnInput(email)
                }
            }
            shadcnFieldSeparator(label = "Plan")
            shadcnFieldGroup {
                shadcnField {
                    ShadcnFieldLabel("Subscription")
                    ShadcnSelectTrigger(
                        value = plans[plan.value],
                        onClick = { plan.value = (plan.value + 1) % plans.size },
                    )
                }
                shadcnField {
                    ShadcnFieldLabel("Email me receipts")
                    ShadcnSwitch(receipts.value, onCheckedChange = { receipts.value = it })
                }
            }
        }
    },
)
