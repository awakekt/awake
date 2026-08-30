/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.overlays

import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.showcasePlaceholder

internal val DrawerPage = showcasePlaceholder(
    id = "drawer",
    title = "Drawer",
    category = ShowcaseCategory.Overlays,
    description = "A slide-over panel anchored to any viewport edge.",
    missing = "shadcnDrawer is not ported to the compose engine.",
    referenceExample = "registry/new-york-v4/examples/drawer-demo.tsx",
)
