/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.status

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.sample.uishowcase.ui.showcaseMatrix
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAvatar
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAvatarSizeVariant
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

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
