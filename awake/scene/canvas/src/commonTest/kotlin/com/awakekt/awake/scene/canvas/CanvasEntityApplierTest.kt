/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.Composition
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.runtime.node
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The [CanvasEntityApplier] turns the runtime's insert, move and remove into canvas entities, as the
 * runtime's own `FakeTree` test drives a tree of nothing: a declaration is an entity under its
 * parent's, a keyed move keeps the entity and changes where it draws, and a pass that declares what
 * the last one did writes nothing.
 */
class CanvasEntityApplierTest {
    private val world = World()
    private val hud = world.create().also { world.add(it, Transform()) }
    private val applier = CanvasEntityApplier(world, hud)
    private val composition = Composition(applier, applier.root)

    private fun pass(content: context(Composer) () -> Unit) {
        composition.reconcile(content)
        applier.finishPass()
    }

    context(_: Composer)
    private fun element(
        id: String,
        kind: CanvasElementKind = CanvasElementKind.Panel,
        width: Float = 10f,
        content: (
            context(Composer)
            () -> Unit
        )? = null,
    ) {
        node(kind, update = { applier.write(it as CanvasContentNode, settings(id, kind, width)) }, content = content)
    }

    private fun settings(id: String, kind: CanvasElementKind, width: Float) =
        CanvasElementScope(id, kind, id).apply { size(Dp(width), Dp(10f)) }.canvasParts()

    private fun entityOf(id: String): Entity = checkNotNull(world.findCanvasElement(hud, id)) { "no element $id" }

    private fun idsInDrawOrder(): List<String> = world.canvasDrawOrder().map { checkNotNull(world.get<CanvasElement>(it)).elementId }

    @Test
    fun anInsertMakesAnEntityUnderItsParent() {
        pass { element("frame") { element("name", CanvasElementKind.Text) } }

        val frame = entityOf("frame")
        val name = entityOf("name")
        assertEquals(hud, world.get<Transform>(frame)?.parent)
        assertEquals(frame, world.get<Transform>(name)?.parent)
        assertEquals(CanvasElementKind.Text, world.get<CanvasElement>(name)?.kind)
        assertEquals(2L, applier.created)
    }

    @Test
    fun anUnchangedPassWritesNothing() {
        val items = listOf("a", "b", "c")
        pass { element("list") { items.forEach { key(it) { element(it) } } } }
        val writes = applier.writes

        repeat(3) { pass { element("list") { items.forEach { key(it) { element(it) } } } } }

        assertEquals(writes, applier.writes, "a pass that declares the same content writes no component")
        assertEquals(4L, applier.created)
    }

    @Test
    fun oneChangedFieldWritesOneComponent() {
        var width = 10f
        val content: context(Composer)
        () -> Unit = { element("bar", CanvasElementKind.Bar, width) }
        pass(content)
        val writes = applier.writes

        width = 20f
        pass(content)

        assertEquals(writes + 1, applier.writes, "only the canvas_rect holds the width")
        assertEquals(20f, world.get<CanvasElement>(entityOf("bar"))?.width)
    }

    @Test
    fun aKeyedMoveKeepsTheEntityAndChangesWhereItDraws() {
        var order = listOf("a", "b", "c")
        val content: context(Composer)
        () -> Unit = { element("list") { order.forEach { key(it) { element(it) } } } }
        pass(content)
        val before = order.associateWith { entityOf(it) }
        assertEquals(listOf("list", "a", "b", "c"), idsInDrawOrder())

        order = listOf("c", "a", "b")
        pass(content)

        assertEquals(listOf("list", "c", "a", "b"), idsInDrawOrder())
        order.forEach { assertEquals(before[it], entityOf(it), "$it kept its entity") }
        assertEquals(4L, applier.created, "nothing was made again")
        assertEquals(0L, applier.destroyed)
    }

    @Test
    fun theCachedOrderSeesAMove() {
        var order = listOf("a", "b")
        val content: context(Composer)
        () -> Unit = { order.forEach { key(it) { element(it) } } }
        pass(content)
        val cache = CanvasItemOrder(world)
        val first = cache.items().map { it.id }

        order = listOf("b", "a")
        pass(content)

        assertEquals(first.reversed(), cache.items().map { it.id })
    }

    @Test
    fun aRemoveDestroysTheEntityAndEverythingUnderIt() {
        var shown = true
        val content: context(Composer)
        () -> Unit = {
            element("keep")
            if (shown) element("window") { element("title", CanvasElementKind.Text) }
        }
        pass(content)
        val window = entityOf("window")
        val title = entityOf("title")

        shown = false
        pass(content)

        assertFalse(world.isAlive(window))
        assertFalse(world.isAlive(title))
        assertTrue(world.isAlive(entityOf("keep")))
        assertTrue(world.isAlive(hud), "the root is the caller's")
        assertEquals(2L, applier.destroyed)
    }

    @Test
    fun keyedItemsKeepTheirRememberedState() {
        var order = listOf("a", "b")
        val seen = mutableMapOf<String, Any>()
        val content: context(Composer)
        () -> Unit = {
            order.forEach { id -> key(id) { element(id) { seen[id] = remember { mutableStateOf(id) } } } }
        }
        pass(content)
        val first = seen.toMap()

        order = listOf("b", "a")
        pass(content)

        assertSame(first["a"], seen["a"])
        assertSame(first["b"], seen["b"])
    }

    @Test
    fun aChangedKindIsANewEntity() {
        var kind = CanvasElementKind.Panel
        val content: context(Composer)
        () -> Unit = { element("slot", kind) }
        pass(content)
        val panel = entityOf("slot")

        kind = CanvasElementKind.Image
        pass(content)

        assertNotSame(panel, entityOf("slot"))
        assertFalse(world.isAlive(panel))
    }

    @Test
    fun disposingLeavesOnlyTheRoot() {
        pass { element("a") { element("b") } }

        composition.dispose()

        assertEquals(emptyList(), world.canvasDrawOrder())
        assertTrue(world.isAlive(hud))
    }
}
