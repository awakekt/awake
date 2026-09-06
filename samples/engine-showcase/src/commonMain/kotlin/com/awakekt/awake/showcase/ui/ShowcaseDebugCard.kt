/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.showcase.ShowcaseDebugToggles
import com.awakekt.awake.ui.shadcn.components.ShadcnCheckbox
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant

private val CARD_WIDTH: Dp = 190.dp
private val CARD_INSET: Dp = 12.dp
private val ROW_GAP: Dp = 6.dp

/** Tags for a test to address the debug card and its toggles. */
internal object ShowcaseDebugTags {
    const val CARD = "showcase-debug"
    const val NAV_GRID = "showcase-debug-nav-grid"
    const val CORRIDOR = "showcase-debug-corridor"
    const val BOUNDS = "showcase-debug-bounds"
    const val SHADOW_CASCADES = "showcase-debug-shadow-cascades"
    const val CASCADED_SHADOWS = "showcase-debug-cascaded-shadows"
    const val SHADOWS = "showcase-debug-shadows"
    const val OCCLUSION = "showcase-debug-occlusion"
    const val LIGHTS = "showcase-debug-lights"
    const val COLLIDERS = "showcase-debug-colliders"
    const val WIREFRAME = "showcase-debug-wireframe"
}

/**
 * What the running showcase draws over itself, separate from which showcase is running.
 *
 * Everything here is off by default. These are diagnostics — a marker on unwalkable ground, the
 * cells a long-range route passes through — and a diagnostic that is on before anyone asked reads
 * as part of the demonstration, which is exactly the confusion the always-on navigation overlay
 * caused.
 *
 * Toggles that mean nothing to the showcase currently running are still listed rather than hidden.
 * A control that appears and disappears as you change demonstration is harder to find than one
 * that is simply inert, and every entry here says which showcase it belongs to.
 */

/**
 * One row per toggle, rather than a call per toggle.
 *
 * The block below was nine near-identical five-line calls and adding a tenth pushed it past what
 * anyone reads in one go. Reading and writing stay as lambdas because each flag is a separate
 * property on an object, not a map.
 */
private class DebugToggle(
    val label: String,
    val tag: String,
    val checked: () -> Boolean,
    val onChange: (Boolean) -> Unit,
)

private val DEBUG_TOGGLES = listOf(
    DebugToggle("Nav grid", ShowcaseDebugTags.NAV_GRID, { ShowcaseDebugToggles.showNavGrid }) {
        ShowcaseDebugToggles.showNavGrid = it
    },
    DebugToggle("Route corridor", ShowcaseDebugTags.CORRIDOR, { ShowcaseDebugToggles.showCorridor }) {
        ShowcaseDebugToggles.showCorridor = it
    },
    // The engine's own diagnostics, on every showcase rather than the navigation ones' two. They
    // were reachable only by authoring a WorldDebugSettings node into a scene document, which no
    // showcase did -- so they read as unimplemented rather than as unreachable.
    DebugToggle("Bounds", ShowcaseDebugTags.BOUNDS, { ShowcaseDebugToggles.showBounds }) {
        ShowcaseDebugToggles.showBounds = it
    },
    DebugToggle(
        "Shadow cascades",
        ShowcaseDebugTags.SHADOW_CASCADES,
        { ShowcaseDebugToggles.showShadowCascades },
    ) { ShowcaseDebugToggles.showShadowCascades = it },
    DebugToggle(
        "Cascaded shadows",
        ShowcaseDebugTags.CASCADED_SHADOWS,
        { ShowcaseDebugToggles.cascadedShadows },
    ) { ShowcaseDebugToggles.cascadedShadows = it },
    DebugToggle("Occluders", ShowcaseDebugTags.OCCLUSION, { ShowcaseDebugToggles.showOcclusion }) {
        ShowcaseDebugToggles.showOcclusion = it
    },
    DebugToggle("Light gizmo", ShowcaseDebugTags.LIGHTS, { ShowcaseDebugToggles.showLights }) {
        ShowcaseDebugToggles.showLights = it
    },
    DebugToggle("Colliders", ShowcaseDebugTags.COLLIDERS, { ShowcaseDebugToggles.showColliders }) {
        ShowcaseDebugToggles.showColliders = it
    },
    DebugToggle("Wireframe", ShowcaseDebugTags.WIREFRAME, { ShowcaseDebugToggles.wireframe }) {
        ShowcaseDebugToggles.wireframe = it
    },
)

context(_: Composer)
internal fun ShowcaseDebugCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .testTag(ShowcaseDebugTags.CARD)
            .width(CARD_WIDTH)
            .background(ShowcaseTheme.palette.card)
            .padding(CARD_INSET),
    ) {
        ShadcnText("Debug", variant = ShadcnTextVariant.Small)
        DEBUG_TOGGLES.forEach { toggle ->
            Toggle(
                label = toggle.label,
                tag = toggle.tag,
                checked = toggle.checked(),
                onChange = toggle.onChange,
            )
        }
    }
}

context(_: Composer)
private fun Toggle(label: String, tag: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.padding(top = ROW_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadcnCheckbox(
            checked = checked,
            modifier = Modifier.testTag(tag),
            onCheckedChange = onChange,
        )
        ShadcnText(
            label,
            modifier = Modifier.padding(start = ROW_GAP),
            variant = ShadcnTextVariant.Small,
        )
    }
}
