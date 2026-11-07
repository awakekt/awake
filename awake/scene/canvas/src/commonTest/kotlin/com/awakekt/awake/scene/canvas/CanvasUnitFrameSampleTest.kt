/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The measurements the docs quote for the party HUD sample: what declarative canvas content costs
 * on a frame where nothing changed, when one value changes, and when a long keyed list reorders.
 */
class CanvasUnitFrameSampleTest {
    private val world = World()
    private val hud = world.create().also { world.add(it, Transform()) }
    private val system = CanvasContentSystem()

    private fun member(index: Int) = PartyMember("m$index", "Member $index", level = 10 + index % 50, hp = 80, maxHp = 100, mp = 30, maxMp = 50)

    private fun frame() = system.update(world, FRAME_SECONDS)

    private fun element(id: String): CanvasElement = checkNotNull(world.get<CanvasElement>(checkNotNull(world.findCanvasElement(hud, id))))

    @Test
    fun anUnchangedFrameWritesNothing() {
        val party = MutableStateFlow(PartyState(List(4, ::member)))
        val content = partyHud(world, hud, party)
        val made = content.applier.created
        val writes = content.applier.writes

        repeat(FRAMES) { frame() }

        assertEquals(0L, content.applier.writes - writes, "entity writes over $FRAMES unchanged frames")
        assertEquals(made, content.applier.created)
    }

    @Test
    fun oneHpChangeWritesTheBarAndItsLabel() {
        val party = MutableStateFlow(PartyState(List(4, ::member)))
        val content = partyHud(world, hud, party)
        val writes = content.applier.writes

        party.value = party.value.copy(members = party.value.members.mapIndexed { i, m -> if (i == 2) m.copy(hp = 40) else m })
        frame()

        assertEquals(2L, content.applier.writes - writes, "the fill's bar widget and the label's canvas_text")
        assertEquals(0.4f, element("hp-m2.fill").value)
        assertEquals("40 / 100", element("hp-m2.label").text)
    }

    @Test
    fun twoHundredKeyedFramesReorderWithoutRecreating() {
        val party = MutableStateFlow(PartyState(List(LONG_LIST, ::member)))
        val content = partyHud(world, hud, party)
        val made = content.applier.created
        val frameEntity = world.findCanvasElement(hud, "frame-m0")
        val writes = content.applier.writes

        party.value = PartyState(party.value.members.reversed())
        frame()

        assertEquals(made, content.applier.created, "no entity made again")
        assertEquals(0L, content.applier.destroyed, "no entity destroyed")
        assertEquals(LONG_LIST.toLong(), content.applier.writes - writes, "one sibling place per frame moved, no component")
        assertEquals(frameEntity, world.findCanvasElement(hud, "frame-m0"))
        val order = world.canvasDrawOrder().mapNotNull { world.get<CanvasElement>(it)?.elementId?.takeIf { id -> id.startsWith("frame-") } }
        assertEquals((LONG_LIST - 1 downTo 0).map { "frame-m$it" }, order)
    }

    @Test
    fun theHudDraws() {
        partyHud(world, hud, MutableStateFlow(PartyState(List(2, ::member), inCombat = true)))

        ComposeHost().frame(FrameInput(WIDTH, HEIGHT)) { SceneCanvas(world) }

        assertEquals(220f, element("frame-m1").screenBounds?.width)
    }

    private companion object {
        const val FRAMES = 60
        const val LONG_LIST = 200
        const val FRAME_SECONDS = 1f / 60f
        const val WIDTH = 480
        const val HEIGHT = 640
    }
}
