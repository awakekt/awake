/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.icons

import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element

/*
 * SVG -> Awake `ImageVector` Kotlin source.
 *
 * Emits only what the engine's path builder takes: moveTo / lineTo / quadTo / cubicTo / close.
 * Arcs become cubic Beziers (W3C endpoint-to-center parameterization, <=90-degree segments, exact
 * endpoints), so curves survive instead of flattening into lines. Anything the engine cannot draw
 * is refused rather than approximated: `transform`, shapes other than path/circle/rect, a path
 * that both fills and strokes, and even-odd subpaths that cross instead of nest.
 */

/** One normalized absolute command: `M`, `L`, `Q`, `C` or `Z`, with its operands. */
internal data class PathOp(val op: Char, val args: List<Double>)

/** One `<path>`/`<circle>`/`<rect>` after style resolution. */
internal sealed interface SvgShape {
    val d: String

    data class Fill(override val d: String, val fillRule: String) : SvgShape

    data class Stroke(override val d: String, val width: Double, val cap: String, val join: String) : SvgShape
}

internal data class ParsedSvg(val viewportWidth: Double, val viewportHeight: Double, val shapes: List<SvgShape>)

private val NUMBER = Regex("""[+-]?(?:\d*\.\d+|\d+\.?)(?:[eE][+-]?\d+)?""")
private val SEPARATORS = Regex("""[\s,]*""")

/** Cursor over a `d` string. Arc flags are single characters: older data writes `a.75.75 0 011.06.02`. */
private class PathScanner(private val d: String) {
    var pos = 0

    private fun skipSeparators() {
        pos = SEPARATORS.matchAt(d, pos)?.range?.let { it.last + 1 } ?: pos
    }

    fun atEnd(): Boolean {
        skipSeparators()
        return pos >= d.length
    }

    fun peek(): Char? {
        skipSeparators()
        return d.getOrNull(pos)
    }

    fun readCommand(): Char {
        skipSeparators()
        return d[pos++]
    }

    fun readNumber(): Double {
        skipSeparators()
        val match = NUMBER.matchAt(d, pos)
            ?: error("expected number at $pos: ...${d.substring(pos, min(d.length, pos + 20))}")
        pos = match.range.last + 1
        return match.value.toDouble()
    }

    fun readFlag(): Boolean {
        skipSeparators()
        val c = d[pos]
        require(c == '0' || c == '1') { "expected arc flag at $pos: '$c'" }
        pos++
        return c == '1'
    }
}

/** One SVG elliptical arc as cubics, or a line when the arc is degenerate. */
internal fun arcToCubics(
    x1: Double,
    y1: Double,
    rxIn: Double,
    ryIn: Double,
    phiDegrees: Double,
    large: Boolean,
    sweep: Boolean,
    x2: Double,
    y2: Double,
): List<PathOp> {
    if (rxIn == 0.0 || ryIn == 0.0 || (x1 == x2 && y1 == y2)) return listOf(PathOp('L', listOf(x2, y2)))
    val phi = Math.toRadians(phiDegrees)
    val cp = cos(phi)
    val sp = sin(phi)
    val dx2 = (x1 - x2) / 2.0
    val dy2 = (y1 - y2) / 2.0
    val x1p = cp * dx2 + sp * dy2
    val y1p = -sp * dx2 + cp * dy2
    var rx = abs(rxIn)
    var ry = abs(ryIn)
    val lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
    if (lambda > 1) {
        val s = sqrt(lambda)
        rx *= s
        ry *= s
    }
    val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    var co = if (den != 0.0) sqrt(max(num, 0.0) / den) else 0.0
    if (large == sweep) co = -co
    val cxp = co * rx * y1p / ry
    val cyp = -co * ry * x1p / rx
    val cx = cp * cxp - sp * cyp + (x1 + x2) / 2.0
    val cy = sp * cxp + cp * cyp + (y1 + y2) / 2.0

    fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val d = hypot(ux, uy) * hypot(vx, vy)
        val a = acos(((ux * vx + uy * vy) / d).coerceIn(-1.0, 1.0))
        return if (ux * vy - uy * vx < 0) -a else a
    }

    val th1 = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    var dth = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if (!sweep && dth > 0) dth -= 2 * Math.PI else if (sweep && dth < 0) dth += 2 * Math.PI

    val n = max(1, ceil(abs(dth) / (Math.PI / 2.0)).toInt())
    val delta = dth / n
    val k = 4.0 / 3.0 * tan(delta / 4.0)

    fun pointX(t: Double) = cx + rx * cos(t) * cp - ry * sin(t) * sp
    fun pointY(t: Double) = cy + rx * cos(t) * sp + ry * sin(t) * cp
    fun derivX(t: Double) = -rx * sin(t) * cp - ry * cos(t) * sp
    fun derivY(t: Double) = -rx * sin(t) * sp + ry * cos(t) * cp

    return (0 until n).map { i ->
        val t1 = th1 + i * delta
        val t2 = t1 + delta
        val last = i == n - 1
        PathOp(
            'C',
            listOf(
                pointX(t1) + k * derivX(t1),
                pointY(t1) + k * derivY(t1),
                pointX(t2) - k * derivX(t2),
                pointY(t2) - k * derivY(t2),
                // The exact endpoint on the last segment, so the arc closes with no float drift.
                if (last) x2 else pointX(t2),
                if (last) y2 else pointY(t2),
            ),
        )
    }
}

/** A `d` string as absolute `M`/`L`/`Q`/`C`/`Z` commands. */
internal fun parsePathData(d: String): List<PathOp> {
    val s = PathScanner(d)
    val out = mutableListOf<PathOp>()
    var cx = 0.0
    var cy = 0.0
    var sx = 0.0
    var sy = 0.0
    var previous: Char? = null
    var previousCubic: Pair<Double, Double>? = null
    var previousQuad: Pair<Double, Double>? = null

    while (!s.atEnd()) {
        val cmd = if (s.peek()!!.isLetter()) {
            s.readCommand()
        } else {
            // Implicit repetition: a number after M/m continues as L/l, after anything else as itself.
            when (previous) {
                null -> error("path data starts with a number, not a command")
                'M' -> 'L'
                'm' -> 'l'
                else -> previous
            }
        }
        val rel = cmd.isLowerCase()
        fun x(v: Double) = if (rel) cx + v else v
        fun y(v: Double) = if (rel) cy + v else v

        when (cmd.uppercaseChar()) {
            'M' -> {
                val px = x(s.readNumber())
                val py = y(s.readNumber())
                out += PathOp('M', listOf(px, py))
                cx = px
                cy = py
                sx = px
                sy = py
                previousCubic = null
                previousQuad = null
            }
            'L' -> {
                val px = x(s.readNumber())
                val py = y(s.readNumber())
                out += PathOp('L', listOf(px, py))
                cx = px
                cy = py
                previousCubic = null
                previousQuad = null
            }
            'H' -> {
                cx = x(s.readNumber())
                out += PathOp('L', listOf(cx, cy))
                previousCubic = null
                previousQuad = null
            }
            'V' -> {
                cy = y(s.readNumber())
                out += PathOp('L', listOf(cx, cy))
                previousCubic = null
                previousQuad = null
            }
            'C' -> {
                val c1x = x(s.readNumber())
                val c1y = y(s.readNumber())
                val c2x = x(s.readNumber())
                val c2y = y(s.readNumber())
                val px = x(s.readNumber())
                val py = y(s.readNumber())
                out += PathOp('C', listOf(c1x, c1y, c2x, c2y, px, py))
                cx = px
                cy = py
                previousCubic = c2x to c2y
                previousQuad = null
            }
            'S' -> {
                val c2x = x(s.readNumber())
                val c2y = y(s.readNumber())
                val px = x(s.readNumber())
                val py = y(s.readNumber())
                val c1 = previousCubic?.let { (rx, ry) -> 2 * cx - rx to 2 * cy - ry } ?: (cx to cy)
                out += PathOp('C', listOf(c1.first, c1.second, c2x, c2y, px, py))
                cx = px
                cy = py
                previousCubic = c2x to c2y
                previousQuad = null
            }
            'Q' -> {
                val qx = x(s.readNumber())
                val qy = y(s.readNumber())
                val px = x(s.readNumber())
                val py = y(s.readNumber())
                out += PathOp('Q', listOf(qx, qy, px, py))
                cx = px
                cy = py
                previousQuad = qx to qy
                previousCubic = null
            }
            'T' -> {
                val px = x(s.readNumber())
                val py = y(s.readNumber())
                val q = previousQuad?.let { (rx, ry) -> 2 * cx - rx to 2 * cy - ry } ?: (cx to cy)
                out += PathOp('Q', listOf(q.first, q.second, px, py))
                cx = px
                cy = py
                previousQuad = q
                previousCubic = null
            }
            'A' -> {
                val rx = s.readNumber()
                val ry = s.readNumber()
                val rotation = s.readNumber()
                val large = s.readFlag()
                val sweep = s.readFlag()
                val px = x(s.readNumber())
                val py = y(s.readNumber())
                out += arcToCubics(cx, cy, rx, ry, rotation, large, sweep, px, py)
                cx = px
                cy = py
                previousCubic = null
                previousQuad = null
            }
            'Z' -> {
                out += PathOp('Z', emptyList())
                cx = sx
                cy = sy
                previousCubic = null
                previousQuad = null
            }
            else -> error("unsupported path command '$cmd'")
        }
        previous = cmd
    }
    return out
}

internal fun splitSubpaths(commands: List<PathOp>): List<List<PathOp>> {
    val subpaths = mutableListOf<List<PathOp>>()
    var current = mutableListOf<PathOp>()
    for (command in commands) {
        if (command.op == 'M' && current.isNotEmpty()) {
            subpaths += current
            current = mutableListOf()
        }
        current += command
    }
    if (current.isNotEmpty()) subpaths += current
    return subpaths
}

private class Box(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    fun overlaps(o: Box) = minX < o.maxX && o.minX < maxX && minY < o.maxY && o.minY < maxY

    fun contains(o: Box) = minX <= o.minX && minY <= o.minY && o.maxX <= maxX && o.maxY <= maxY
}

private fun List<PathOp>.box(): Box {
    val xs = flatMap { it.args.filterIndexed { i, _ -> i % 2 == 0 } }
    val ys = flatMap { it.args.filterIndexed { i, _ -> i % 2 == 1 } }
    return Box(xs.min(), ys.min(), xs.max(), ys.max())
}

/** Command endpoints as a polygon -- enough to tell nested subpaths from crossing ones. */
private fun List<PathOp>.endpoints(): List<Pair<Double, Double>> =
    filter { it.op != 'Z' }.map { it.args[it.args.size - 2] to it.args[it.args.size - 1] }

private fun pointInPolygon(x: Double, y: Double, polygon: List<Pair<Double, Double>>): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val (xi, yi) = polygon[i]
        val (xj, yj) = polygon[j]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

/**
 * `EvenOdd` when the subpaths nest (holes), null for plain non-zero emission. Crossing even-odd
 * subpaths are refused: the engine's containment-based hole grouping cannot represent them.
 */
internal fun classifyFillRule(subpaths: List<List<PathOp>>, fillRule: String): String? {
    if (fillRule != "evenodd" || subpaths.size < 2) return null
    val polygons = subpaths.map { it.endpoints() }
    val boxes = subpaths.map { it.box() }
    var hasNesting = false
    for (a in boxes.indices) {
        for (b in a + 1 until boxes.size) {
            if (!boxes[a].overlaps(boxes[b])) continue
            val aInB = boxes[b].contains(boxes[a]) &&
                pointInPolygon(polygons[a][0].first, polygons[a][0].second, polygons[b])
            val bInA = boxes[a].contains(boxes[b]) &&
                pointInPolygon(polygons[b][0].first, polygons[b][0].second, polygons[a])
            check(aInB || bInA) {
                "evenodd path with CROSSING (non-nested) subpaths -- the engine's containment-based hole " +
                    "grouping cannot represent this. Rework the source SVG."
            }
            hasNesting = true
        }
    }
    return if (hasNesting) "EvenOdd" else null
}

private val UNSUPPORTED_SHAPES = setOf("ellipse", "line", "polyline", "polygon")

/**
 * The drawable shapes of one SVG document.
 *
 * `fill` and `stroke` inherit: Heroicons' outline tier sets `fill="none" stroke="currentColor"` on
 * the root and leaves each `<path>` carrying only cap and join, so reading the path's own attributes
 * would fill the stroke centreline as a solid polygon. Each attribute resolves through ancestors.
 */
internal fun parseSvg(text: String): ParsedSvg {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        // Published plugin, consumer-supplied files: never resolve external entities or DTDs.
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isExpandEntityReferences = false
    }
    val root = factory.newDocumentBuilder().parse(text.byteInputStream()).documentElement
    val viewBox = root.attr("viewBox")?.takeIf(String::isNotBlank) ?: error("svg has no viewBox")
    val vb = viewBox.replace(',', ' ').trim().split(Regex("\\s+")).map(String::toDouble)
    require(vb[0] == 0.0 && vb[1] == 0.0) { "viewBox with non-zero origin unsupported: $viewBox" }

    val elements = buildList { root.walk(this) }
    for (el in elements) {
        val tag = el.tag
        require(el.attr("transform").isNullOrEmpty()) { "<$tag transform=...> unsupported -- flatten it first" }
        require(tag !in UNSUPPORTED_SHAPES) { "<$tag> unsupported -- convert shapes to paths first" }
    }

    val shapes = elements.filter { it.tag in setOf("path", "circle", "rect") }.mapNotNull { el ->
        val tag = el.tag
        val hasStroke = el.inherited("stroke").orEmpty().ifEmpty { "none" } != "none"
        val hasFill = el.inherited("fill") != "none"
        require(!(hasStroke && hasFill)) {
            "<$tag> has both a fill and a stroke -- one vector path draws one or the other, not both. " +
                "Split the SVG into two <path> elements."
        }
        val d = el.shapePath()
        when {
            hasStroke -> SvgShape.Stroke(
                d = d,
                width = (el.inherited("stroke-width") ?: "1").toDouble(),
                cap = el.inherited("stroke-linecap") ?: "butt",
                join = el.inherited("stroke-linejoin") ?: "miter",
            )
            hasFill -> SvgShape.Fill(d, el.inherited("fill-rule") ?: "nonzero")
            else -> null
        }
    }
    require(shapes.isNotEmpty()) { "no fillable or strokeable <path> elements found" }
    return ParsedSvg(vb[2], vb[3], shapes)
}

private val Element.tag: String get() = localName ?: tagName

private fun Element.attr(name: String): String? = if (hasAttribute(name)) getAttribute(name) else null

private fun Element.walk(into: MutableList<Element>) {
    into += this
    val children = childNodes
    for (i in 0 until children.length) (children.item(i) as? Element)?.walk(into)
}

private fun Element.inherited(name: String): String? {
    var node: Element? = this
    while (node != null) {
        node.attr(name)?.let { return it }
        node = node.parentNode as? Element
    }
    return null
}

/** `<circle>` and `<rect>` become path data, keeping their stroke rather than expanding it. */
private fun Element.shapePath(): String {
    fun number(name: String) = (attr(name) ?: "0").toDouble()
    return when (tag) {
        "path" -> attr("d") ?: error("<path> has no d")
        "circle" -> {
            val x = number("cx")
            val y = number("cy")
            val r = number("r")
            require(r > 0) { "circle radius must be positive" }
            "M${x + r} $y A$r $r 0 1 0 ${x - r} $y A$r $r 0 1 0 ${x + r} $y Z"
        }
        else -> {
            val x = number("x")
            val y = number("y")
            val width = number("width")
            val height = number("height")
            require(width > 0 && height > 0) { "rectangle dimensions must be positive" }
            var rx = (attr("rx") ?: attr("ry") ?: "0").toDouble()
            var ry = (attr("ry") ?: attr("rx") ?: "0").toDouble()
            require(rx >= 0 && ry >= 0) { "rectangle corner radius must be nonnegative" }
            rx = min(rx, width / 2)
            ry = min(ry, height / 2)
            if (rx == 0.0 || ry == 0.0) {
                "M$x $y H${x + width} V${y + height} H$x Z"
            } else {
                "M${x + rx} $y H${x + width - rx} A$rx $ry 0 0 1 ${x + width} ${y + ry} " +
                    "V${y + height - ry} A$rx $ry 0 0 1 ${x + width - rx} ${y + height} " +
                    "H${x + rx} A$rx $ry 0 0 1 $x ${y + height - ry} " +
                    "V${y + ry} A$rx $ry 0 0 1 ${x + rx} $y Z"
            }
        }
    }
}

/** 4 decimals, trailing zeros trimmed, never `-0`, with Kotlin's `f` suffix. */
internal fun fmt(v: Double): String =
    BigDecimal(v).setScale(4, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString().let {
        if (it == "-0") "0f" else "${it}f"
    }

private val CAPS = mapOf("butt" to "Butt", "round" to "Round", "square" to "Square")
private val JOINS = mapOf("miter" to "Miter", "round" to "Round", "bevel" to "Bevel")

/**
 * One icon as a lazy `val` with builder calls, indented [level] steps of four spaces. Lazy so an
 * icon is built on first use rather than every icon in the pack when the object loads.
 */
internal fun emitIcon(name: String, svg: ParsedSvg, dp: Double, source: String, level: Int): String {
    val outer = "    ".repeat(level)
    val i0 = "    ".repeat(level + 1)
    val i1 = "    ".repeat(level + 2)
    val i2 = "    ".repeat(level + 3)
    val lines = mutableListOf<String>()
    if (source.isNotEmpty()) lines += "$outer/** $source, generated from the vendored SVG -- do not hand-edit. */"
    lines += "${outer}val $name: ImageVector by lazy {"
    lines += "${i0}imageVector("
    lines += "${i1}defaultWidth = ${fmt(dp)}.dp,"
    lines += "${i1}defaultHeight = ${fmt(dp)}.dp,"
    lines += "${i1}viewportWidth = ${fmt(svg.viewportWidth)},"
    lines += "${i1}viewportHeight = ${fmt(svg.viewportHeight)},"
    lines += "$i0) {"
    for (shape in svg.shapes) {
        val subpaths = splitSubpaths(parsePathData(shape.d))
        lines += when (shape) {
            is SvgShape.Stroke -> "${i1}path(stroke = DrawStroke(width = ${fmt(shape.width)}.dp, " +
                "cap = UiStrokeCap.${CAPS[shape.cap] ?: "Butt"}, join = UiStrokeJoin.${JOINS[shape.join] ?: "Miter"})) {"
            is SvgShape.Fill -> classifyFillRule(subpaths, shape.fillRule)
                ?.let { "${i1}path(fillRule = UiFillRule.$it) {" } ?: "${i1}path {"
        }
        for (op in subpaths.flatten()) {
            val a = op.args.map(::fmt)
            lines += i2 + when (op.op) {
                'M' -> "moveTo(${a[0]}, ${a[1]})"
                'L' -> "lineTo(${a[0]}, ${a[1]})"
                'Q' -> "quadTo(${a[0]}, ${a[1]}, ${a[2]}, ${a[3]})"
                'C' -> "cubicTo(${a[0]}, ${a[1]}, ${a[2]}, ${a[3]}, ${a[4]}, ${a[5]})"
                else -> "close()"
            }
        }
        lines += "$i1}"
    }
    lines += "$i0}"
    lines += "$outer}"
    return lines.joinToString("\n")
}

private const val LICENSE_HEADER = """/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */"""

/** Imports pulled in only when the icons use them -- an unused import fails detekt. */
private val CONDITIONAL_IMPORTS = mapOf(
    "DrawStroke(" to "com.awakekt.awake.core.graphics2d.DrawStroke",
    "UiFillRule." to "com.awakekt.awake.core.graphics2d.UiFillRule",
    "UiStrokeCap." to "com.awakekt.awake.core.graphics2d.UiStrokeCap",
    "UiStrokeJoin." to "com.awakekt.awake.core.graphics2d.UiStrokeJoin",
    "imageVector(\n" to "com.awakekt.awake.compose.ui.graphics.vector.imageVector",
)

private fun kdoc(lines: List<String>, indent: String): List<String> = when (lines.size) {
    0 -> emptyList()
    1 -> listOf("$indent/** ${lines[0]} */")
    else -> listOf("$indent/**") + lines.map { "$indent *${if (it.isEmpty()) "" else " $it"}" } + "$indent */"
}

/** `arrow-down-tray` -> `arrowDownTray`. */
internal fun camelCase(stem: String): String {
    val parts = stem.split('-', '_').filter(String::isNotEmpty)
    return parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
}

/** An icon pack: where it goes, and the Kotlin file holding every icon in it. */
internal class GeneratedPack(val packageName: String, val fileName: String, val source: String)

/** The Kotlin file for the pack described by [manifestFile], reading the SVGs beside it. */
internal fun generatePack(manifestFile: File): GeneratedPack {
    val manifest = Json.parseToJsonElement(manifestFile.readText()).jsonObject
    val packDirectory = manifestFile.parentFile
    val interfaceName = manifest.string("interface")
    val packageName = manifest.string("package")
    val sourceName = manifest.getValue("source").jsonObject.string("name")
    val tiers = manifest.getValue("tiers").jsonArray.map { it.jsonObject }
    val flat = manifest["flat"]?.jsonPrimitive?.booleanOrNull ?: false
    require(!flat || tiers.size == 1) { "a flat icon manifest must have exactly one tier" }

    val body = mutableListOf<String>()
    for (tier in tiers) {
        val directory = packDirectory.resolve(tier.string("directory"))
        // Every icon takes its tier's native size: `defaultWidth` is per-tier data, not per-glyph.
        val dp = tier.getValue("dp").jsonPrimitive.double
        if (!flat) {
            body += kdoc(tier.stringList("kdoc"), "    ")
            body += "    object ${tier.string("object")} : $interfaceName {"
        }
        val icons = directory.listFiles { file -> file.name.endsWith(".svg") }.orEmpty().sortedBy { it.name }
        require(icons.isNotEmpty()) { "no .svg files in $directory" }
        icons.forEachIndexed { index, file ->
            val stem = file.name.removeSuffix(".svg")
            val svg = runCatching { parseSvg(file.readText()) }
                .getOrElse { throw IllegalArgumentException("${file.path}: ${it.message}", it) }
            if (index > 0) body += ""
            body += emitIcon(
                name = camelCase(stem),
                svg = svg,
                dp = dp,
                source = "$sourceName `$stem` (${tier.string("label")})",
                level = if (flat) 1 else 2,
            )
        }
        if (!flat) body += "    }"
        body += ""
    }
    val text = body.joinToString("\n")

    val imports = (
        setOf("com.awakekt.awake.compose.ui.graphics.vector.ImageVector", "com.awakekt.awake.core.math2d.dp") +
            CONDITIONAL_IMPORTS.filterKeys { it in text }.values
        ).sorted()
    val visibility = manifest["visibility"]?.jsonPrimitive?.content?.let { "$it " }.orEmpty()
    val head = listOf(LICENSE_HEADER, "package $packageName", "") +
        imports.map { "import $it" } +
        "" +
        kdoc(manifest.stringList("fileKdoc"), "") +
        "$visibility${if (flat) "object" else "sealed interface"} $interfaceName {"
    val source = (head + text).joinToString("\n").trimEnd('\n') + "\n}\n"
    return GeneratedPack(packageName, interfaceName, source)
}

private fun JsonObject.string(name: String): String =
    this[name]?.jsonPrimitive?.content ?: error("manifest has no \"$name\" field")

private fun JsonObject.stringList(name: String): List<String> =
    this[name]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
