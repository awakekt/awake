/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.sheet.SliceInsets
import com.awakekt.awake.compose.foundation.skin.BarKey
import com.awakekt.awake.compose.foundation.skin.FrameKey
import com.awakekt.awake.compose.foundation.skin.LocalSkin
import com.awakekt.awake.compose.foundation.skin.SheetRect
import com.awakekt.awake.compose.foundation.skin.Skin
import com.awakekt.awake.compose.foundation.skin.StatesKey
import com.awakekt.awake.compose.foundation.skin.skin
import com.awakekt.awake.compose.foundation.skin.skinBar
import com.awakekt.awake.compose.foundation.skin.skinPart
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.BackdropGlassStyle
import com.awakekt.awake.compose.ui.draw.backdropEffect
import com.awakekt.awake.compose.ui.draw.backdropSource
import com.awakekt.awake.compose.ui.draw.dropShadow
import com.awakekt.awake.compose.ui.draw.rememberBackdropSourceState
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.graphics.shadow.Shadow
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextStyle

/** How a panel is dressed; the layout and content are the same for all four. */
enum class PanelLook { Skinned, Plain, Styled, Glass }

internal const val FRAME_W = 1280
internal const val FRAME_H = 720
internal const val PANEL_W = 400
internal const val PANEL_H = 300
internal const val BUTTONS = 3

private val Window = FrameKey("window")
private val ButtonFace = StatesKey("button")
private val GaugeTrack = FrameKey("gauge.track")
private val GaugeFill = BarKey("gauge.fill")

private val Ink = Color(0.92f, 0.92f, 0.95f, 1f)
private val Fill = Color(0.13f, 0.15f, 0.2f, 1f)
private val GlassFill = Color(0.13f, 0.15f, 0.2f, 0.45f)
private val Accent = Color(0.3f, 0.55f, 0.9f, 1f)
private val Dark = Color(0.2f, 0.22f, 0.3f, 1f)
private val Corner = RoundedCornerShape(8.dp)
private val PanelShadow = Shadow(radius = 12.dp, color = Color(0f, 0f, 0f, 0.5f))
private val ButtonShadow = Shadow(radius = 4.dp, color = Color(0f, 0f, 0f, 0.5f))

/** A 64x64 sheet, generated: every part below is cut from it, so all of them sample one image. */
fun panelSheet(): ImageBitmap {
    val side = 64
    val pixels = ByteArray(side * side * 4)
    for (y in 0 until side) {
        for (x in 0 until side) {
            val shade = (40 + (x * 3 + y * 2) % 120)
            val o = (y * side + x) * 4
            pixels[o] = shade.toByte(); pixels[o + 1] = (shade + 20).toByte(); pixels[o + 2] = (shade + 50).coerceAtMost(255).toByte()
            pixels[o + 3] = 0xFF.toByte()
        }
    }
    return ImageBitmap(side, side, pixels)
}

/** The skin one panel draws from: window, button states, gauge track and fill, all from [sheet]. */
fun panelSkin(sheet: ImageBitmap) = skin(sheet) {
    frame(Window, SheetRect(0, 0, 32, 32), SliceInsets(8), repeatEdges = true)
    states(ButtonFace, SheetRect(0, 48, 64, 16), slice = SliceInsets(2))
    frame(GaugeTrack, SheetRect(0, 32, 32, 8), SliceInsets(2))
    bar(GaugeFill, SheetRect(0, 40, 32, 8), SliceInsets(left = 2, right = 2))
}

/**
 * One panel at [x], [y]: a window frame, a title, a row of three buttons, a gauge and a text block.
 * [look] only changes how those are dressed; the node tree is the same shape for all four.
 */
context(_: Composer)
fun Panel(look: PanelLook, x: Int, y: Int, buttons: InteractionSource) {
    val glass = rememberBackdropSourceState()
    val window = Modifier.offset(x.dp, y.dp).size(PANEL_W.dp, PANEL_H.dp).let { base ->
        when (look) {
            PanelLook.Skinned -> base.skinPart(Window)
            PanelLook.Plain -> base.background(Fill, Corner)
            PanelLook.Styled -> base.dropShadow(Corner, PanelShadow).background(Fill, Corner)
            PanelLook.Glass -> base.dropShadow(Corner, PanelShadow).backdropEffect(glass, BackdropGlassStyle.Regular, Corner)
                .background(GlassFill, Corner)
        }
    }
    Column(window.padding(16.dp)) {
        Text("Inventory", style = TextStyle(color = Ink, size = 20.sp))
        Row(Modifier.padding(top = 12.dp)) {
            repeat(BUTTONS) { i ->
                val base = Modifier.padding(end = 8.dp).size(100.dp, 36.dp)
                val face = when (look) {
                    PanelLook.Skinned -> base.skinPart(ButtonFace, buttons)
                    PanelLook.Plain -> base.background(Dark, Corner)
                    else -> base.dropShadow(Corner, ButtonShadow).background(Dark, Corner)
                }
                Box(face) { Text("Use $i", Modifier.padding(8.dp), style = TextStyle(color = Ink, size = 14.sp)) }
            }
        }
        val track = Modifier.padding(top = 12.dp).size(360.dp, 16.dp)
        Box(if (look == PanelLook.Skinned) track.skinPart(GaugeTrack) else track.background(Dark)) {
            val bar = Modifier.size(216.dp, 16.dp)
            Box(if (look == PanelLook.Skinned) bar.skinBar(GaugeFill, { 1f }) else bar.background(Accent))
        }
        Text("Weight 12 / 20. Gold 340. Two keys.", Modifier.padding(top = 12.dp), style = TextStyle(color = Ink, size = 14.sp))
    }
}

/**
 * The frame the four cases share: a scene behind the panels for the glass to blur, then [panels]
 * panels at [positions], the first drawn last so it is on top.
 */
context(_: Composer)
fun PanelFrame(look: PanelLook, positions: List<Pair<Int, Int>>, skin: Skin?) {
    val buttons = InteractionSource()
    val backdrop = rememberBackdropSourceState()
    CompositionLocalProvider(LocalSkin provides skin) {
        Box(Modifier.size(FRAME_W.dp, FRAME_H.dp).backdropSource(backdrop).background(Color(0.05f, 0.08f, 0.12f, 1f))) {
            for (i in positions.indices.reversed()) Panel(look, positions[i].first, positions[i].second, buttons)
        }
    }
}
