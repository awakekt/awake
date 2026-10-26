/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity.textclip

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.testing.toBufferedImage
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clipToBounds
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasCurve
import com.awakekt.awake.ui.canvas.CanvasCurvePoint
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasFollowRules
import com.awakekt.awake.ui.canvas.CanvasItem
import com.awakekt.awake.ui.canvas.CanvasOverlay
import com.awakekt.awake.ui.canvas.CanvasPadding
import com.awakekt.awake.ui.canvas.CanvasProjection
import com.awakekt.awake.ui.canvas.CanvasResolver
import com.awakekt.awake.ui.shadcn.ShadcnTheme
import com.awakekt.awake.ui.shadcn.ShadcnThemeValues
import com.awakekt.awake.ui.shadcn.components.ShadcnSidebar
import com.awakekt.awake.ui.shadcn.components.ShadcnSidebarMenu
import com.awakekt.awake.ui.shadcn.components.ShadcnSidebarMenuItem
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Which rectangles the sheets outline in green: the clips a frame pushed, its layer nodes, or its glyph quads. */
internal enum class Outline { Clips, Layers, Quads }

/** A group of cases, one sheet: [id] names the sheet file, [title] heads it. */
internal class CaseGroup(val id: String, val title: String)

/** One text-clipping case: [capture] draws it. [id] is its comparison key across commits. */
internal class TextClipCase(
    val id: String,
    val group: CaseGroup,
    val title: String,
    val note: String,
    val outline: Outline,
    val capture: () -> Captured,
)

/**
 * Every case, drawn black on white by the CPU rasterizer the UI snapshot tests use. They are the
 * shapes behind #1053: ink that reaches past its text box (a leading `j`'s tail, an `Å`'s ring,
 * the distance-field fringe of a loaded font) cut off by a field's clip, a layer's target, a
 * sidebar's padding box or a canvas element's box, and a font scale laid out but not painted.
 */
internal class TextClipCases(private val fontsDir: File) {
    private val black = Color(0f, 0f, 0f, 1f)
    private val white = Color(1f, 1f, 1f, 1f)
    private val theme = ShadcnThemeValues(ShadcnTheme)

    private val roboto: UiFont by lazy {
        UiFonts.load(File(fontsDir, FONT_FILE).readBytes(), FONT_FILE, UiFonts.DEFAULT_CHARS + EXTRA_CHARS)
    }

    fun all(): List<TextClipCase> = fieldCases() + layerCases() + sidebarCases() + fontScaleCases() + sdfCases() + canvasCases()

    // ---------------------------------------------------------------- helpers

    private fun frame(density: Float, fontScale: Float, w: Int, h: Int, content: context(Composer) () -> Unit): FrameOutput =
        ComposeHost(density = density, fontScale = fontScale).frame(FrameInput(w, h), content)

    private fun paint(prims: List<UiDrawPrimitive>, w: Int, h: Int, layers: List<GraphicsLayerFrame> = emptyList()): BufferedImage =
        prims.rasterizeToPixelMap(w, h, background = white, font = UiFonts.default(), layers = layers).toBufferedImage()

    private fun withoutClips(p: List<UiDrawPrimitive>) = p.filterNot {
        it is UiDrawPrimitive.ClipPush || it is UiDrawPrimitive.ClipPop || it is UiDrawPrimitive.ClipPathPush
    }

    private fun clipRects(p: List<UiDrawPrimitive>) =
        p.filterIsInstance<UiDrawPrimitive.ClipPush>().map { Rect(it.rect.x, it.rect.y, it.rect.width, it.rect.height) }

    private fun quadRects(p: List<UiDrawPrimitive>) = p.filterIsInstance<UiDrawPrimitive.Glyph>().map { Rect(it.x, it.y, it.w, it.h) }

    private fun layerRects(l: List<GraphicsLayerFrame>) = l.map { Rect(it.x, it.y, it.width.toFloat(), it.height.toFloat()) }

    private fun num(v: Float) = if (v % 1f == 0f) v.toInt().toString() else v.toString()

    private fun px(v: Float, scale: Float) = (v * scale).roundToInt()

    // ---------------------------------------------------------------- BasicTextField

    private fun fieldCases() = buildList {
        for ((text, tag) in listOf("Åjump" to "A", "jump" to "j")) {
            for (size in listOf(14, 96)) {
                for (density in listOf(1f, 1.5f)) {
                    add(
                        TextClipCase(
                            "field-$tag-${size}sp-d${num(density)}",
                            FIELD,
                            "\"$text\" $size sp, density ${num(density)}",
                            "loaded Roboto TTF, singleLine, padded 16+ dp around the field",
                            Outline.Clips,
                        ) {
                            val pad = max(FIELD_MIN_PAD, size / 2)
                            val w = px((size * text.length + 2 * pad + FIELD_MARGIN).toFloat(), density)
                            val h = px((size * 2 + 2 * pad + FIELD_MARGIN).toFloat(), density)
                            val style = TextStyle(color = black, size = size.sp, font = roboto)
                            val prims = frame(density, 1f, w, h) {
                                Box(Modifier.padding(FIELD_OUTER.dp)) {
                                    BasicTextField(TextFieldState(text, cursor = 0), Modifier.padding(pad.dp), style = style, singleLine = true)
                                }
                            }.primitives
                            Captured(paint(prims, w, h), paint(withoutClips(prims), w, h), clips = clipRects(prims))
                        },
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- graphicsLayer

    private fun layerCases() = buildList {
        for (tight in listOf(false, true)) {
            for (size in listOf(18, 32)) {
                val line = if (tight) "tight" else "normal"
                add(
                    TextClipCase(
                        "layer-${size}sp-$line",
                        LAYER,
                        "\"$LAYER_TEXT\" $size sp, $line line",
                        if (tight) "lineHeight = 1 em" else "the font's own line height",
                        Outline.Layers,
                    ) {
                        val style = TextStyle(color = black, size = size.sp, lineHeight = if (tight) size.sp else null)
                        val w = size * LAYER_W_EM + LAYER_MARGIN
                        val h = size * 3 + LAYER_MARGIN
                        val plain = frame(1f, 1f, w, h) { Box(Modifier.padding(LAYER_PAD.dp)) { Box { Text(LAYER_TEXT, style = style) } } }
                        val layered = frame(1f, 1f, w, h) {
                            Box(Modifier.padding(LAYER_PAD.dp)) { Box(Modifier.graphicsLayer(alpha = ALMOST_OPAQUE)) { Text(LAYER_TEXT, style = style) } }
                        }
                        Captured(paint(layered.primitives, w, h, layered.graphicsLayers), paint(plain.primitives, w, h), layers = layerRects(layered.graphicsLayers))
                    },
                )
            }
        }
    }

    // ---------------------------------------------------------------- ShadcnSidebar

    /** Glyphs alone in black on white, so the panel's own fill does not count as ink. */
    private fun sidebarCase(id: String, title: String, note: String, size: FrameSize, content: context(Composer) () -> Unit) =
        TextClipCase(id, SIDEBAR, title, note, Outline.Clips) {
            val (density, w, h) = size
            val prims = frame(density, 1f, w, h, content).primitives
            val glyphs = prims.filter {
                it is UiDrawPrimitive.Glyph || it is UiDrawPrimitive.ClipPush || it is UiDrawPrimitive.ClipPop || it is UiDrawPrimitive.ClipPathPush
            }.map { if (it is UiDrawPrimitive.Glyph) it.copy(color = black) else it }
            Captured(paint(glyphs, w, h), paint(glyphs.filterIsInstance<UiDrawPrimitive.Glyph>(), w, h), clips = clipRects(prims))
        }

    private fun sidebarCases() = buildList {
        add(
            sidebarCase("sidebar-showcase-d2", "Sidebar \"Acme Inc\", density 2", "the showcase page's sidebar", FrameSize(2f, px(360f, 2f), px(440f, 2f))) {
                provideShadcnTheme(theme) {
                    Box(Modifier.padding(SIDEBAR_PAD.dp)) {
                        ShadcnSidebar(
                            modifier = Modifier.width(SIDEBAR_W.dp).height(SIDEBAR_H.dp),
                            header = { ShadcnText("Acme Inc") },
                            footer = { ShadcnText("shadcn") },
                        ) {
                            ShadcnSidebarMenu {
                                listOf("Overview", "Analytics", "Projects", "Settings").forEachIndexed { i, label ->
                                    ShadcnSidebarMenuItem(label = label, active = i == 0, onClick = {})
                                }
                            }
                        }
                    }
                }
            },
        )
        for ((size, density) in listOf(14 to 1.5f, 96 to 1f)) {
            val text = "jÅump"
            add(
                sidebarCase(
                    "sidebar-ttf-${size}sp-d${num(density)}",
                    "Sidebar \"$text\" $size sp, density ${num(density)}",
                    "loaded Roboto TTF header",
                    FrameSize(density, px((size * text.length + SIDEBAR_MARGIN).toFloat(), density), px((size * 3 + SIDEBAR_MARGIN).toFloat(), density)),
                ) {
                    provideShadcnTheme(theme) {
                        Box(Modifier.padding(SIDEBAR_PAD.dp)) {
                            ShadcnSidebar(header = { Text(text, style = TextStyle(color = black, size = size.sp, font = roboto)) }) {}
                        }
                    }
                },
            )
        }
    }

    // ---------------------------------------------------------------- font scale

    private fun fontScaleCases() = listOf(FONT_SCALE_SMALL, FONT_SCALE_LARGE).map { fs ->
        TextClipCase(
            "fontscale-${num(fs)}",
            FONT_SCALE,
            "font scale ${num(fs)}",
            "clipped to its 160 dp box; reference: 14 x ${num(fs)} sp at scale 1",
            Outline.Clips,
        ) {
            val w = FS_W
            val h = FS_H
            val render = frame(1f, fs, w, h) {
                Box(Modifier.padding(FS_PAD.dp)) { Box(Modifier.width(FS_BOX.dp)) { Text(PARAGRAPH, Modifier.clipToBounds(), style = TextStyle(color = black, size = 14.sp)) } }
            }
            val ref = frame(1f, 1f, w, h) {
                Box(Modifier.padding(FS_PAD.dp)) { Box(Modifier.width(FS_BOX.dp)) { Text(PARAGRAPH, style = TextStyle(color = black, size = (14 * fs).sp)) } }
            }
            Captured(paint(render.primitives, w, h), paint(ref.primitives, w, h), clips = clipRects(render.primitives))
        }
    }

    // ---------------------------------------------------------------- distance-field edge

    private fun sdfCases() = buildList {
        for (size in listOf(28, 32, 40, 48)) {
            for (ch in "Wof&Qg") {
                add(
                    TextClipCase(
                        "sdf-${if (ch == '&') "amp" else ch.toString()}-${size}sp",
                        SDF,
                        "\"$ch\" $size sp",
                        "ink on the glyph quad's outer ring; one glyph a frame",
                        Outline.Quads,
                    ) {
                        val w = (size * SDF_W_EM).roundToInt() + SDF_MARGIN
                        val h = (size * SDF_H_EM).roundToInt() + SDF_MARGIN
                        val out = frame(1f, 1f, w, h) {
                            Box(Modifier.padding(SDF_PAD.dp)) { Text(ch.toString(), style = TextStyle(color = black, size = size.sp, font = roboto)) }
                        }
                        Captured(paint(out.primitives, w, h), null, quads = quadRects(out.primitives))
                    },
                )
            }
        }
    }

    // ---------------------------------------------------------------- game canvas

    private val canvasFontKey = FONT_FILE

    /** The text's own box in pixels at [scale] x, as the compose engine lays it out. */
    private fun textBox(text: String, size: Float, scale: Float, font: UiFont?): Pair<Int, Int> {
        val out = ComposeHost(density = scale).frame(FrameInput(TEXT_BOX_W, TEXT_BOX_H)) {
            Text(text, Modifier.testTag("m"), style = TextStyle(color = black, size = size.sp, font = font))
        }
        fun find(nodes: List<SemanticsNode>): SemanticsNode? = nodes.firstNotNullOfOrNull { if (it.testTag == "m") it else find(it.children) }
        val node = requireNotNull(find(out.semantics)) { "the measured text has no semantics node" }
        return node.width to node.height
    }

    /** An element whose box is exactly the text's, so any ink past the text box meets the element's clip. */
    private fun element(kind: CanvasElementKind, text: String, size: Float, scale: Float, font: UiFont?) = CanvasElement().apply {
        this.kind = kind
        this.text = text
        fontSize = size
        color = "#000000"
        offsetX = CANVAS_OFFSET
        offsetY = CANVAS_OFFSET
        if (font != null) this.font = canvasFontKey
        val (w, h) = textBox(text, size, scale, font)
        // A field is sized to its text plus its own inner padding, as an input is laid out.
        val inset = if (kind == CanvasElementKind.TextField) padding else CanvasPadding.None
        width = w / scale + 2 * inset.horizontal
        height = h / scale + 2 * inset.vertical
    }

    private fun canvasFrame(items: List<CanvasItem>, scale: Float, w: Int, h: Int, resolver: CanvasResolver = CanvasResolver.None): FrameOutput {
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            CanvasOverlay(items, scale = scale, resolver = resolver, fonts = mapOf(canvasFontKey to roboto))
        }
        // A second frame: the overlay settles its measured sizes on the first.
        host.frame(FrameInput(w, h), content)
        return host.frame(FrameInput(w, h), content)
    }

    private fun canvasCases() = buildList {
        for ((kind, tag, kindTitle) in listOf(Triple(CanvasElementKind.Text, "text", "Text"), Triple(CanvasElementKind.TextField, "field", "TextField"))) {
            for ((text, textTag) in listOf("Åjump" to "A", "jump" to "j")) {
                for (size in listOf(14f, 48f)) {
                    for (scale in listOf(1f, 1.5f)) {
                        if (kind == CanvasElementKind.Text && textTag == "j") continue
                        add(
                            TextClipCase(
                                "canvas-$tag-$textTag-${size.toInt()}px-s${num(scale)}",
                                CANVAS,
                                "$kindTitle \"$text\" ${size.toInt()} px, scale ${num(scale)}",
                                "loaded Roboto TTF, element box = the text's box (a field adds its own padding)",
                                Outline.Clips,
                            ) {
                                val el = element(kind, text, size, scale, roboto)
                                val w = px(CANVAS_MARGIN + size * text.length, scale) + CANVAS_MARGIN.toInt()
                                val h = px(CANVAS_MARGIN + size * 2, scale) + CANVAS_MARGIN.toInt()
                                val out = canvasFrame(listOf(CanvasItem(1, el)), scale, w, h)
                                Captured(
                                    paint(out.primitives, w, h, out.graphicsLayers),
                                    paint(withoutClips(out.primitives), w, h, out.graphicsLayers),
                                    clips = clipRects(out.primitives),
                                    layers = layerRects(out.graphicsLayers),
                                )
                            },
                        )
                    }
                }
            }
        }
        for (size in listOf(18f, 32f)) {
            add(
                TextClipCase(
                    "canvas-follower-fade-${size.toInt()}px",
                    CANVAS,
                    "Follower \"$LAYER_TEXT\" ${size.toInt()} px",
                    "a Text element in graphicsLayer(alpha = 0.999)",
                    Outline.Layers,
                ) {
                    val w = (size * LAYER_W_EM).roundToInt() + FOLLOWER_MARGIN_W
                    val h = (size * 3).roundToInt() + FOLLOWER_MARGIN_H
                    val resolver = object : CanvasResolver {
                        override fun project(target: String, offset: Vec3f) = CanvasProjection(w / 2f, h * FOLLOWER_Y)
                    }
                    fun follower(fade: Float?) = CanvasElement().apply {
                        kind = CanvasElementKind.Text
                        text = LAYER_TEXT
                        fontSize = size
                        color = "#000000"
                        follow = "Target"
                        anchor = CanvasAnchor.BottomCenter
                        offsetX = 0f
                        offsetY = 0f
                        val (bw, bh) = textBox(LAYER_TEXT, size, 1f, null)
                        width = bw.toFloat()
                        height = bh.toFloat()
                        followRules = fade?.let { CanvasFollowRules(fade = CanvasCurve(listOf(CanvasCurvePoint(0f, it)))) }
                    }
                    val faded = canvasFrame(listOf(CanvasItem(1, follower(ALMOST_OPAQUE))), 1f, w, h, resolver)
                    val plain = canvasFrame(listOf(CanvasItem(1, follower(null))), 1f, w, h, resolver)
                    Captured(paint(faded.primitives, w, h, faded.graphicsLayers), paint(plain.primitives, w, h), layers = layerRects(faded.graphicsLayers))
                },
            )
        }
    }

    /** A frame's density and size in pixels. */
    private data class FrameSize(val density: Float, val w: Int, val h: Int)

    private companion object {
        val FIELD = CaseGroup("field", "BasicTextField: ink past the field's box")
        val LAYER = CaseGroup("layer", "graphicsLayer(alpha) around text")
        val SIDEBAR = CaseGroup("sidebar", "ShadcnSidebar: ink past the header's text box")
        val FONT_SCALE = CaseGroup("fontscale", "Font scale on wrapped 14 sp text")
        val SDF = CaseGroup("sdf", "Distance-field edge of a loaded font")
        val CANVAS = CaseGroup("canvas", "Game canvas: Text, TextField and a follower faded by distance")
        const val FONT_FILE = "Roboto-Regular.ttf"
        const val EXTRA_CHARS = "ÅÉÑÇÿ"
        const val ALMOST_OPAQUE = 0.999f
        const val LAYER_TEXT = "Hgjpqy|Wolf"
        const val PARAGRAPH = "The quick brown fox jumps over the lazy dog again and again"

        const val FIELD_MIN_PAD = 16
        const val FIELD_MARGIN = 48
        const val FIELD_OUTER = 24
        const val LAYER_W_EM = 10
        const val LAYER_MARGIN = 100
        const val LAYER_PAD = 40
        const val SIDEBAR_PAD = 24
        const val SIDEBAR_W = 264
        const val SIDEBAR_H = 360
        const val SIDEBAR_MARGIN = 80
        const val FONT_SCALE_SMALL = 0.85f
        const val FONT_SCALE_LARGE = 1.3f
        const val FS_W = 400
        const val FS_H = 260
        const val FS_PAD = 40
        const val FS_BOX = 160
        const val SDF_W_EM = 1.2f
        const val SDF_H_EM = 1.6f
        const val SDF_MARGIN = 60
        const val SDF_PAD = 24
        const val TEXT_BOX_W = 800
        const val TEXT_BOX_H = 400
        const val CANVAS_OFFSET = 20f
        const val CANVAS_MARGIN = 40f
        const val FOLLOWER_MARGIN_W = 120
        const val FOLLOWER_MARGIN_H = 100
        const val FOLLOWER_Y = 0.7f
    }
}
