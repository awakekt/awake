/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ui.canvas.CanvasBinding
import com.awakekt.awake.ui.canvas.fieldPaths

/**
 * The fields the canvas's shown, bound elements read, polled each frame for a change (#1512): a game
 * writes them in place, `health.current -= 5`, which no store revision or element sees.
 *
 * The list is gathered on frames that compose, with what composing read; each poll then reads each
 * bound component once, and is a change when any field's value differs from the last poll. A
 * formatted placeholder, `{purse.gold:n0}`, is watched through its field's value, not the formatted text.
 */
internal class BoundFields(private val named: CanvasNodeNames, private val reader: CanvasFieldReader) {
    /** The fields of one node's component, read in one pass. */
    private class Group(val node: String, val component: String) {
        val values = ArrayList<FieldValue>()
        var used = false
    }

    private val groups = HashMap<String, Group>()
    private val values = HashMap<String, FieldValue>()

    /** [groups] in use, walked by index: a poll allocates no iterator. */
    private val polled = ArrayList<Group>()

    /** Starts gathering what this frame's elements read. */
    fun begin() {
        for (group in groups.values) {
            group.used = false
            group.values.clear()
        }
    }

    /** Adds the fields [bind] reads. */
    fun add(bind: CanvasBinding) {
        for (path in bind.fieldPaths()) {
            val parsed = reader.path(path) ?: continue
            val group = groups.getOrPut(bind.node + '\u0000' + parsed.component) { Group(bind.node, parsed.component) }
            val value = values.getOrPut(bind.node + '\u0000' + path) { FieldValue(parsed.value.segments) }
            group.used = true
            if (value !in group.values) group.values.add(value)
        }
    }

    /** Ends gathering: drops what no element reads now, and records what each field holds, as composing saw it. */
    fun end() {
        groups.values.removeAll { !it.used }
        polled.clear()
        polled.addAll(groups.values)
        changed()
    }

    /** Whether any watched field's value differs from the previous call. */
    fun changed(): Boolean {
        var changed = false
        for (index in polled.indices) {
            val group = polled[index]
            val values = group.values
            val entity = named[group.node]
            if (entity == null) {
                for (at in values.indices) values[at].clear()
            } else {
                reader.read(entity, group.component, values)
            }
            for (at in values.indices) if (values[at].changed()) changed = true
        }
        return changed
    }

    /** Whether any field is watched. */
    val watching: Boolean get() = polled.isNotEmpty()
}
