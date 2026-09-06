/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.overlays

import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.showcasePlaceholder

internal val AlertDialogPage = showcasePlaceholder(
    id = "alert-dialog",
    title = "Alert Dialog",
    category = ShowcaseCategory.Overlays,
    description = "A modal dialog that interrupts the user with important content and expects a response.",
    missing = "shadcnAlertDialog is not ported to the compose engine.",
)
