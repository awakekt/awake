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
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.capture.FramebufferAttachment
import com.awakekt.awake.scene.rendering.debug.RenderDiagnostics
import com.awakekt.awake.scene.runtime.LocalFrameStats
import com.awakekt.awake.showcase.EngineShowcase
import com.awakekt.awake.showcase.ShowcaseDebugOption
import com.awakekt.awake.showcase.ShowcaseDebugToggles
import com.awakekt.awake.showcase.ShowcaseFramebufferDebugger
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCheckbox
import com.awakekt.awake.ui.shadcn.components.ShadcnSlider
import com.awakekt.awake.ui.shadcn.components.ShadcnTabs
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import kotlin.math.roundToInt

private val CARD_WIDTH: Dp = 244.dp
private val CARD_INSET: Dp = 12.dp
private val ROW_GAP: Dp = 6.dp
private val FIELD_WIDTH: Dp = 220.dp
private val PREVIEW_WIDTH: Dp = 220.dp
private val PREVIEW_HEIGHT: Dp = 124.dp

/** Tags for a test to address the debug card and its toggles. */
internal object ShowcaseDebugTags {
    const val CARD = "showcase-debug"
    const val TAB_DIAGNOSTICS = "showcase-debug-tab-diagnostics"
    const val TAB_ENVIRONMENT = "showcase-debug-tab-environment"
    const val TAB_FRAMEBUFFER = "showcase-debug-tab-framebuffer"
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

private class DebugCardState {
    var selectedTab: String = "diagnostics"
}

context(_: Composer)
@Suppress("LongMethod")
internal fun ShowcaseDebugCard(
    showcase: EngineShowcase,
    framebufferDebugger: ShowcaseFramebufferDebugger,
    modifier: Modifier = Modifier,
) {
    val state = remember { DebugCardState() }
    Column(
        modifier = modifier
            .testTag(ShowcaseDebugTags.CARD)
            .width(CARD_WIDTH)
            .background(ShowcaseTheme.palette.card)
            .padding(CARD_INSET),
    ) {
        ShadcnTabs(
            selectedValue = state.selectedTab,
            onSelectedChange = { state.selectedTab = it },
            modifier = Modifier.width(FIELD_WIDTH).padding(bottom = 4.dp),
        ) {
            tab("diagnostics", "Diagnostics", tag = ShowcaseDebugTags.TAB_DIAGNOSTICS)
            tab("environment", "Environment", tag = ShowcaseDebugTags.TAB_ENVIRONMENT)
            tab("framebuffer", "Framebuffer", tag = ShowcaseDebugTags.TAB_FRAMEBUFFER)
        }

        if (state.selectedTab == "diagnostics") {
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
        } else if (state.selectedTab == "environment") {
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
                    modifier = Modifier.padding(top = 10.dp).width(FIELD_WIDTH),
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
        } else {
            FramebufferDebuggerPanel(framebufferDebugger)
        }
    }
}

context(_: Composer)
@Suppress("LongMethod")
private fun FramebufferDebuggerPanel(debugger: ShowcaseFramebufferDebugger) {
    ShadcnText("Attachment", variant = ShadcnTextVariant.Small)
    ShadcnTabs(
        selectedValue = debugger.selectedAttachment.name,
        onSelectedChange = { value ->
            FramebufferAttachment.entries.firstOrNull { it.name == value }?.let(debugger::select)
        },
        modifier = Modifier.width(FIELD_WIDTH).padding(top = ROW_GAP),
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
internal fun ShowcaseStatsCard(modifier: Modifier = Modifier) {
    val frameStats = LocalFrameStats.current
    val phases = frameStats.phases
    Column(
        modifier = modifier.width(CARD_WIDTH).background(ShowcaseTheme.palette.card).padding(CARD_INSET),
    ) {
        ShadcnText("FPS: ${frameStats.fps.oneDecimal()}  Frame: ${frameStats.frameTimeMs.oneDecimal()}ms", variant = ShadcnTextVariant.Small)
        if (phases.isMeasured) {
            ShadcnText("UI: ${phases.uiBuildMs.oneDecimal()}ms  Wait: ${phases.uiWaitMs.oneDecimal()}ms", variant = ShadcnTextVariant.Small)
            ShadcnText("Stage: ${phases.uiStageMs.oneDecimal()}ms  Sim: ${phases.simRenderMs.oneDecimal()}ms", variant = ShadcnTextVariant.Small)
        } else {
            ShadcnText("Press F2 for phase timings", variant = ShadcnTextVariant.Small)
        }
        ShadcnText("Memory: unavailable on this target", variant = ShadcnTextVariant.Small)
        ShadcnText("Surface aspect: ${RenderDiagnostics.surfaceAspect.oneDecimal()}", variant = ShadcnTextVariant.Small)
        ShadcnText("Draws: ${RenderDiagnostics.submittedDrawCalls}  Instances: ${RenderDiagnostics.submittedInstances}", variant = ShadcnTextVariant.Small)
        ShadcnText("Unresolved: ${RenderDiagnostics.unresolvedDrawCalls}", variant = ShadcnTextVariant.Small)
        ShadcnText("Frustum culled: ${RenderDiagnostics.frustumCulled}", variant = ShadcnTextVariant.Small)
        ShadcnText("Occluded: ${RenderDiagnostics.occluded}", variant = ShadcnTextVariant.Small)
    }
}

private fun Float.oneDecimal(): String = ((this * 10f).roundToInt() / 10f).toString()

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

/** Reusable float slider field with label, readout, and slider track. */
context(_: Composer)
internal fun FloatSliderField(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    tag: String? = null,
    enabled: Boolean = true,
    format: (Float) -> String = { it.oneDecimal() },
    onValueChange: (Float) -> Unit,
) {
    Column(modifier.padding(top = ROW_GAP)) {
        Row(
            modifier = Modifier.width(FIELD_WIDTH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnText(label, variant = ShadcnTextVariant.Small)
            Spacer(Modifier.weight(1f))
            ShadcnText(format(value), variant = ShadcnTextVariant.Muted)
        }
        val sliderMod = Modifier.padding(top = 4.dp).width(FIELD_WIDTH)
        ShadcnSlider(
            value = value,
            min = min,
            max = max,
            steps = steps,
            enabled = enabled,
            modifier = if (tag != null) sliderMod.testTag(tag) else sliderMod,
            onValueChange = onValueChange,
        )
    }
}

/** Reusable 2D vector field with X and Y sliders. */
context(_: Composer)
internal fun Vec2SliderField(
    label: String,
    x: Float,
    y: Float,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 1f,
    steps: Int = 0,
    tagPrefix: String? = null,
    enabled: Boolean = true,
    onValueChange: (Float, Float) -> Unit,
) {
    Column(modifier.padding(top = ROW_GAP)) {
        ShadcnText(label, variant = ShadcnTextVariant.Small)
        FloatSliderField(
            label = "X",
            value = x,
            min = min,
            max = max,
            steps = steps,
            tag = tagPrefix?.let { "$it-x" },
            enabled = enabled,
            onValueChange = { onValueChange(it, y) },
        )
        FloatSliderField(
            label = "Y",
            value = y,
            min = min,
            max = max,
            steps = steps,
            tag = tagPrefix?.let { "$it-y" },
            enabled = enabled,
            onValueChange = { onValueChange(x, it) },
        )
    }
}

/** Reusable 3D vector field with X, Y, and Z sliders. */
context(_: Composer)
internal fun Vec3SliderField(
    label: String,
    x: Float,
    y: Float,
    z: Float,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 1f,
    steps: Int = 0,
    labels: Triple<String, String, String> = Triple("X", "Y", "Z"),
    tagPrefix: String? = null,
    enabled: Boolean = true,
    onValueChange: (Float, Float, Float) -> Unit,
) {
    Column(modifier.padding(top = ROW_GAP)) {
        ShadcnText(label, variant = ShadcnTextVariant.Small)
        FloatSliderField(
            label = labels.first,
            value = x,
            min = min,
            max = max,
            steps = steps,
            tag = tagPrefix?.let { "$it-x" },
            enabled = enabled,
            onValueChange = { onValueChange(it, y, z) },
        )
        FloatSliderField(
            label = labels.second,
            value = y,
            min = min,
            max = max,
            steps = steps,
            tag = tagPrefix?.let { "$it-y" },
            enabled = enabled,
            onValueChange = { onValueChange(x, it, z) },
        )
        FloatSliderField(
            label = labels.third,
            value = z,
            min = min,
            max = max,
            steps = steps,
            tag = tagPrefix?.let { "$it-z" },
            enabled = enabled,
            onValueChange = { onValueChange(x, y, it) },
        )
    }
}

/** Reusable RGB Color field with live color preview swatch and component sliders. */
context(_: Composer)
internal fun ColorRgbField(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    tagPrefix: String? = null,
    enabled: Boolean = true,
    onColorChange: (Color) -> Unit,
) {
    Column(modifier.padding(top = ROW_GAP)) {
        Row(
            modifier = Modifier.width(FIELD_WIDTH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadcnText(label, variant = ShadcnTextVariant.Small)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(16.dp)
                    .background(color, RoundedCornerShape(3.dp))
                    .border(1.dp, ShowcaseTheme.palette.border, RoundedCornerShape(3.dp)),
            )
        }
        FloatSliderField(
            label = "R",
            value = color.r,
            min = 0f,
            max = 1f,
            steps = 100,
            tag = tagPrefix?.let { "$it-r" },
            enabled = enabled,
            format = { (it * 100f).roundToInt().toString() + "%" },
            onValueChange = { onColorChange(Color(it, color.g, color.b, color.a)) },
        )
        FloatSliderField(
            label = "G",
            value = color.g,
            min = 0f,
            max = 1f,
            steps = 100,
            tag = tagPrefix?.let { "$it-g" },
            enabled = enabled,
            format = { (it * 100f).roundToInt().toString() + "%" },
            onValueChange = { onColorChange(Color(color.r, it, color.b, color.a)) },
        )
        FloatSliderField(
            label = "B",
            value = color.b,
            min = 0f,
            max = 1f,
            steps = 100,
            tag = tagPrefix?.let { "$it-b" },
            enabled = enabled,
            format = { (it * 100f).roundToInt().toString() + "%" },
            onValueChange = { onColorChange(Color(color.r, color.g, it, color.a)) },
        )
    }
}
