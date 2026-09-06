/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.tailwind.Tw

/**
 * `ShadcnCarousel`: Horizontally paged content slider with previous and next navigation controls.
 *
 * **Tailwind Reference**: `relative w-full flex items-center gap-3`.
 *
 * Use cases:
 * - Onboarding slide decks & feature showcases.
 * - Image galleries and media carousels.
 *
 * **Example Usage**:
 * ```kotlin
 * ShadcnCarousel(
 *     items = slidesList,
 *     currentIndex = index,
 *     onIndexChange = { index = it },
 * ) { slideData ->
 *     ShadcnCard { ShadcnText(slideData.title) }
 * }
 * ```
 *
 * @param items List of item data elements to render in the carousel.
 * @param currentIndex Index of the currently active slide.
 * @param onIndexChange Callback triggered when navigating between slides.
 * @param modifier Custom layout modifier applied to the carousel container.
 * @param itemContent Slot lambda rendering individual slide items.
 *
 * Keywords: carousel, slider, content slider, paged list, gallery slider.
 */
context(_: Composer)
fun <T> ShadcnCarousel(
    items: List<T>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: (context(Composer) (T) -> Unit),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnButton(
            label = "<",
            variant = ShadcnButtonVariant.Outline,
            size = ShadcnButtonSizeVariant.Sm,
            enabled = currentIndex > 0,
            onClick = { if (currentIndex > 0) onIndexChange(currentIndex - 1) },
        )

        Box(modifier = Modifier.weight(1f)) {
            val currentItem = items.getOrNull(currentIndex)
            if (currentItem != null) {
                itemContent(currentItem)
            }
        }

        ShadcnButton(
            label = ">",
            variant = ShadcnButtonVariant.Outline,
            size = ShadcnButtonSizeVariant.Sm,
            enabled = currentIndex < items.size - 1,
            onClick = { if (currentIndex < items.size - 1) onIndexChange(currentIndex + 1) },
        )
    }
}
