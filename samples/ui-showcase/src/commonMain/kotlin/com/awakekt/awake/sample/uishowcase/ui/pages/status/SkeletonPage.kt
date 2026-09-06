/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.status

import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnSkeleton

internal val SkeletonPage = ShowcasePage(
    id = "skeleton",
    title = "Skeleton",
    category = ShowcaseCategory.Status,
    description = "Use to show a placeholder while content is loading.",
    usageCode = """shadcnSkeleton(modifier = Modifier.width(260.dp).height(20.dp))""",
    referenceExample = "registry/new-york-v4/examples/skeleton-demo.tsx",
    previewHeight = 300,
    notes = listOf("Muted background box indicating content loading."),
    hero = {
        ShadcnSkeleton(modifier = Modifier.width(260.dp).height(20.dp))
        Spacer(Modifier.height(8.dp))
        ShadcnSkeleton(modifier = Modifier.width(320.dp).height(12.dp))
        Spacer(Modifier.height(8.dp))
        ShadcnSkeleton(modifier = Modifier.width(200.dp).height(12.dp))
    },
)
