/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.rememberScrollState
import com.awakekt.awake.compose.foundation.verticalScroll
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clipToBounds
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalViewportSize
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.scene.runtime.LocalFrameStats
import com.awakekt.awake.showcase.EngineShowcase
import com.awakekt.awake.showcase.ShowcaseFramebufferDebugger
import com.awakekt.awake.showcase.ShowcaseSelection
import com.awakekt.awake.showcase.icon.HeroIcons
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnIcon
import com.awakekt.awake.ui.shadcn.components.ShadcnSheet
import com.awakekt.awake.ui.shadcn.components.ShadcnSheetSide
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import com.awakekt.awake.ui.shadcn.shadcnThemeValues
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme

internal val ShowcaseTheme = shadcnThemeValues(dark = true)

internal object ShowcaseSwitcherTags {
    const val PANEL = "showcase-switcher"
    fun entry(id: String): String = "showcase-switcher-$id"
}

internal object ShowcaseChromeTags {
    const val TOOLBAR = "showcase-toolbar"
    const val MENU = "showcase-menu"
    const val CLOSE_NAV = "showcase-close-navigation"
    const val NAV_SHEET = "showcase-navigation-sheet"
    const val DEBUG = "showcase-open-debug"
    const val CLOSE_DEBUG = "showcase-close-debug"
    const val DEBUG_SHEET = "showcase-debug-sheet"
    const val STATS = "showcase-open-stats"
}

private class ShowcaseChromeState {
    var desktopNavigation = true
    var mobileNavigation = false
    var debugOpen = false
    var debugTab = "diagnostics"
}

/** Viewport width in density-independent units, so high-density phones use the compact layout too. */
private const val COMPACT_WIDTH_DP = 768
private val NAVIGATION_WIDTH = 232.dp
private val TOUCH_TARGET = 44.dp
private val TOOLBAR_ICON_SIZE = 24.dp

context(_: Composer)
internal fun ShowcaseOverlay(
    selection: ShowcaseSelection,
    showcases: List<EngineShowcase>,
    framebufferDebugger: ShowcaseFramebufferDebugger = ShowcaseFramebufferDebugger(),
    modifier: Modifier = Modifier,
    phaseTimingsEnabled: Boolean = false,
    onPhaseTimingsChange: (Boolean) -> Unit = {},
) {
    val state = remember { ShowcaseChromeState() }
    val compact = LocalViewportSize.current.width / LocalDensity.current < COMPACT_WIDTH_DP
    if (!compact) state.mobileNavigation = false
    val active = showcases.firstOrNull { it.id == selection.current }
    provideShadcnTheme(ShowcaseTheme) {
        Box(modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                if (!compact && state.desktopNavigation) {
                    ShowcaseSwitcher(
                        selection,
                        showcases,
                        Modifier.width(NAVIGATION_WIDTH),
                        onClose = { state.desktopNavigation = false },
                    )
                }
                Column(Modifier.weight(1f)) {
                    ShowcaseToolbar(
                        title = active?.title ?: "Engine showcase",
                        onMenu = {
                            if (compact) {
                                state.debugOpen = false
                                state.mobileNavigation = true
                            } else {
                                state.desktopNavigation = !state.desktopNavigation
                            }
                        },
                        onDebug = {
                            state.mobileNavigation = false
                            state.debugTab = "diagnostics"
                            state.debugOpen = true
                        },
                        onStats = {
                            state.mobileNavigation = false
                            state.debugTab = "stats"
                            state.debugOpen = true
                        },
                    )
                }
            }
            ShowcaseNavigationSheet(state, compact, selection, showcases)
            ShowcaseDebugSheet(state, compact) {
                active?.let {
                    ShowcaseDebugCard(
                        it,
                        framebufferDebugger,
                        selectedTab = state.debugTab,
                        onSelectedChange = { tab -> state.debugTab = tab },
                        phaseTimingsEnabled = phaseTimingsEnabled,
                        onPhaseTimingsChange = onPhaseTimingsChange,
                    )
                }
            }
        }
    }
}

context(_: Composer)
private fun ShowcaseToolbar(title: String, onMenu: () -> Unit, onDebug: () -> Unit, onStats: () -> Unit) {
    val stats = LocalFrameStats.current
    Row(
        Modifier.fillMaxWidth().background(ShowcaseTheme.palette.background).padding(4.dp).testTag(ShowcaseChromeTags.TOOLBAR),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnButton(
            modifier = Modifier.size(TOUCH_TARGET).semantics { this[SemanticsProperties.Label] = "Toggle showcases" }
                .testTag(ShowcaseChromeTags.MENU),
            variant = ShadcnButtonVariant.Ghost,
            size = ShadcnButtonSizeVariant.Icon,
            onClick = onMenu,
        ) { ShadcnIcon(HeroIcons.Outline24.bars3, size = TOOLBAR_ICON_SIZE) }
        ShadcnText(title, Modifier.weight(1f), variant = ShadcnTextVariant.Small)
        ShadcnButton(
            label = "${stats.fps.oneDecimal()} fps",
            modifier = Modifier.height(TOUCH_TARGET).testTag(ShowcaseChromeTags.STATS)
                .semantics { this[SemanticsProperties.Label] = "Open performance stats" },
            variant = ShadcnButtonVariant.Ghost,
            onClick = onStats,
        )
        ShadcnButton("Debug", modifier = Modifier.height(TOUCH_TARGET).testTag(ShowcaseChromeTags.DEBUG), onClick = onDebug)
    }
}

context(_: Composer)
internal fun ShowcaseCloseButton(label: String, tag: String, onClick: () -> Unit) {
    ShadcnButton(
        modifier = Modifier.size(TOUCH_TARGET).testTag(tag).semantics { this[SemanticsProperties.Label] = label },
        variant = ShadcnButtonVariant.Ghost,
        size = ShadcnButtonSizeVariant.Icon,
        onClick = onClick,
    ) { ShadcnIcon(HeroIcons.Outline24.xMark, size = TOOLBAR_ICON_SIZE) }
}

context(_: Composer)
private fun ShowcaseDebugSheet(
    state: ShowcaseChromeState,
    compact: Boolean,
    content: context(Composer) () -> Unit,
) {
    ShadcnSheet(
        visible = state.debugOpen,
        onDismissRequest = { state.debugOpen = false },
        side = if (compact) ShadcnSheetSide.Bottom else ShadcnSheetSide.Right,
        modifier = if (compact) Modifier.fillMaxHeight(0.85f) else Modifier,
        id = ShowcaseChromeTags.DEBUG_SHEET,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ShadcnText("Debug", Modifier.weight(1f), variant = ShadcnTextVariant.H4)
                ShowcaseCloseButton("Close debug panel", ShowcaseChromeTags.CLOSE_DEBUG) { state.debugOpen = false }
            }
            Column(Modifier.weight(1f).fillMaxWidth().clipToBounds().verticalScroll(rememberScrollState())) {
                content()
            }
        }
    }
}

context(_: Composer)
private fun ShowcaseNavigationSheet(
    state: ShowcaseChromeState,
    compact: Boolean,
    selection: ShowcaseSelection,
    showcases: List<EngineShowcase>,
) {
    ShadcnSheet(
        visible = compact && state.mobileNavigation,
        onDismissRequest = { state.mobileNavigation = false },
        side = ShadcnSheetSide.Left,
        id = ShowcaseChromeTags.NAV_SHEET,
    ) {
        ShowcaseSwitcher(
            selection,
            showcases,
            Modifier.fillMaxWidth(),
            onClose = { state.mobileNavigation = false },
            onSelect = { state.mobileNavigation = false },
        )
    }
}
