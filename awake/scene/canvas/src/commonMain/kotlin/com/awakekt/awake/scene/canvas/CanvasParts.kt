/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

/**
 * Where a [CanvasParts] sits in its widget: the id its parts' ids start with and, for one item of a
 * [CanvasParts.list], its position there.
 *
 * @property id The group's own id, such as `bar.slot-3`: a part named `icon` in it is `bar.slot-3.icon`.
 * @property index The group's position in the list that made it, from 0, or -1 for a group that is
 * not a list item.
 */
class CanvasPartScope internal constructor(val id: String, val index: Int)

/**
 * The elements of a canvas prefab that code builds and binds, declared once as typed handles: the
 * one place their ids are composed. A widget is a [CanvasWidgetParts]; a group of parts inside it,
 * such as a slot, is a [CanvasParts] made by [group] or, repeated, by [list].
 *
 * ```kotlin
 * class SlotParts(scope: CanvasPartScope) : CanvasParts(scope) {
 *     val button = button()             // the slot itself: "bar.slot-3"
 *     val icon = image("icon")          // "bar.slot-3.icon"
 *     val cooldown = sweep("cooldown")  // "bar.slot-3.cooldown"
 * }
 * class BarParts(slotCount: Int) : CanvasWidgetParts("bar") {
 *     val frame = panel()
 *     val slots = list("slot", slotCount) { SlotParts(it) }
 * }
 * ```
 *
 * The same declaration builds the prefab, as [canvasPrefab] and the builder's functions take the
 * handles, and binds a placed copy: one instance of the declaration per copy, [resolve]d against
 * that copy's root, then written through each handle's typed fields. The prefab saves plain string
 * ids, so editors, themes and overrides see what they always have.
 *
 * Each kind's function takes the part's name, composed onto the group's id with a `.`, or no name
 * for the group's own element, whose id is the group's. A part whose prefab already saves another
 * id, written before the declaration, keeps it with `pinnedId`.
 *
 * @param scope Where the group sits: a [CanvasWidgetParts]'s own, or the one [group] or [list]
 * hands its factory.
 */
@Suppress("TooManyFunctions") // One function for each element kind.
abstract class CanvasParts(scope: CanvasPartScope) {
    /** The group's own id, which its parts' ids start with. */
    val id: String = scope.id

    /** The group's position in the [list] that made it, from 0, or -1 when it is not a list item. */
    val index: Int = scope.index

    private val parts = mutableListOf<CanvasPart>()
    private val groups = mutableListOf<CanvasParts>()

    /** Declares a Text part; see [CanvasParts] for [name] and [pinnedId]. */
    protected fun text(name: String = "", pinnedId: String? = null): CanvasTextPart = declare(CanvasTextPart(idOf(name, pinnedId)))

    /** Declares a Panel part; see [CanvasParts] for [name] and [pinnedId]. */
    protected fun panel(name: String = "", pinnedId: String? = null): CanvasPanelPart = declare(CanvasPanelPart(idOf(name, pinnedId)))

    /** Declares an Image part; see [CanvasParts] for [name] and [pinnedId]. */
    protected fun image(name: String = "", pinnedId: String? = null): CanvasImagePart = declare(CanvasImagePart(idOf(name, pinnedId)))

    /** Declares a View part; see [CanvasParts] for [name] and [pinnedId]. */
    protected fun view(name: String = "", pinnedId: String? = null): CanvasViewPart = declare(CanvasViewPart(idOf(name, pinnedId)))

    /** Declares a Bar part; see [CanvasParts] for [name] and [pinnedId]. */
    protected fun bar(name: String = "", pinnedId: String? = null): CanvasBarPart = declare(CanvasBarPart(idOf(name, pinnedId)))

    /** Declares a Sweep part; see [CanvasParts] for [name] and [pinnedId]. */
    protected fun sweep(name: String = "", pinnedId: String? = null): CanvasSweepPart = declare(CanvasSweepPart(idOf(name, pinnedId)))

    /** Declares a Button part; see [CanvasParts] for [name] and [pinnedId]. */
    protected fun button(name: String = "", pinnedId: String? = null): CanvasButtonPart = declare(CanvasButtonPart(idOf(name, pinnedId)))

    /** Declares a TextField part; see [CanvasParts] for [name] and [pinnedId]. */
    protected fun textField(name: String = "", pinnedId: String? = null): CanvasTextFieldPart = declare(CanvasTextFieldPart(idOf(name, pinnedId)))

    /** Declares a Joystick part; see [CanvasParts] for [name] and [pinnedId]. */
    protected fun joystick(name: String = "", pinnedId: String? = null): CanvasJoystickPart = declare(CanvasJoystickPart(idOf(name, pinnedId)))

    /** Declares a LookArea part; see [CanvasParts] for [name] and [pinnedId]. */
    protected fun lookArea(name: String = "", pinnedId: String? = null): CanvasLookAreaPart = declare(CanvasLookAreaPart(idOf(name, pinnedId)))

    /** Declares a Stepper part; see [CanvasParts] for [name] and [pinnedId]. */
    protected fun stepper(name: String = "", pinnedId: String? = null): CanvasStepperPart = declare(CanvasStepperPart(idOf(name, pinnedId)))

    /**
     * Declares a group of parts named [name], whose ids start with `<this id>.<name>`.
     *
     * @param factory Makes the group from the scope it is handed.
     * @return The group.
     */
    protected fun <T : CanvasParts> group(name: String, factory: (CanvasPartScope) -> T): T =
        factory(CanvasPartScope(childId(name), -1)).also { groups += it }

    /**
     * Declares [size] groups of parts named [name], one for each item of a repeated part such as a
     * bar's slots: item `i`'s ids start with `<this id>.<name>-<i>`, from 0.
     *
     * @param factory Makes item [CanvasPartScope.index] from the scope it is handed.
     * @return The items, in order.
     */
    protected fun <T : CanvasParts> list(name: String, size: Int, factory: (CanvasPartScope) -> T): CanvasPartList<T> {
        require(size >= 0) { "a list of canvas parts cannot have $size items" }
        val items = List(size) { index -> factory(CanvasPartScope(childId("$name-$index"), index)) }
        groups += items
        return CanvasPartList(items)
    }

    /** Adds every part of this group and the groups in it to [into], depth first. */
    internal fun collectParts(into: MutableList<CanvasPart>) {
        into += parts
        groups.forEach { it.collectParts(into) }
    }

    private fun <P : CanvasPart> declare(part: P): P = part.also { parts += it }

    private fun idOf(name: String, pinnedId: String?): String {
        val partId = pinnedId ?: if (name.isEmpty()) id else childId(name)
        require(isCanvasElementId(partId)) { "canvas part id \"$partId\" $ELEMENT_ID_RULE" }
        return partId
    }

    private fun childId(name: String): String {
        require(name.isNotEmpty() && '.' !in name) { "canvas part name \"$name\" must be non-empty and hold no '.'" }
        return "$id.$name"
    }
}

/**
 * A widget's parts: the [CanvasParts] at the top of a prefab, whose [id] every part's id starts
 * with, such as `bar` for `bar.slot-3.icon`. Declare one subclass for each widget, and make one
 * instance for building its prefab and one for each placed copy code binds.
 *
 * @param id The widget's id: letters, digits, `_`, `.` and `-`, starting with a letter, digit or `_`.
 */
abstract class CanvasWidgetParts(id: String) : CanvasParts(widgetScope(id))

private fun widgetScope(id: String): CanvasPartScope {
    require(isCanvasElementId(id)) { "canvas widget id \"$id\" $ELEMENT_ID_RULE" }
    return CanvasPartScope(id, -1)
}

/**
 * The items a [CanvasParts.list] declared, in order, as a read-only list. Read them by index in code
 * that runs every frame, `for (i in slots.indices)`, which builds no iterator.
 *
 * @param T The type of each item's parts.
 */
class CanvasPartList<T : CanvasParts> internal constructor(private val items: List<T>) : AbstractList<T>() {
    override val size: Int get() = items.size

    override fun get(index: Int): T = items[index]
}
