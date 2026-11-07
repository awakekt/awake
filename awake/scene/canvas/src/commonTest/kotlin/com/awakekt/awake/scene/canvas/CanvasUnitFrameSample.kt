/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.dp
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasDirection
import com.awakekt.awake.ui.canvas.CanvasLayout
import kotlinx.coroutines.flow.StateFlow

// The reference sample of declarative canvas content: the showcase's Compose unit frame, ported to
// canvas elements. The docs' "Declarative canvas content" section includes it from here.

// --8<-- [start:unit-frame-state]
/** One party member, as the game's state holds it. */
internal data class PartyMember(val id: String, val name: String, val level: Int, val hp: Int, val maxHp: Int, val mp: Int, val maxMp: Int)

/** The party HUD's state: the members in the order they are shown. */
internal data class PartyState(val members: List<PartyMember>, val inCombat: Boolean = false)
// --8<-- [end:unit-frame-state]

// --8<-- [start:unit-frame]

/** A unit frame: name and level over an HP bar and an MP bar, each with its numbers. */
context(_: Composer)
internal fun unitFrame(member: PartyMember) {
    panel(
        id = "frame-${member.id}",
        settings = {
            size(220.dp, 64.dp)
            background = Color(0.08f, 0.08f, 0.1f, 0.85f)
            layout = CanvasLayout(direction = CanvasDirection.Column, gap = 4f, padding = 8f)
        },
    ) {
        text(id = "name-${member.id}") {
            size(204.dp, 16.dp)
            text = "${member.name}  Lv.${member.level}"
            fontSize = 12.sp
        }
        gauge("hp-${member.id}", member.hp, member.maxHp, Color(0.8f, 0.15f, 0.15f, 1f), height = 14f)
        gauge("mp-${member.id}", member.mp, member.maxMp, Color(0.2f, 0.4f, 0.9f, 1f), height = 10f)
    }
}

/** A bar filled to [current] of [max], with the two numbers over it. */
context(_: Composer)
private fun gauge(id: String, current: Int, max: Int, fill: Color, height: Float) {
    val safeMax = max.coerceAtLeast(1)
    panel(id = id, settings = { size(204.dp, height.dp) }) {
        bar(id = "$id.fill") {
            size(204.dp, height.dp)
            offset(0.dp, 0.dp)
            value = (current.toFloat() / safeMax).coerceIn(0f, 1f)
            color = fill
            background = Color(0f, 0f, 0f, 0.5f)
        }
        text(id = "$id.label") {
            size(204.dp, height.dp)
            offset(0.dp, 0.dp)
            text = "$current / $safeMax"
            fontSize = 9.sp
            textAlign = CanvasAnchor.Center
        }
    }
}
// --8<-- [end:unit-frame]

// --8<-- [start:party-hud]

/** The party HUD: a frame per member, keyed so a frame keeps its entities as the party reorders. */
internal fun partyHud(world: World, hudRoot: Entity, party: StateFlow<PartyState>): CanvasContent =
    canvasContent(world, root = hudRoot, state = party) { state ->
        panel(
            id = "party",
            settings = {
                anchor = CanvasAnchor.TopLeft
                size(220.dp, 600.dp)
                layout = CanvasLayout(direction = CanvasDirection.Column, gap = 6f)
            },
        ) {
            state.members.forEach { member -> key(member.id) { unitFrame(member) } }
            if (state.inCombat) text(id = "combat") { textKey = "hud.combat" }
        }
    }
// --8<-- [end:party-hud]
