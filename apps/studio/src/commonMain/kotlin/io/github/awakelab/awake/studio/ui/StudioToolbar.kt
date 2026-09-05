/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.studio.ui

import io.github.awakelab.awake.compose.foundation.BorderSides
import io.github.awakelab.awake.compose.foundation.background
import io.github.awakelab.awake.compose.foundation.border
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.editor.core.license.AwakeLicenseRegistry
import io.github.awakelab.awake.heroicons.icon.HeroIcons
import io.github.awakelab.awake.scene.runtime.ScenePhaseStats
import io.github.awakelab.awake.studio.fixture.StudioSceneRegistry
import io.github.awakelab.awake.studio.state.StudioContract
import io.github.awakelab.awake.studio.state.StudioStore
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadge
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadgeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnDropdownMenu
import io.github.awakelab.awake.ui.shadcn.components.ShadcnIcon
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuEntry
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenubar
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenubarMenu
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSelectTrigger
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
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

/** Actions and state for the top bar dialog triggers and switches. */
internal class StudioTopBarControls(
    val onOpenMarketplace: () -> Unit = {},
    val onOpenLicense: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onToggleDarkMode: () -> Unit = {},
    val isDarkMode: Boolean = true,
    val onOpenSceneFile: () -> Unit = {},
    val onImportAssetFile: () -> Unit = {},
)

/** Full-width top bar for Studio's fixed integration fixture. */
context(_: Composer)
internal fun StudioTopBar(
    sceneTitle: String,
    store: StudioStore,
    historyAction: context(Composer) () -> Unit,
    playAction: context(Composer) () -> Unit,
    controls: StudioTopBarControls = StudioTopBarControls(),
) {
    val theme = shadcnTheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT)
            .background(theme.palette.background)
            .border(1f.dp, theme.palette.border, sides = BorderSides(false, false, true, false))
            .padding(horizontal = BAR_INSET)
            .testTag("studio-top-bar"),
        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudioAppBrand()
            Box(Modifier.width(1.dp).height(16.dp).background(theme.palette.border))
            StudioMenuBar(store, controls)
            Box(Modifier.width(1.dp).height(16.dp).background(theme.palette.border))
            historyAction()
        }
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            playAction()
            StudioScenePicker(sceneTitle, store)
        }
        StudioTopBarActions(controls)
    }
}

context(_: Composer)
private fun StudioAppBrand() {
    val theme = shadcnTheme
    Row(
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnIcon(
            HeroIcons.Outline24.cube,
            modifier = Modifier.size(18.dp),
            tint = theme.palette.primary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnText("Awake", variant = ShadcnTextVariant.Small)
            ShadcnBadge("STUDIO", variant = ShadcnBadgeVariant.Secondary)
        }
    }
}

context(_: Composer)
private fun StudioTopBarActions(
    controls: StudioTopBarControls,
) {
    Row(
        horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnButton(
            variant = ShadcnButtonVariant.Ghost,
            size = ShadcnButtonSizeVariant.IconSm,
            onClick = controls.onToggleDarkMode,
        ) {
            ShadcnIcon(if (controls.isDarkMode) HeroIcons.Outline24.sun else HeroIcons.Outline24.moon)
        }
        ShadcnButton(
            variant = ShadcnButtonVariant.Ghost,
            size = ShadcnButtonSizeVariant.IconSm,
            onClick = controls.onOpenSettings,
        ) {
            ShadcnIcon(HeroIcons.Outline24.adjustmentsHorizontal)
        }
        ShadcnButton(
            label = "Marketplace",
            variant = ShadcnButtonVariant.Outline,
            size = ShadcnButtonSizeVariant.Sm,
            onClick = controls.onOpenMarketplace,
        )
        if (AwakeLicenseRegistry.isProActive) {
            ShadcnButton(
                label = "PRO",
                size = ShadcnButtonSizeVariant.Sm,
                onClick = controls.onOpenLicense,
            )
        } else {
            ShadcnButton(
                label = "Free",
                variant = ShadcnButtonVariant.Ghost,
                size = ShadcnButtonSizeVariant.Sm,
                onClick = controls.onOpenLicense,
            )
        }
    }
}

private class StudioMenuBarState {
    var openMenuIndex: Int? = null
}

private val FILE_MENU_ENTRIES: List<ShadcnMenuEntry> = listOf(
    ShadcnMenuItem("New Scene"),
    ShadcnMenuItem("Open Scene..."),
    ShadcnMenuItem("Save Scene"),
    ShadcnMenuSeparator,
    ShadcnMenuItem("Import Asset..."),
    ShadcnMenuSeparator,
    ShadcnMenuItem("Exit"),
)

private val EDIT_MENU_ENTRIES: List<ShadcnMenuEntry> = listOf(
    ShadcnMenuItem("Undo"),
    ShadcnMenuItem("Redo"),
)

private val VIEW_MENU_ENTRIES: List<ShadcnMenuEntry> = listOf(
    ShadcnMenuItem("Toggle Dark/Light Mode"),
    ShadcnMenuSeparator,
    ShadcnMenuItem("Reset Layout"),
)

private val TOOLS_MENU_ENTRIES: List<ShadcnMenuEntry> = listOf(
    ShadcnMenuItem("Marketplace..."),
    ShadcnMenuItem("Settings & Theme..."),
    ShadcnMenuSeparator,
    ShadcnMenuItem("Manage License..."),
)

private val HELP_MENU_ENTRIES: List<ShadcnMenuEntry> = listOf(
    ShadcnMenuItem("Awake Documentation"),
    ShadcnMenuItem("About Awake Studio"),
)

context(_: Composer)
private fun StudioMenuBar(
    store: StudioStore,
    controls: StudioTopBarControls,
) {
    val menuState = remember { StudioMenuBarState() }

    ShadcnMenubar(bordered = false) {
        ShadcnMenubarMenu(
            title = "File",
            isOpen = menuState.openMenuIndex == 0,
            onOpenChange = { open -> menuState.openMenuIndex = if (open) 0 else null },
            entries = FILE_MENU_ENTRIES,
            onItemSelected = { index ->
                when (index) {
                    1 -> controls.onOpenSceneFile()
                    2 -> store.dispatch(StudioContract.Intent.SaveScene)
                    4 -> controls.onImportAssetFile()
                }
            },
            id = "menubar-file",
        )
        ShadcnMenubarMenu(
            title = "Edit",
            isOpen = menuState.openMenuIndex == 1,
            onOpenChange = { open -> menuState.openMenuIndex = if (open) 1 else null },
            entries = EDIT_MENU_ENTRIES,
            onItemSelected = {},
            id = "menubar-edit",
        )
        ShadcnMenubarMenu(
            title = "View",
            isOpen = menuState.openMenuIndex == 2,
            onOpenChange = { open -> menuState.openMenuIndex = if (open) 2 else null },
            entries = VIEW_MENU_ENTRIES,
            onItemSelected = { index -> if (index == 0) controls.onToggleDarkMode() },
            id = "menubar-view",
        )
        ShadcnMenubarMenu(
            title = "Tools",
            isOpen = menuState.openMenuIndex == 3,
            onOpenChange = { open -> menuState.openMenuIndex = if (open) 3 else null },
            entries = TOOLS_MENU_ENTRIES,
            onItemSelected = { index ->
                when (index) {
                    0 -> controls.onOpenMarketplace()
                    1 -> controls.onOpenSettings()
                    3 -> controls.onOpenLicense()
                }
            },
            id = "menubar-tools",
        )
        ShadcnMenubarMenu(
            title = "Help",
            isOpen = menuState.openMenuIndex == 4,
            onOpenChange = { open -> menuState.openMenuIndex = if (open) 4 else null },
            entries = HELP_MENU_ENTRIES,
            onItemSelected = {},
            id = "menubar-help",
        )
    }
}

/** Shell chrome owning only a local dropdown state and its explicitly supplied store. */
context(_: Composer)
private fun StudioScenePicker(sceneTitle: String, store: StudioStore) {
    val picker = remember { ScenePickerState() }
    val entries = remember {
        StudioSceneRegistry.all.map { ShadcnMenuItem(it.title) }
    }
    ShadcnDropdownMenu(
        entries = entries,
        expanded = picker.expanded,
        onExpandedChange = { picker.expanded = it },
        onItemSelected = { index ->
            val selectedScene = StudioSceneRegistry.all.getOrNull(index)
            if (selectedScene != null) {
                store.selectScene(selectedScene.id)
            }
        },
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
