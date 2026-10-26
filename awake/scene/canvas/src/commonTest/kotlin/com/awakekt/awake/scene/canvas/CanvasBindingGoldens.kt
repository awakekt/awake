/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("MatchingDeclarationName") // Named for what it holds, the golden cases; the case class is the one class it needs.

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.text.unicode.TextLengthUnit
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasBar
import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.CanvasButton
import com.awakekt.awake.ui.canvas.CanvasCluster
import com.awakekt.awake.ui.canvas.CanvasControlSkin
import com.awakekt.awake.ui.canvas.CanvasControlTheme
import com.awakekt.awake.ui.canvas.CanvasDirection
import com.awakekt.awake.ui.canvas.CanvasFollowRank
import com.awakekt.awake.ui.canvas.CanvasFollowRules
import com.awakekt.awake.ui.canvas.CanvasJoystick
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasLook
import com.awakekt.awake.ui.canvas.CanvasPadding
import com.awakekt.awake.ui.canvas.CanvasPlayerLayout
import com.awakekt.awake.ui.canvas.CanvasScaleMode
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.CanvasSweep
import com.awakekt.awake.ui.canvas.CanvasTouchVisibility

/** One canvas binding and a saved form of it with every field away from its default. */
internal class GoldenCase<S : SceneComponent>(val binding: SceneComponentBinding<*, S>, val saved: S)

/**
 * A saved form of each canvas binding, each field set to something other than its default, so a field
 * a binding fails to copy changes what it saves. Every float is a dyadic fraction, so it prints the
 * same on every target.
 */
internal val goldenCases: Map<String, GoldenCase<*>> = mapOf(
    "rect" to GoldenCase(CanvasRectBinding, SceneCanvasRect(CanvasAnchor.BottomRight, 2f, 4f, 64f, 32f, 3, false, true, false, 0.5f, "hp", "jump")),
    "text" to GoldenCase(CanvasTextBinding, SceneCanvasText("Hi", "hud.hi", mapOf("n" to "1"), 18f, "mono", CanvasAnchor.Center, false)),
    "appearance" to GoldenCase(CanvasAppearanceBinding, SceneCanvasAppearance("#ff0000", "#00000080", CanvasStyle(background = "#123456"), CanvasControlSkin())),
    "layout" to GoldenCase(CanvasLayoutBinding, SceneCanvasLayout(CanvasLayout(direction = CanvasDirection.Column, gap = 4f))),
    "cluster" to GoldenCase(CanvasClusterBinding, SceneCanvasCluster(CanvasCluster(radius = 48f))),
    "bind" to GoldenCase(CanvasBindBinding, SceneCanvasBind(CanvasBinding(node = "Player", value = "hp"))),
    "follow" to GoldenCase(CanvasFollowBinding, SceneCanvasFollow("Enemy", SceneVec3(0f, 2f, 0.5f), true, 4f, CanvasFollowRules(maxDistance = 32f))),
    "follow_arrow" to GoldenCase(CanvasFollowArrowBinding, SceneCanvasFollowArrow()),
    "input" to GoldenCase(CanvasInputBinding, SceneCanvasInput(pressable = true, draggable = true, dropTarget = true, wheel = true)),
    "tooltip" to GoldenCase(CanvasTooltipBinding, SceneCanvasTooltip("Tip", mapOf("n" to "2"))),
    "button" to GoldenCase(CanvasButtonBinding, SceneCanvasButton("jump", "dash", CanvasButton(), false)),
    "bar" to GoldenCase(CanvasBarBinding, SceneCanvasBar(0.5f, CanvasBar(thickness = 4f))),
    "sweep" to GoldenCase(CanvasSweepBinding, SceneCanvasSweep(0.25f, CanvasSweep(startAngle = 90f))),
    "stepper" to GoldenCase(CanvasStepperBinding, SceneCanvasStepper(CanvasStepper(value = 2f, min = 1f, repeatDelay = 0.5f, repeatInterval = 0.125f))),
    "joystick" to GoldenCase(CanvasJoystickBinding, SceneCanvasJoystick("move", CanvasJoystick(radius = 40f))),
    "look_area" to GoldenCase(CanvasLookAreaBinding, SceneCanvasLookArea("look", CanvasLook(sensitivity = 2f))),
    "text_field" to GoldenCase(CanvasTextFieldBinding, SceneCanvasTextField("*", 8, TextLengthUnit.CodePoints, "Name", CanvasPadding(2f, 1f))),
    "view" to GoldenCase(CanvasViewBinding, SceneCanvasView("Cam")),
    "image" to GoldenCase(CanvasImageBinding, SceneCanvasImage()),
    "scaler" to GoldenCase(
        CanvasScalerBinding,
        SceneCanvasScaler(CanvasScaleMode.ConstantPixelSize, 640f, 360f, 0.5f, 2f, 192f, 0.5f, 4f, 1.5f),
    ),
    "controls" to GoldenCase(
        CanvasControlsBinding,
        SceneCanvasControls(CanvasTouchVisibility.Always, 48f, CanvasControlTheme(), CanvasPlayerLayout(scale = 1.5f)),
    ),
    "follow_limit" to GoldenCase(CanvasFollowLimitBinding, SceneCanvasFollowLimit("enemies", 4, CanvasFollowRank.Priority)),
    "follow_occlusion" to GoldenCase(CanvasFollowOcclusionBinding, SceneCanvasFollowOcclusion(8, 3, 0)),
)
