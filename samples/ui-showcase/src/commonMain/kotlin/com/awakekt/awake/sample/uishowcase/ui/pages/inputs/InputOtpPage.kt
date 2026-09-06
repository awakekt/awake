/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.inputs

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.text.rememberTextFieldState
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnInputOtp
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

internal val InputOtpPage = ShowcasePage(
    id = "input-otp",
    title = "Input OTP",
    category = ShowcaseCategory.Inputs,
    description = "Accessible one-time password component with copy paste functionality.",
    usageCode = """
        val state = rememberTextFieldState("123456")
        ShadcnInputOtp(state = state, length = 6, groupSize = 3)
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/input-otp-demo.tsx",
    hero = {
        val standardState = rememberTextFieldState("482910")
        val groupedState = rememberTextFieldState("934182")

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShadcnText("6-digit standard OTP", variant = ShadcnTextVariant.Small)
                ShadcnInputOtp(state = standardState, length = 6)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShadcnText("Grouped OTP (3-3 split)", variant = ShadcnTextVariant.Small)
                ShadcnInputOtp(state = groupedState, length = 6, groupSize = 3)
            }
        }
    },
)
