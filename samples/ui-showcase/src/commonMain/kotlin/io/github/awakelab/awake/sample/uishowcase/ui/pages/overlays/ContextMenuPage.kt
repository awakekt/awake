/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.overlays

import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.showcasePlaceholder

internal val ContextMenuPage = showcasePlaceholder(
    id = "context-menu",
    title = "Context Menu",
    category = ShowcaseCategory.Overlays,
    description = "Displays a menu located at the pointer, triggered by a right click.",
    missing = "shadcnContextMenu is not ported to the compose engine.",
)
