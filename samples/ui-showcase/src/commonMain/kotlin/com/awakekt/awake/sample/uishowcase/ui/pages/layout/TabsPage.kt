/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.layout

import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnTabs

private class TabsSelected {
    var value: String = "account"
}

internal val TabsPage = ShowcasePage(
    id = "tabs",
    title = "Tabs",
    category = ShowcaseCategory.Layout,
    description = "A set of layered sections of content displayed one panel at a time.",
    usageCode = """ShadcnTabs(selectedValue = "account", onSelectedChange = ::select) {
    tab("account", "Account")
    tab("password", "Password")
}""",
    referenceExample = "registry/new-york-v4/examples/tabs-demo.tsx",
    previewHeight = 300,
    notes = listOf("Muted track with active tab surface elevation."),
    hero = {
        val state = remember { TabsSelected() }
        ShadcnTabs(selectedValue = state.value, onSelectedChange = { state.value = it }) {
            tab("account", "Account")
            tab("password", "Password")
        }
    },
)
