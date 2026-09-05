/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBreadcrumb

internal val BreadcrumbPage = ShowcasePage(
    id = "breadcrumb",
    title = "Breadcrumb",
    category = ShowcaseCategory.Layout,
    description = "Displays the path to the current resource using a hierarchy of links.",
    usageCode = """shadcnBreadcrumb(crumbs = listOf("Docs", "Components", "Button"))""",
    referenceExample = "registry/new-york-v4/examples/breadcrumb-demo.tsx",
    previewHeight = 240,
    notes = listOf("Compact path trail for deep page hierarchies."),
    hero = {
        ShadcnBreadcrumb(crumbs = listOf("Docs", "Components", "Button"))
    },
)
