/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.studio.ui

import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.heroicons.icon.HeroIcons
import io.github.awakelab.awake.scene.runtime.ScenePhaseStats
import io.github.awakelab.awake.studio.state.StudioStore
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadgeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadge
import io.github.awakelab.awake.ui.shadcn.components.shadcnDropdownMenu
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSelectTrigger
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme
import kotlin.math.roundToInt

internal val TOP_BAR_HEIGHT: Dp = 44.dp
internal val STATUS_BAR_HEIGHT: Dp = 26.dp
private val BAR_INSET: Dp = 12.dp
private val SCENE_PICKER_WIDTH: Dp = 168.dp

/** Holds "sim+render 12.3", the widest figure the bar shows. */
private val STATUS_FIGURE_WIDTH: Dp = 104.dp

/** Shorter than the band so the picker does not touch the hairline it sits above. */
private val SCENE_PICKER_HEIGHT: Dp = 32.dp
private val STUDIO_SCENE_ENTRY = listOf(ShadcnMenuItem("Rotating cube"))

/** Full-width top bar for Studio's fixed integration fixture. */
context(_: Composer)
internal fun StudioTopBar(
    sceneTitle: String,
    store: StudioStore,
    historyAction: context(Composer) () -> Unit,
    playAction: context(Composer) () -> Unit,
) {
    BarBand(
        TOP_BAR_HEIGHT,
        "studio-top-bar",
        // Undo and redo sit with the document controls on the left, not beside Play. They act on
        // the scene; Play changes what the whole window means.
        leading = {
            Row(
                horizontalArrangement = Arrangement.spacedByHorizontal(BAR_INSET),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudioScenePicker(sceneTitle, store)
                historyAction()
            }
        },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                playAction()
            }
        },
    )
}

/** Shell chrome owning only a local dropdown state and its explicitly supplied store. */
context(_: Composer)
private fun StudioScenePicker(sceneTitle: String, store: StudioStore) {
    Row(
        horizontalArrangement = Arrangement.spacedByHorizontal(BAR_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnText("Awake Studio")
        val picker = remember { ScenePickerState() }
        shadcnDropdownMenu(
            entries = STUDIO_SCENE_ENTRY,
            expanded = picker.expanded,
            onExpandedChange = { picker.expanded = it },
            onItemSelected = { store.reloadFixture() },
            menuModifier = Modifier.width(SCENE_PICKER_WIDTH),
            id = "studio-top-bar-scene-menu",
        ) { onClick ->
            ShadcnSelectTrigger(
                sceneTitle,
                Modifier.width(SCENE_PICKER_WIDTH).height(SCENE_PICKER_HEIGHT)
                    .testTag("studio-top-bar-scene"),
                onClick = onClick,
            )
        }
    }
}

/** Kept in composition so the controlled menu survives the frame loop without leaking into store state. */
private class ScenePickerState {
    var expanded: Boolean = false
}

/**
 * Full-width status bar: mode, entity count and frame timings at the left, backend at the right.
 *
 * [entityCount] counts NAMED entities -- exactly the rows the hierarchy lists, so the two never
 * disagree. [backend] is what the game was configured with rather than probed from the window;
 * it was a hard-coded "Vulkan" literal that read as fact on every platform including web.
 *
 * **The trial-pass figure is gone.** It counted `ui-core`'s re-measure passes, and this shell
 * composes once -- printing "trials 0" would report a mechanism that no longer exists as though
 * it were idle.
 */
context(_: Composer)
internal fun StudioStatusBar(
    mode: String,
    backend: String,
    entityCount: Int,
    fps: Float = 0f,
    frameTimeMs: Float = 0f,
    phases: ScenePhaseStats? = null,
) {
    BarBand(
        STATUS_BAR_HEIGHT,
        "studio-status-bar",
        leading = {
            Row(
                horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadcnText(mode)
                StatusFigure("$entityCount entities")
                StatusFigure("${fps.roundToInt()} fps")
                StatusFigure("$frameTimeMs ms")
                // Only while F2 has phase collection on. Disabled, `phaseStats()` reports zeros
                // rather than null, so this read "ui 0.0ms wait 0.0ms stage 0.0ms sim+render
                // 0.0ms" on every frame of every run -- four figures that were never measured,
                // permanently occupying the bar that says which mode you are in.
                phases?.takeIf { it.isMeasured }?.let {
                    // The whole point of the split: "40ms" alone cannot tell a UI-layout problem
                    // from a GPU-submission one, and those cost months apart to fix.
                    StatusFigure("ui ${it.uiBuildMs.roundToTenth()}")
                    // Separate from stage on purpose: this is time blocked on the GPU, and while
                    // it was inside stage a GPU-bound frame read as an expensive UI.
                    StatusFigure("wait ${it.uiWaitMs.roundToTenth()}")
                    StatusFigure("stage ${it.uiStageMs.roundToTenth()}")
                    StatusFigure("sim+render ${it.simRenderMs.roundToTenth()}")
                }
            }
        },
        trailing = { ShadcnBadge(backend, variant = ShadcnBadgeVariant.Outline) },
    )
}

/**
 * One reading on the status bar, in a box wide enough for its own widest value.
 *
 * Fixed width, because these are live counters in a proportional font: "9 fps" and "60 fps" are
 * different widths, so every figure to the right of a changing digit slid sideways every frame.
 * The row was one dash-joined string, which made that the whole line at once.
 */
context(_: Composer)
private fun StatusFigure(text: String) {
    ShadcnText(text, Modifier.width(STATUS_FIGURE_WIDTH), variant = ShadcnTextVariant.Muted)
}

/**
 * A full-bleed shell band: exactly [height] tall, muted, square, inset horizontally only.
 *
 * Its height must BE its declared height. The ui-core version wrapped a fixed-height row in a
 * card and each band came out 32px taller than the constant naming it -- and because the shell
 * *subtracted* those constants to size the workspace, it overflowed by exactly the two bands'
 * padding and pushed the status bar off-screen. The shell no longer subtracts anything, so that
 * failure has nowhere left to happen.
 */
context(_: Composer)
private fun BarBand(
    height: Dp,
    tag: String,
    leading: context(Composer) () -> Unit,
    trailing: context(Composer) () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(shadcnTheme.palette.muted)
            .padding(horizontal = BAR_INSET)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        trailing()
    }
}

private fun Float.roundToTenth(): Float = (this * 10f).roundToInt() / 10f
