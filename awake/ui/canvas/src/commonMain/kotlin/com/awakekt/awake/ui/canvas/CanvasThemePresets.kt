/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

/**
 * The built-in looks of [CanvasThemePreset]: each a skin over every control and the tokens it names,
 * all plain data a theme replaces field by field.
 */
object CanvasThemePresets {
    /** The slots [preset] gives every control; null for [CanvasThemePreset.None]. */
    fun skin(preset: CanvasThemePreset): CanvasControlSkin? = when (preset) {
        CanvasThemePreset.None -> null
        CanvasThemePreset.MinimalFlat -> flat
        CanvasThemePreset.Glass -> glass
        CanvasThemePreset.Textured -> textured
    }

    /** The colour tokens [preset] gives, by name. */
    fun colors(preset: CanvasThemePreset): Map<String, String> = when (preset) {
        CanvasThemePreset.None -> emptyMap()
        CanvasThemePreset.MinimalFlat -> FLAT_COLORS
        CanvasThemePreset.Glass -> GLASS_COLORS
        CanvasThemePreset.Textured -> TEXTURED_COLORS
    }

    /** The image tokens [preset] gives, by name: the textured preset's pictures, which a project provides. */
    fun images(preset: CanvasThemePreset): Map<String, CanvasImage> =
        if (preset == CanvasThemePreset.Textured) TEXTURED_IMAGES else emptyMap()

    private val FLAT_COLORS = mapOf(
        "base" to "#FFFFFF26",
        "knob" to "#FFFFFFB3",
        "fill" to "#FFFFFF33",
        "pressed" to "#FFFFFF66",
        "accent" to "#4C9AFF",
        "label" to "#FFFFFF",
        "shade" to "#000000A0",
        "danger" to "#E5484D",
    )

    private val GLASS_COLORS = mapOf(
        "base" to "#FFFFFF1A",
        "knob" to "#FFFFFF59",
        "fill" to "#FFFFFF26",
        "pressed" to "#FFFFFF4D",
        "rim" to "#FFFFFF66",
        "glow" to "#FFFFFF33",
        "accent" to "#9AD1FF",
        "label" to "#FFFFFF",
        "shade" to "#0A0F1A99",
        "danger" to "#FF6B6B",
        "shadow" to "#00000059",
    )

    private val TEXTURED_COLORS = mapOf(
        "label" to "#FFF6E0",
        "shade" to "#000000A0",
        "accent" to "#FFC857",
        "danger" to "#E5484D",
    )

    private val TEXTURED_IMAGES = mapOf(
        "pad" to CanvasImage("controls/pad.png"),
        "knob" to CanvasImage("controls/knob.png"),
        "button" to CanvasImage("controls/button.png", sliceLeft = SLICE, sliceTop = SLICE, sliceRight = SLICE, sliceBottom = SLICE),
        "buttonPressed" to CanvasImage("controls/button-pressed.png", sliceLeft = SLICE, sliceTop = SLICE, sliceRight = SLICE, sliceBottom = SLICE),
    )

    private const val SLICE = 24

    private fun fill(color: String, radius: Float? = null) = CanvasStyle(background = color, cornerRadius = radius)

    private val pressedSwell = mapOf(CanvasControlState.Pressed to CanvasSlotOverride(scale = PRESSED_SCALE))

    private val readyPulse = CanvasSlotOverride(scale = READY_SCALE)

    private fun buttonStates(pressed: CanvasStyle) = mapOf(
        CanvasControlState.Pressed to CanvasSlotOverride(style = pressed, scale = PRESSED_BUTTON_SCALE),
        CanvasControlState.Disabled to CanvasSlotOverride(alpha = DISABLED_ALPHA),
        CanvasControlState.Ready to readyPulse,
    )

    /** The parts every preset shares: the label, the sweep, the badge, the aim's guides. */
    private fun common(rangeColor: String) = CanvasButtonSkin(
        label = CanvasSlot(style = CanvasStyle(textColor = "@label", textShadow = CanvasTextShadow())),
        cooldown = CanvasSlot(style = fill("@shade")),
        charges = CanvasSlot(style = fill("@accent", BADGE_RADIUS), states = mapOf(CanvasControlState.Ready to readyPulse), transitionSeconds = FADE),
        aimCursor = CanvasSlot(style = fill("@accent"), circle = true, size = CURSOR),
        aimRange = CanvasSlot(style = CanvasStyle(background = rangeColor, borderWidth = 1f, borderColor = "@accent"), circle = true),
        cancelZone = CanvasSlot(
            style = CanvasStyle(background = "@shade", cornerRadius = ZONE_RADIUS, borderWidth = 2f, borderColor = "@danger"),
            states = mapOf(CanvasControlState.Cancelling to CanvasSlotOverride(style = fill("@danger"), scale = PRESSED_SCALE)),
            transitionSeconds = FADE,
        ),
    )

    private val flat = CanvasControlSkin(
        joystick = CanvasJoystickSkin(
            base = CanvasSlot(style = fill("@base"), circle = true),
            knob = CanvasSlot(style = fill("@knob"), circle = true, states = pressedSwell, transitionSeconds = FADE),
            sprintRing = CanvasSlot(style = CanvasStyle(borderWidth = 3f, borderColor = "@accent"), circle = true),
            arrows = CanvasSlot(style = fill("@accent", ARROW_RADIUS), size = ARROW),
        ),
        button = common("@base").copy(
            background = CanvasSlot(style = fill("@fill"), circle = true, states = buttonStates(fill("@pressed")), transitionSeconds = FADE),
        ),
        look = CanvasLookSkin(feedback = CanvasSlot(style = fill("@base"), circle = true, size = FEEDBACK)),
    )

    private val glassPane = CanvasStyle(
        gradient = CanvasGradient("@glow", "@fill"),
        borderWidth = 1f,
        borderColor = "@rim",
        shadow = CanvasShadow(color = "@shadow", offsetY = 4f, blur = 12f),
    )

    private val glass = CanvasControlSkin(
        joystick = CanvasJoystickSkin(
            base = CanvasSlot(style = glassPane, circle = true),
            knob = CanvasSlot(
                style = CanvasStyle(background = "@knob", borderWidth = 1f, borderColor = "@rim", shadow = CanvasShadow(color = "@shadow")),
                circle = true,
                states = pressedSwell,
                transitionSeconds = FADE,
            ),
            sprintRing = CanvasSlot(style = CanvasStyle(borderWidth = 2f, borderColor = "@accent"), circle = true),
            arrows = CanvasSlot(style = fill("@accent", ARROW_RADIUS), size = ARROW),
        ),
        button = common("@base").copy(
            background = CanvasSlot(style = glassPane, circle = true, states = buttonStates(CanvasStyle(background = "@pressed")), transitionSeconds = FADE),
        ),
        look = CanvasLookSkin(feedback = CanvasSlot(style = glassPane, circle = true, size = FEEDBACK)),
    )

    private val textured = CanvasControlSkin(
        joystick = CanvasJoystickSkin(
            base = CanvasSlot(style = CanvasStyle(image = CanvasImage("@pad"))),
            knob = CanvasSlot(style = CanvasStyle(image = CanvasImage("@knob")), states = pressedSwell, transitionSeconds = FADE),
            sprintRing = CanvasSlot(style = CanvasStyle(borderWidth = 3f, borderColor = "@accent"), circle = true),
        ),
        button = common("#FFFFFF1A").copy(
            background = CanvasSlot(
                style = CanvasStyle(image = CanvasImage("@button")),
                states = buttonStates(CanvasStyle(image = CanvasImage("@buttonPressed"))),
                transitionSeconds = FADE,
            ),
        ),
        look = CanvasLookSkin(feedback = CanvasSlot(style = CanvasStyle(image = CanvasImage("@knob")), size = FEEDBACK)),
    )

    private const val FADE = 0.12f
    private const val PRESSED_SCALE = 1.1f
    private const val PRESSED_BUTTON_SCALE = 0.94f
    private const val READY_SCALE = 1.15f
    private const val DISABLED_ALPHA = 0.4f
    private const val BADGE_RADIUS = 9f
    private const val ZONE_RADIUS = 12f
    private const val ARROW_RADIUS = 3f
    private const val ARROW = 14f
    private const val CURSOR = 28f
    private const val FEEDBACK = 56f
}
