/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui.pages.blocks

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.sample.uishowcase.ui.ShowcaseCategory
import com.awakekt.awake.sample.uishowcase.ui.ShowcasePage
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnCarousel
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import com.awakekt.awake.ui.shadcn.components.shadcnMuted

internal val CarouselPage = ShowcasePage(
    id = "carousel",
    title = "Carousel",
    category = ShowcaseCategory.Blocks,
    description = "A horizontally paged content slider with previous/next controls.",
    usageCode = """
ShadcnCarousel(
    items = listOf("Slide 1", "Slide 2", "Slide 3"),
    currentIndex = index,
    onIndexChange = { index = it },
) { slide ->
    ShadcnCard { ShadcnText(slide) }
}
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/carousel-demo.tsx",
    previewHeight = 350,
    hero = {
        val indexState = remember { CarouselIntState(0) }
        val slides = remember {
            listOf(
                "Slide 1: Welcome to the Awake Engine UI showcase",
                "Slide 2: 50+ Accessible UI Primitives Styled with OKLCH Tokens",
                "Slide 3: High Performance Retained Rendering & Cross-Platform Parity",
            )
        }

        ShadcnCarousel(
            items = slides,
            currentIndex = indexState.value,
            onIndexChange = { indexState.value = it },
        ) { text ->
            ShadcnCard {
                Column {
                    ShadcnText("Feature Highlight", variant = ShadcnTextVariant.H3)
                    shadcnMuted(text)
                }
            }
        }
    },
)

private class CarouselIntState(var value: Int)
