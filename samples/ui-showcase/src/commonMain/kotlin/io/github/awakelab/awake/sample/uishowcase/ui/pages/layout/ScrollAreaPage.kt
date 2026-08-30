/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.layout

import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.showcasePlaceholder

internal val ScrollAreaPage = showcasePlaceholder(
    id = "scroll-area",
    title = "Scroll Area",
    category = ShowcaseCategory.Layout,
    description = "Augments native scroll functionality for custom, cross-browser styling.",
    // Plain scrolling exists (`Modifier.verticalScroll`, which ShowcaseApp's own sidebar uses).
    // What is missing is the styled thumb-on-hover chrome upstream's ScrollArea draws over it.
    missing = "shadcnScrollArea (the styled thumb) is not ported to the compose engine.",
    referenceExample = "registry/new-york-v4/examples/scroll-area-demo.tsx",
)
