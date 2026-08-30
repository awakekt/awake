/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.status

import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.showcasePlaceholder

internal val ToastPage = showcasePlaceholder(
    id = "toast",
    title = "Toast",
    category = ShowcaseCategory.Status,
    description = "A succinct message that is displayed temporarily.",
    missing = "shadcnToast is not ported to the compose engine.",
)
