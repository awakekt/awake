/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.showcase.ui

import com.awakekt.awake.compose.foundation.TextureQuad
import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.horizontalScroll
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.rememberScrollState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.capture.FramebufferAttachment
import com.awakekt.awake.showcase.EngineShowcase
import com.awakekt.awake.showcase.ShowcaseDebugOption
import com.awakekt.awake.showcase.ShowcaseDebugToggles
import com.awakekt.awake.showcase.ShowcaseFramebufferDebugger
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnTabs
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import kotlin.math.roundToInt

internal val CARD_INSET: Dp = 12.dp
private val ROW_GAP: Dp = 6.dp
private val PREVIEW_WIDTH: Dp = 220.dp
private val PREVIEW_HEIGHT: Dp = 124.dp

/** Tags for a test to address the debug card and its toggles. */
internal object ShowcaseDebugTags {
    const val CARD = "showcase-debug"
    const val TAB_DIAGNOSTICS = "showcase-debug-tab-diagnostics"
    const val TAB_ENVIRONMENT = "showcase-debug-tab-environment"
    const val TAB_FRAMEBUFFER = "showcase-debug-tab-framebuffer"
    const val TAB_STATS = "showcase-debug-tab-stats"
    const val PHASE_TIMINGS = "showcase-debug-phase-timings"
    const val FRAMEBUFFER_ATTACHMENT = "showcase-debug-framebuffer-attachment"
    const val FRAMEBUFFER_CAPTURE = "showcase-debug-framebuffer-capture"
    const val FRAMEBUFFER_PREVIEW = "showcase-debug-framebuffer-preview"
    const val FRAMEBUFFER_STATUS = "showcase-debug-framebuffer-status"
    const val NAV_GRID = "showcase-debug-nav-grid"
    const val CORRIDOR = "showcase-debug-corridor"
    const val BOUNDS = "showcase-debug-bounds"
    const val INSTANCE_BOUNDS = "showcase-debug-instance-bounds"
    const val SHADOW_CASCADES = "showcase-debug-shadow-cascades"
    const val CASCADED_SHADOWS = "showcase-debug-cascaded-shadows"
    const val SHADOWS = "showcase-debug-shadows"
    const val OCCLUSION = "showcase-debug-occlusion"
    const val LIGHTS = "showcase-debug-lights"
    const val COLLIDERS = "showcase-debug-colliders"
    const val TERRAIN_DIAGNOSTICS = "showcase-debug-terrain-diagnostics"
    const val WIREFRAME = "showcase-debug-wireframe"
    const val FOG_ENABLED = "showcase-debug-fog-enabled"
    const val FOG_DENSITY = "showcase-debug-fog-density"
    const val FOG_RESET = "showcase-debug-fog-reset"
    const val STRESS_COUNT = "showcase-debug-stress-count"
    const val STRESS_MOVING = "showcase-debug-stress-moving"
    const val AUDIO_VOLUME = "showcase-debug-audio-volume"
    const val AUDIO_MUTE = "showcase-debug-audio-mute"
    const val PROP_SPAWN_BOX = "showcase-debug-prop-spawn-box"
    const val PROP_SPAWN_SPHERE = "showcase-debug-prop-spawn-sphere"
    const val PROP_SPAWN_WEDGE = "showcase-debug-prop-spawn-wedge"
    const val PROP_IMPULSE = "showcase-debug-prop-impulse"
    const val PROP_RESET = "showcase-debug-prop-reset"
    const val CAMERA_MODE = "showcase-debug-camera-mode"
}

/**
 * What the running showcase draws over itself, separate from which showcase is running.
 *
 * Everything here is off by default. These are diagnostics — a marker on unwalkable ground, the
 * cells a long-range route passes through — and a diagnostic that is on before anyone asked reads
 * as part of the demonstration, which is exactly the confusion the always-on navigation overlay
 * caused.
 *
 * Engine-wide controls stay reachable on every sample. The sample-specific section only contains
 * diagnostics whose underlying data exists for the selected demonstration.
 */

/**
 * One row per toggle, rather than a call per toggle.
 *
 * The block below was nine near-identical five-line calls and adding a tenth pushed it past what
 * anyone reads in one go. Reading and writing stay as lambdas because each flag is a separate
 * property on an object, not a map.
 */
private class DebugToggle(
    val option: ShowcaseDebugOption,
    val label: String,
    val tag: String,
    val checked: () -> Boolean,
    val onChange: (Boolean) -> Unit,
)

private val DEBUG_TOGGLES = listOf(
    DebugToggle(ShowcaseDebugOption.NavGrid, "Nav grid", ShowcaseDebugTags.NAV_GRID, { ShowcaseDebugToggles.showNavGrid }) {
        ShowcaseDebugToggles.showNavGrid = it
    },
    DebugToggle(ShowcaseDebugOption.Corridor, "Route corridor", ShowcaseDebugTags.CORRIDOR, { ShowcaseDebugToggles.showCorridor }) {
        ShowcaseDebugToggles.showCorridor = it
    },
    DebugToggle(ShowcaseDebugOption.Bounds, "Bounds", ShowcaseDebugTags.BOUNDS, { ShowcaseDebugToggles.showBounds }) {
        ShowcaseDebugToggles.showBounds = it
    },
    DebugToggle(
        ShowcaseDebugOption.InstanceBounds,
        "Instance bounds",
        ShowcaseDebugTags.INSTANCE_BOUNDS,
        { ShowcaseDebugToggles.showInstanceBounds },
    ) { ShowcaseDebugToggles.showInstanceBounds = it },
    DebugToggle(
        ShowcaseDebugOption.ShadowCascades,
        "Shadow cascades",
        ShowcaseDebugTags.SHADOW_CASCADES,
        { ShowcaseDebugToggles.showShadowCascades },
    ) { ShowcaseDebugToggles.showShadowCascades = it },
    DebugToggle(
        ShowcaseDebugOption.CascadedShadows,
        "Cascaded shadows",
        ShowcaseDebugTags.CASCADED_SHADOWS,
        { ShowcaseDebugToggles.cascadedShadows },
    ) { ShowcaseDebugToggles.cascadedShadows = it },
    DebugToggle(ShowcaseDebugOption.Shadows, "Directional shadows", ShowcaseDebugTags.SHADOWS, { ShowcaseDebugToggles.shadows }) {
        ShowcaseDebugToggles.shadows = it
    },
    DebugToggle(ShowcaseDebugOption.Occlusion, "Occluders", ShowcaseDebugTags.OCCLUSION, { ShowcaseDebugToggles.showOcclusion }) {
        ShowcaseDebugToggles.showOcclusion = it
    },
    DebugToggle(ShowcaseDebugOption.Lights, "Light gizmo", ShowcaseDebugTags.LIGHTS, { ShowcaseDebugToggles.showLights }) {
        ShowcaseDebugToggles.showLights = it
    },
    DebugToggle(ShowcaseDebugOption.Colliders, "Colliders", ShowcaseDebugTags.COLLIDERS, { ShowcaseDebugToggles.showColliders }) {
        ShowcaseDebugToggles.showColliders = it
    },
    DebugToggle(
        ShowcaseDebugOption.TerrainProbes,
        "Terrain probes",
        ShowcaseDebugTags.TERRAIN_DIAGNOSTICS,
        { ShowcaseDebugToggles.showTerrainDiagnostics },
    ) { ShowcaseDebugToggles.showTerrainDiagnostics = it },
    DebugToggle(ShowcaseDebugOption.Wireframe, "Wireframe", ShowcaseDebugTags.WIREFRAME, { ShowcaseDebugToggles.wireframe }) {
        ShowcaseDebugToggles.wireframe = it
    },
)

context(_: Composer)
internal fun ShowcaseDebugCard(
    showcase: EngineShowcase,
    framebufferDebugger: ShowcaseFramebufferDebugger,
    modifier: Modifier = Modifier,
    selectedTab: String = "diagnostics",
    onSelectedChange: (String) -> Unit = {},
    phaseTimingsEnabled: Boolean = false,
    onPhaseTimingsChange: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier
            .testTag(ShowcaseDebugTags.CARD)
            .fillMaxWidth()
            .background(ShowcaseTheme.palette.card)
            .padding(CARD_INSET),
    ) {
        ShadcnTabs(
            selectedValue = selectedTab,
            onSelectedChange = onSelectedChange,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
        ) {
            tab("diagnostics", "Render", tag = ShowcaseDebugTags.TAB_DIAGNOSTICS)
            tab("environment", "Scene", tag = ShowcaseDebugTags.TAB_ENVIRONMENT)
            tab("framebuffer", "Buffers", tag = ShowcaseDebugTags.TAB_FRAMEBUFFER)
            tab("stats", "Stats", tag = ShowcaseDebugTags.TAB_STATS)
        }

        if (selectedTab == "stats") {
            Toggle("Phase timings", ShowcaseDebugTags.PHASE_TIMINGS, phaseTimingsEnabled, onPhaseTimingsChange)
            ShowcaseStatsCard()
        } else if (selectedTab == "diagnostics") {
            RenderControls(showcase)
        } else if (selectedTab == "environment") {
            EnvironmentControls()
        } else {
            FramebufferDebuggerPanel(framebufferDebugger)
        }
    }
}

context(_: Composer)
private fun FramebufferDebuggerPanel(debugger: ShowcaseFramebufferDebugger) {
    ShadcnText("Attachment", variant = ShadcnTextVariant.Small)
    ShadcnTabs(
        selectedValue = debugger.selectedAttachment.name,
        onSelectedChange = { value ->
            FramebufferAttachment.entries.firstOrNull { it.name == value }?.let(debugger::select)
        },
        modifier = Modifier.fillMaxWidth().padding(top = ROW_GAP),
    ) {
        FramebufferAttachment.entries.forEach { attachment ->
            tab(
                value = attachment.name,
                label = attachment.shortLabel(),
                tag = "${ShowcaseDebugTags.FRAMEBUFFER_ATTACHMENT}-${attachment.name.lowercase()}",
            )
        }
    }
    ShadcnButton(
        label = if (debugger.captureInFlight) "Capturing…" else "Capture attachment",
        variant = ShadcnButtonVariant.Outline,
        size = ShadcnButtonSizeVariant.Sm,
        enabled = !debugger.captureInFlight,
        modifier = Modifier
            .padding(top = ROW_GAP)
            .testTag(ShowcaseDebugTags.FRAMEBUFFER_CAPTURE),
        onClick = debugger::requestCapture,
    )

    val capture = debugger.lastCapture
    FramebufferPreview(debugger)
    ShadcnText(
        text = when {
            debugger.error != null -> debugger.error!!
            capture?.available == true -> "${capture.width}×${capture.height} · ${capture.attachment.shortLabel()}"
            else -> "Color0 is available; other attachments need retained targets"
        },
        modifier = Modifier
            .padding(top = ROW_GAP)
            .testTag(ShowcaseDebugTags.FRAMEBUFFER_STATUS),
        variant = ShadcnTextVariant.Muted,
    )
}

private fun FramebufferAttachment.shortLabel(): String = when (this) {
    FramebufferAttachment.Color0 -> "Color"
    FramebufferAttachment.Depth -> "Depth"
    FramebufferAttachment.Stencil -> "Stencil"
    FramebufferAttachment.Normal -> "Normal"
}

context(_: Composer)
private fun RenderControls(showcase: EngineShowcase) {
    DEBUG_TOGGLES.filter { it.option.isGlobal }.forEach { toggle ->
        Toggle(
            label = toggle.label,
            tag = toggle.tag,
            checked = toggle.checked(),
            onChange = toggle.onChange,
        )
    }
    DEBUG_TOGGLES.filter { !it.option.isGlobal && it.option in showcase.debugOptions }.forEach { toggle ->
        Toggle(
            label = toggle.label,
            tag = toggle.tag,
            checked = toggle.checked(),
            onChange = toggle.onChange,
        )
    }
    showcase.controls?.let { controls -> controls() }
}

context(_: Composer)
private fun EnvironmentControls() {
    Toggle(
        label = "Enable Fog",
        tag = ShowcaseDebugTags.FOG_ENABLED,
        checked = ShowcaseDebugToggles.fogEnabled,
        onChange = {
            ShowcaseDebugToggles.fogEnabled = it
            ShowcaseDebugToggles.overrideFog = true
        },
    )
    FloatSliderField(
        label = "Fog Density",
        value = ShowcaseDebugToggles.fogDensity,
        min = 0f,
        max = 0.05f,
        steps = 100,
        tag = ShowcaseDebugTags.FOG_DENSITY,
        enabled = ShowcaseDebugToggles.fogEnabled,
        format = { ((it * 10000f).roundToInt() / 10000f).toString() },
        onValueChange = {
            ShowcaseDebugToggles.fogDensity = it
            ShowcaseDebugToggles.overrideFog = true
        },
    )
    ColorRgbField(
        label = "Fog Color",
        color = ShowcaseDebugToggles.fogColor,
        tagPrefix = "showcase-debug-fog-color",
        enabled = ShowcaseDebugToggles.fogEnabled,
        onColorChange = {
            ShowcaseDebugToggles.fogColor = it
            ShowcaseDebugToggles.overrideFog = true
        },
    )
    if (ShowcaseDebugToggles.overrideFog) {
        Row(
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnButton(
                label = "Reset Defaults",
                variant = ShadcnButtonVariant.Outline,
                size = ShadcnButtonSizeVariant.Sm,
                modifier = Modifier.testTag(ShowcaseDebugTags.FOG_RESET),
                onClick = {
                    ShowcaseDebugToggles.overrideFog = false
                    ShowcaseDebugToggles.fogEnabled = true
                    ShowcaseDebugToggles.fogDensity = 0.005f
                    ShowcaseDebugToggles.fogColor = Color(0.7f, 0.75f, 0.8f, 1f)
                },
            )
        }
    }
}

context(_: Composer)
private fun FramebufferPreview(debugger: ShowcaseFramebufferDebugger) {
    val capture = debugger.lastCapture
    val preview = debugger.previewMaterial.takeIf {
        capture?.available == true && capture.attachment == debugger.selectedAttachment
    }
    if (preview != null) {
        TextureQuad(
            material = preview,
            modifier = Modifier
                .padding(top = ROW_GAP)
                .size(PREVIEW_WIDTH, PREVIEW_HEIGHT)
                .border(1.dp, ShowcaseTheme.palette.border, RoundedCornerShape(3.dp))
                .testTag(ShowcaseDebugTags.FRAMEBUFFER_PREVIEW),
        )
    } else {
        Box(
            Modifier
                .padding(top = ROW_GAP)
                .size(PREVIEW_WIDTH, PREVIEW_HEIGHT)
                .background(ShowcaseTheme.palette.muted, RoundedCornerShape(3.dp))
                .border(1.dp, ShowcaseTheme.palette.border, RoundedCornerShape(3.dp))
                .testTag(ShowcaseDebugTags.FRAMEBUFFER_PREVIEW),
            contentAlignment = Alignment.Center,
        ) {
            ShadcnText(
                if (debugger.captureInFlight) "Reading attachment…" else "No capture yet",
                variant = ShadcnTextVariant.Muted,
            )
        }
    }
}
