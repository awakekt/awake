/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.status

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.sample.uishowcase.ui.showcaseMatrix
import com.awakekt.awake.ui.shadcn.components.ShadcnAvatar
import com.awakekt.awake.ui.shadcn.components.ShadcnAvatarSizeVariant
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

internal val AvatarPage = ShowcasePage(
    id = "avatar",
    title = "Avatar",
    category = ShowcaseCategory.Status,
    description = "An image element with a fallback for representing the user.",
    usageCode = """shadcnAvatar(initials = "JD")""",
    referenceExample = "registry/new-york-v4/examples/avatar-demo.tsx",
    previewHeight = 360,
    notes = listOf("Rounded avatar with initial text fallback."),
    hero = {
        Row(horizontalArrangement = Arrangement.spacedByHorizontal(8.dp)) {
            ShadcnAvatar(initials = "RJ")
            ShadcnAvatar(initials = "AK")
            ShadcnAvatar(initials = "MS")
        }
        Spacer(Modifier.height(12.dp))
        shadcnMuted("Each avatar is a standalone recipe call.")
    },
    variants = {
        showcaseMatrix(ShadcnAvatarSizeVariant.entries) { size ->
            ShadcnAvatar(initials = "AW", size = size)
        }
    },
)
