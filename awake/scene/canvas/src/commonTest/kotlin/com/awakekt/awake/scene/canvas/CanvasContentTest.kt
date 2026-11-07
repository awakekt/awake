/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneValidationException
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [canvasContent] keeps canvas entities in step with the state its block is a function of: a state
 * change adds, removes and reorders elements on the next frame, and changes only the settings that
 * changed; a frame with no change writes nothing.
 */
class CanvasContentTest {
    private val world = World()
    private val hud = world.create().also { world.add(it, Transform()) }

    private class Member(val id: String, val health: Float)

    private class Party(val members: List<Member>, val inCombat: Boolean)

    private val party = MutableStateFlow(Party(listOf(Member("ana", 1f), Member("bo", 0.5f)), inCombat = false))
    private val system = CanvasContentSystem()

    private fun frame() = system.update(world, 0f)

    context(_: Composer)
    private fun unitFrame(member: Member) {
        panel(id = "frame-${member.id}", settings = { size(Dp(120f), Dp(32f)) }) {
            text(id = "name-${member.id}") { text = member.id }
            bar(id = "health-${member.id}") { value = member.health }
        }
    }

    private val content: context(Composer)
    (Party) -> Unit = { party ->
        panel(id = "party") {
            party.members.forEach { member -> key(member.id) { unitFrame(member) } }
            if (party.inCombat) text(id = "combat") { textKey = "hud.combat" }
        }
    }

    private fun ids(): List<String> = world.canvasDrawOrder().map { checkNotNull(world.get<CanvasElement>(it)).elementId }

    private fun element(id: String): CanvasElement = checkNotNull(world.findCanvasElement(hud, id)?.let { world.get<CanvasElement>(it) })

    @Test
    fun theBlockBuildsElementsInDeclaredOrder() {
        canvasContent(world, hud, party, content)

        assertEquals(listOf("party", "frame-ana", "name-ana", "health-ana", "frame-bo", "name-bo", "health-bo"), ids())
        assertEquals("ana", element("name-ana").text)
        assertEquals(0.5f, element("health-bo").value)
        assertEquals(CanvasElementKind.Bar, element("health-bo").kind)
    }

    @Test
    fun aStateChangeAddsAndRemovesElements() {
        val hudContent = canvasContent(world, hud, party, content)

        party.value = Party(listOf(Member("ana", 1f)), inCombat = true)
        frame()

        assertEquals(listOf("party", "frame-ana", "name-ana", "health-ana", "combat"), ids())
        assertEquals(null, world.findCanvasElement(hud, "frame-bo"))
    }

    @Test
    fun aStateChangeReordersWithoutRemaking() {
        val hudContent = canvasContent(world, hud, party, content)
        val made = hudContent.applier.created

        party.value = Party(party.value.members.reversed(), inCombat = false)
        frame()

        assertEquals(listOf("party", "frame-bo", "name-bo", "health-bo", "frame-ana", "name-ana", "health-ana"), ids())
        assertEquals(made, hudContent.applier.created)
        assertEquals(0L, hudContent.applier.destroyed)
    }

    @Test
    fun aChangedValueWritesOnlyItsComponent() {
        val hudContent = canvasContent(world, hud, party, content)
        val writes = hudContent.applier.writes

        party.value = Party(listOf(Member("ana", 0.25f), Member("bo", 0.5f)), inCombat = false)
        frame()

        assertEquals(0.25f, element("health-ana").value)
        assertEquals(writes + 1, hudContent.applier.writes, "one bar's fill is one component")
    }

    @Test
    fun framesWithNoNewStateDoNothing() {
        val hudContent = canvasContent(world, hud, party, content)
        val writes = hudContent.applier.writes
        var passes = 0
        val counted = canvasContent(world, hud, party) { passes++ }

        repeat(10) { frame() }

        assertEquals(writes, hudContent.applier.writes)
        assertEquals(1, passes, "only the first build ran the block")
        counted.dispose()
    }

    @Test
    fun recomposingUnchangedStateWritesNothing() {
        val hudContent = canvasContent(world, hud, party, content)
        val writes = hudContent.applier.writes

        repeat(10) { hudContent.recompose() }

        assertEquals(writes, hudContent.applier.writes)
    }

    @Test
    fun aDisposedBlockNoLongerFollowsItsState() {
        val hudContent = canvasContent(world, hud, party, content)
        hudContent.dispose()

        party.value = Party(listOf(Member("cy", 1f)), inCombat = true)
        frame()

        assertEquals(emptyList(), ids())
    }

    @Test
    fun aHostDrivenBlockRecomposesWhenAsked() {
        var label = "one"
        val hudContent = canvasContent(world, hud) { text(id = "label") { text = label } }

        label = "two"
        frame()
        assertEquals("one", element("label").text, "nothing runs it but its host")
        hudContent.recompose()

        assertEquals("two", element("label").text)
    }

    @Test
    fun disposeLeavesTheRoot() {
        val hudContent = canvasContent(world, hud, party, content)

        hudContent.dispose()

        assertEquals(emptyList(), ids())
        assertTrue(world.isAlive(hud))
    }

    @Test
    fun settingsDoNotDeclareChildren() {
        assertFailsWith<IllegalArgumentException> {
            canvasContent(world, hud) { panel(id = "window", settings = { text("Title") }) }
        }
    }

    @Test
    fun aSettingTheKindDoesNotUseFails() {
        assertFailsWith<SceneValidationException> {
            canvasContent(world, hud) { panel(id = "frame") { text(id = "label") { camera = "Main" } } }
        }
        assertFalse(world.canvasDrawOrder().any { world.get<CanvasElement>(it)?.elementId == "label" })
    }
}
