/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.skin

import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.graphics.FillDirection
import com.awakekt.awake.compose.ui.graphics.drawImageFill
import com.awakekt.awake.compose.ui.graphics.drawImageFillBar
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.node.DrawModifierNode

/**
 * The skin components draw from, provided like a theme: `LocalSkin provides skin`. `null`, the
 * default, means no skin, and a component keeps its token-based look.
 */
val LocalSkin = compositionLocalOf<Skin?> { null }

context(_: Composer)
/**
 * Draws the [key] frame behind the node. Without a skin, without that part, or before the sheet
 * has decoded it draws nothing, so chain the token look before it.
 */
fun Modifier.skinPart(key: FrameKey): Modifier {
    val skin = LocalSkin.current ?: return this
    return this then SkinFrameElement(skin, key)
}

context(_: Composer)
/**
 * Draws the [key] frame for the state [source], [enabled] and [selected] give, behind the node:
 * normal, hovered, pressed or disabled, falling back as [com.awakekt.awake.compose.foundation.sheet.StateSheet.fill]
 * says. Read each draw, so a state change shows with no recomposition. Draws nothing without a
 * skin, without that part, or before the sheet has decoded.
 */
fun Modifier.skinPart(
    key: StatesKey,
    source: InteractionSource,
    enabled: Boolean = true,
    selected: Boolean = false,
): Modifier {
    val skin = LocalSkin.current ?: return this
    return this then SkinStatesElement(skin, key, source, enabled, selected)
}

context(_: Composer)
/**
 * Draws the [key] bar filled to [value] (0..1, read each draw) from the edge [direction] names,
 * with its end caps whole. Draws nothing without a skin, that part, or a decoded sheet.
 */
fun Modifier.skinBar(key: BarKey, value: () -> Float, direction: FillDirection = FillDirection.LeftToRight): Modifier {
    val skin = LocalSkin.current ?: return this
    return this then SkinBarElement(skin, key, value, direction)
}

private class SkinFrameElement(
    private val skin: Skin,
    private val key: FrameKey,
) : ModifierNodeElement<SkinFrameNode>() {
    override fun create(): SkinFrameNode = SkinFrameNode()
    override fun update(node: SkinFrameNode) {
        node.skin = skin
        node.key = key
    }
    override fun toString(): String = "skinPart($key)"
}

private class SkinFrameNode :
    Modifier.Node(),
    DrawModifierNode {
    lateinit var skin: Skin
    lateinit var key: FrameKey
    override fun DrawScope.draw(drawContent: () -> Unit) {
        skin.resolve(key)?.let { drawImageFill(it) }
        drawContent()
    }
    override fun toString(): String = "skinPart($key)"
}

private class SkinStatesElement(
    private val skin: Skin,
    private val key: StatesKey,
    private val source: InteractionSource,
    private val enabled: Boolean,
    private val selected: Boolean,
) : ModifierNodeElement<SkinStatesNode>() {
    override fun create(): SkinStatesNode = SkinStatesNode()
    override fun update(node: SkinStatesNode) {
        node.skin = skin
        node.key = key
        node.source = source
        node.enabled = enabled
        node.selected = selected
    }
    override fun toString(): String = "skinPart($key)"
}

private class SkinStatesNode :
    Modifier.Node(),
    DrawModifierNode {
    lateinit var skin: Skin
    lateinit var key: StatesKey
    lateinit var source: InteractionSource
    var enabled: Boolean = true
    var selected: Boolean = false
    override fun DrawScope.draw(drawContent: () -> Unit) {
        skin.resolve(key)?.fill(source, enabled, selected)?.let { drawImageFill(it) }
        drawContent()
    }
    override fun toString(): String = "skinPart($key)"
}

private class SkinBarElement(
    private val skin: Skin,
    private val key: BarKey,
    private val value: () -> Float,
    private val direction: FillDirection,
) : ModifierNodeElement<SkinBarNode>() {
    override fun create(): SkinBarNode = SkinBarNode()
    override fun update(node: SkinBarNode) {
        node.skin = skin
        node.key = key
        node.value = value
        node.direction = direction
    }
    override fun toString(): String = "skinBar($key)"
}

private class SkinBarNode :
    Modifier.Node(),
    DrawModifierNode {
    lateinit var skin: Skin
    lateinit var key: BarKey
    lateinit var value: () -> Float
    var direction: FillDirection = FillDirection.LeftToRight
    override fun DrawScope.draw(drawContent: () -> Unit) {
        skin.resolve(key)?.let { drawImageFillBar(it, value(), direction) }
        drawContent()
    }
    override fun toString(): String = "skinBar($key)"
}
