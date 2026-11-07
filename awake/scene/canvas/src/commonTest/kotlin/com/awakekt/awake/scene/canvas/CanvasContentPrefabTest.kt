/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.ui.canvas.CanvasElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Canvas content places a prefab, the format an editor saves, where its state says, and binds the
 * placed copy's parts from the block: the prefab is made once per place and its overrides are
 * written through the typed handles on every pass.
 */
class CanvasContentPrefabTest {
    private val world = World()
    private val hud = world.create().also { world.add(it, Transform()) }
    private val bar = actionBarFromParts()

    private fun under(root: Entity, id: String): Entity = checkNotNull(world.findCanvasElement(root, id)) { "no $id" }

    private fun element(entity: Entity): CanvasElement = checkNotNull(world.get<CanvasElement>(entity))

    @Test
    fun aPlacedPrefabIsItsWholeTreeUnderTheNode() {
        canvasContent(world, hud) { panel(id = "hud") { prefab(bar) } }

        val root = under(hud, "bar")
        assertEquals(under(hud, "hud"), world.get<Transform>(root)?.parent)
        assertEquals(root, world.get<Transform>(under(root, "bar.energy"))?.parent)
        assertEquals(under(root, "bar.slot-2"), world.get<Transform>(under(root, "bar.slot-2.icon"))?.parent)
        assertEquals("hud.bar.title", element(under(root, "bar.title")).textKey)
    }

    @Test
    fun aPlacedPrefabIsMadeOnce() {
        val content = canvasContent(world, hud) { prefab(bar) }
        val energy = under(hud, "bar.energy")
        val made = content.applier.created
        val writes = content.applier.writes

        repeat(3) { content.recompose() }

        assertEquals(energy, under(hud, "bar.energy"))
        assertEquals(made, content.applier.created)
        assertEquals(writes, content.applier.writes)
    }

    @Test
    fun theBlockBindsTheCopysParts() {
        var energy = 0.5f
        var resolved = 0
        val content = canvasContent(world, hud) {
            prefab(bar, parts = { resolved++; ActionBarParts() }) {
                this.energy.value = energy
                slots[1].count.text = "3"
            }
        }
        assertEquals(0.5f, element(under(hud, "bar.energy")).value)

        energy = 0.25f
        content.recompose()

        assertEquals(0.25f, element(under(hud, "bar.energy")).value)
        assertEquals("3", element(under(hud, "bar.slot-1.count")).text)
        assertEquals(1, resolved, "the parts are resolved once per placed copy")
    }

    @Test
    fun eachKeyedCopyBindsItsOwnParts() {
        var members = listOf("ana" to 1f, "bo" to 0.5f)
        val content = canvasContent(world, hud) {
            members.forEach { (id, health) -> key(id) { prefab(bar, parts = { ActionBarParts() }) { energy.value = health } } }
        }
        val roots = content.applier.root.children.map { it.entity }

        members = members.reversed()
        content.recompose()

        val after = content.applier.root.children.map { it.entity }
        assertEquals(roots.reversed(), after, "each copy kept its entities")
        assertEquals(0.5f, element(under(after[0], "bar.energy")).value)
        assertEquals(1f, element(under(after[1], "bar.energy")).value)
    }

    @Test
    fun aRemovedPrefabTakesItsTreeWithIt() {
        var shown = true
        val content = canvasContent(world, hud) { if (shown) prefab(bar) }
        val icon = under(hud, "bar.slot-0.icon")

        shown = false
        content.recompose()

        assertFalse(world.isAlive(icon))
        assertEquals(emptyList(), world.canvasDrawOrder())
        assertTrue(world.isAlive(hud))
    }

    @Test
    fun anotherPrefabAtTheSamePlaceIsMadeAgain() {
        var placed = bar
        val content = canvasContent(world, hud) { prefab(placed) }
        val icon = under(hud, "bar.slot-0.icon")

        placed = canvasPrefab(guid = "c0ffee00-0000-4000-8000-0000000010a3", name = "Badge", id = "badge") { text("Label", id = "badge.label") }
        content.recompose()

        assertFalse(world.isAlive(icon))
        assertEquals("badge", element(under(hud, "badge")).elementId)
        assertTrue(world.findCanvasElement(hud, "badge.label") != null)
    }

    @Test
    fun aPrefabWithAComponentThatIsNotACanvasOneFails() {
        val tagged = ScenePrefab(guid = "c0ffee00-0000-4000-8000-0000000010a4", name = "Tagged", root = SceneNode(name = "Tagged", components = listOf(NotCanvas)))

        assertFailsWith<IllegalArgumentException> { canvasContent(world, hud) { prefab(tagged) } }
    }
}

/** A component a game would add, which is not part of a canvas element. */
private data object NotCanvas : SceneComponent
