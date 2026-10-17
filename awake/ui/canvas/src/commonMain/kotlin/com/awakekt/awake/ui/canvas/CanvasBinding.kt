/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.text.format.NumberFormat
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Game state an element shows without code: a Bar's fill and a Text's or Button's words, read each
 * frame from the components of the node named [node].
 *
 * A field is addressed as `component.field`, such as `health.current`: the component's saved name,
 * then its field as the scene document writes it, with dots for a field inside another. A game's own
 * components are read the same way once their capability registers them.
 *
 * @property node The name of the node whose components are read.
 * @property value A Bar's fill: this field, divided by [max].
 * @property max The field, or a plain number, [value] is a share of; empty when [value] is already
 * a share from 0 to 1.
 * @property text The words, with `{component.field}` replaced by the field's value, such as
 * `HP {health.current}/{health.max}`. A whole number shows exactly, however large, and any other
 * number to one decimal place. A format after a colon sets how a number is written: `{purse.gold:n0}`
 * groups its digits, `1,234,567`, `{health.current:f0}` rounds to a whole number, and
 * `{health.share:p0}` shows a fraction as a percentage; see [NumberFormat] for the full set. A format
 * leaves a field that is not a number as it is.
 * @property textArgs Arguments of a keyed element's text, [CanvasElement.textKey], each read from a
 * field: the argument's name to its `component.field`, such as `"name": "player.name"` or
 * `"count": "inventory.count"`. The field's value goes in as it is, so a plural reads a number field
 * as a number. A field that reads nothing leaves the element's own [CanvasElement.textArgs] value.
 * @property cooldown An action button's [CanvasElement.cooldown]: this field, a share from 0, ready,
 * to 1, just used, such as `dash.cooldown`; divided by [cooldownMax] when that is set, so a field of
 * seconds left works too.
 * @property cooldownMax The field, or a plain number, [cooldown] is a share of, such as
 * `dash.cooldownSeconds`; empty when [cooldown] is already a share.
 * @property charges An action button's [CanvasElement.charges]: this field, a whole number of uses
 * left, such as `weapon.rounds`.
 * @property enabled An action button's [CanvasElement.enabled]: this field, true or a number above 0
 * for enabled, such as `skills.slot1Ready`.
 */
@Serializable
data class CanvasBinding(
    val node: String,
    val value: String = "",
    val max: String = "",
    val text: String = "",
    val textArgs: Map<String, String> = emptyMap(),
    val cooldown: String = "",
    val cooldownMax: String = "",
    val charges: String = "",
    val enabled: String = "",
) {
    /** What is wrong with this binding, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (node.isBlank()) add("node must name a node")
        if (listOf(value, text, cooldown, charges, enabled).all { it.isBlank() } && textArgs.isEmpty()) {
            add("needs a value, a text, textArgs, a cooldown, charges or enabled to show")
        }
        listOf("value" to value, "max" to max, "cooldownMax" to cooldownMax).forEach { (name, path) ->
            if (path.isNotBlank() && path.toFloatOrNull() == null && !FIELD_PATH.matches(path)) {
                add("$name \"$path\" must be component.field or a number")
            }
        }
        for (placeholder in PLACEHOLDER.findAll(text)) {
            val spec = placeholder.groupValues[FORMAT_GROUP]
            if (spec.isNotEmpty() && NumberFormat.parse(spec) == null) {
                add("text format \"$spec\" in ${placeholder.value} must be n, f or p and a digit, such as n0")
            }
        }
        addAll(textArgProblems())
        listOf("cooldown" to cooldown, "charges" to charges, "enabled" to enabled).forEach { (name, path) ->
            if (path.isNotBlank() && !FIELD_PATH.matches(path)) add("$name \"$path\" must be component.field")
        }
    }

    private fun textArgProblems(): List<String> = buildList {
        for ((name, path) in textArgs) {
            if (name.isBlank()) add("textArgs needs a name for \"$path\"")
            if (!FIELD_PATH.matches(path)) add("textArgs \"$name\" must be component.field, not \"$path\"")
        }
    }
}

/**
 * A Bar's fill or a Text's filled-in template, read through a [CanvasResolver]'s raw field text
 * rather than a world: the scene side turns a component's field into that text, so this side never
 * needs to know what a component or a binding is.
 */
fun CanvasResolver.numberField(node: String, path: String): Float? {
    val number = path.toFloatOrNull() ?: field(node, path)?.let { content ->
        content.toFloatOrNull() ?: content.toBooleanStrictOrNull()?.let { if (it) 1f else 0f }
    }
    return number?.takeIf { it.isFinite() }
}

/** [template] with each `{component.field}` or `{component.field:format}` replaced by that field's text on [node]. */
fun CanvasResolver.textField(node: String, template: String): String = PLACEHOLDER.replace(template) { match ->
    val path = match.groupValues[1]
    val content = field(node, path) ?: return@replace match.value
    val spec = match.groupValues[FORMAT_GROUP]
    if (spec.isEmpty()) {
        showField(content)
    } else {
        val format = NumberFormat.parse(spec)
        if (format == null) {
            warnUnknownFormat("$path:$spec", spec)
            match.value
        } else {
            showField(content, format)
        }
    }
}

// A whole number is its own digits, exact at any size: through a Float it would lose its low ones
// from 16,777,217 up. Any other number is shown to one decimal place, or in the format it asked for.
private fun showField(content: String, format: NumberFormat? = null): String {
    val whole = content.toLongOrNull()
    return when {
        format == null -> whole?.toString() ?: content.toFloatOrNull()?.let(::formatNumber) ?: content
        whole != null -> format.format(whole)
        else -> content.toDoubleOrNull()?.let(format::format) ?: content
    }
}

/** To one decimal place, without it when it is 0: `120`, `0.5`, `-1.5`; past a trillion, or not finite, as it is. */
private fun formatNumber(value: Float): String {
    // First: roundToLong refuses NaN.
    if (!value.isFinite() || abs(value) > LARGEST_FORMATTED) return value.toString()
    val tenths = (value * TENTHS).roundToLong()
    val sign = if (tenths < 0) "-" else ""
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "$sign${abs(tenths) / 10}.${abs(tenths) % 10}"
}

private const val TENTHS = 10f
private const val LARGEST_FORMATTED = 1e12f

private val formatLog = Logger("ui-canvas")

// Bad format specs a scene's own `bind.text` templates spell: as many as the scenes write, so it stays small.
private val warnedFormats = HashSet<String>()

private fun warnUnknownFormat(key: String, spec: String) {
    if (warnedFormats.add(key)) {
        formatLog.warn { "A canvas binding reads '$spec', but it is not a number format, which is n, f or p and a digit, such as n0; the text keeps its placeholder" }
    }
}

private val FIELD_PATH = Regex("""[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+""")

// `{component.field}`, or `{component.field:format}`; group 1 is the field and group 2 the format.
private val PLACEHOLDER = Regex("""\{([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+)(?::([^{}]+))?}""")

private const val FORMAT_GROUP = 2
