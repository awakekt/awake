/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.ui.material3

import io.github.awakelab.awake.compose.foundation.layout.fillMaxSize
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.layout.Measurable
import io.github.awakelab.awake.compose.ui.layout.Placeable
import io.github.awakelab.awake.compose.ui.layout.SubcomposeLayout
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp

/** Insets occupied by [Scaffold]'s measured bars. Apply them to content that must avoid the bars. */
data class ScaffoldPaddingValues(
    val start: Dp = 0.dp,
    val top: Dp = 0.dp,
    val end: Dp = 0.dp,
    val bottom: Dp = 0.dp,
)

private enum class ScaffoldSlot { TopBar, BottomBar, Content, FloatingActionButton }

private val FloatingActionButtonInset = 16.dp

/**
 * Material 3's screen layout with independently measured top and bottom bars.
 *
 * Content is intentionally placed behind the bars, matching Material's `Scaffold`; use the
 * [ScaffoldPaddingValues] supplied to [content] for scroll or other body content that must avoid
 * them. The first version has no window-inset or snackbar support yet.
 */
context(composer: Composer)
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: context(Composer) () -> Unit = {},
    bottomBar: context(Composer) () -> Unit = {},
    floatingActionButton: context(Composer) () -> Unit = {},
    content: context(Composer) (ScaffoldPaddingValues) -> Unit,
) {
    SubcomposeLayout(modifier = modifier.fillMaxSize()) { constraints ->
        val loose = Constraints.of(0, constraints.maxWidth, 0, constraints.maxHeight)
        val topBarPlaceables = subcompose(ScaffoldSlot.TopBar, topBar).measureAll(loose)
        val bottomBarPlaceables = subcompose(ScaffoldSlot.BottomBar, bottomBar).measureAll(loose)
        val topBarHeight = topBarPlaceables.maxHeight()
        val bottomBarHeight = bottomBarPlaceables.maxHeight()
        val padding = ScaffoldPaddingValues(
            top = topBarHeight.toDp(),
            bottom = bottomBarHeight.toDp(),
        )
        val contentPlaceables = subcompose(ScaffoldSlot.Content) { content(padding) }.measureAll(loose)
        val fabPlaceables = subcompose(ScaffoldSlot.FloatingActionButton, floatingActionButton).measureAll(loose)

        val width = constraints.constrainWidth(
            maxOf(
                contentPlaceables.maxWidth(),
                topBarPlaceables.maxWidth(),
                bottomBarPlaceables.maxWidth(),
                fabPlaceables.maxWidth(),
            ),
        )
        val height = constraints.constrainHeight(
            maxOf(
                contentPlaceables.maxHeight(),
                topBarHeight + bottomBarHeight,
                fabPlaceables.maxHeight() + FloatingActionButtonInset.toPx().toInt(),
            ),
        )
        val fabInset = FloatingActionButtonInset.toPx().toInt()

        layout(width, height) {
            topBarPlaceables.placeAll(0, 0)
            contentPlaceables.placeAll(0, 0)
            bottomBarPlaceables.placeAll(0, height - bottomBarHeight)
            fabPlaceables.forEach { placeable ->
                placeable.placeAt(
                    width - fabInset - placeable.width,
                    height - bottomBarHeight - fabInset - placeable.height,
                )
            }
        }
    }
}

private fun List<Measurable>.measureAll(constraints: Constraints): List<Placeable> = map { it.measure(constraints) }

private fun List<Placeable>.maxWidth(): Int = maxOfOrNull { it.width } ?: 0

private fun List<Placeable>.maxHeight(): Int = maxOfOrNull { it.height } ?: 0

private fun List<Placeable>.placeAll(x: Int, y: Int) {
    forEach { it.placeAt(x, y) }
}
