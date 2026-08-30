/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.overlays

import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.showcasePlaceholder

internal val SheetPage = showcasePlaceholder(
    id = "sheet",
    title = "Sheet",
    category = ShowcaseCategory.Overlays,
    description = "Extends the Dialog component to display content that complements the main content of the screen.",
    missing = "shadcnSheet is not ported to the compose engine.",
)
