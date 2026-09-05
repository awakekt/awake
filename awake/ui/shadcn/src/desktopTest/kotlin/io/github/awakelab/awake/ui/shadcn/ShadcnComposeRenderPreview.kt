/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.foundation.text.TextFieldState
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.testing.ComposeComponentFrame
import io.github.awakelab.awake.compose.testing.composeFrame
import io.github.awakelab.awake.compose.testing.composeTestSession
import io.github.awakelab.awake.compose.testing.rasterize
import io.github.awakelab.awake.compose.testing.toBufferedImage
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.text.font.UiFonts
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAlert
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAlertVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAvatar
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAvatarSizeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadge
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadgeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBreadcrumb
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCheckbox
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCombobox
import io.github.awakelab.awake.ui.shadcn.components.ShadcnComboboxItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnDropdownMenu
import io.github.awakelab.awake.ui.shadcn.components.ShadcnIcon
import io.github.awakelab.awake.ui.shadcn.components.ShadcnIcons
import io.github.awakelab.awake.ui.shadcn.components.ShadcnInput
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnPopover
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSelect
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSelectItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSelectTrigger
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSlider
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSpinner
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSwitch
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTabs
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextarea
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTooltip
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rasterizes the ported recipes so a human can look at them.
 *
 * Investigation, not a gate: it asserts only that something was drawn, then writes a PNG under
 * `build/reports/compose-preview/`. `UiRasterizer` takes a `List<UiDrawPrimitive>` and knows nothing
 * about either engine, and a `ComposeComponentFrame` carries exactly that -- which is why the
 * existing verification tooling reaches the new engine with no adapter at all.
 */
class ShadcnComposeRenderPreview {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    private fun write(
        name: String,
        width: Int,
        height: Int,
        frame: ComposeComponentFrame,
    ) {
        assertTrue(frame.primitives.isNotEmpty(), "$name drew nothing")
        val out = File("build/reports/compose-preview").apply { mkdirs() }
        val file = File(out, "$name.png")
        // The font is not optional for anything with text: `rasterize`'s `font` defaults to null,
        // and a glyph with no font rasterizes as a magenta block. The same default that
        // `LocalFont` provides, so the raster matches what the engine measured against.
        val pixels = frame.primitives.rasterize(
            width,
            height,
            background = theme.palette.background,
            font = UiFonts.default(),
        )
        ImageIO.write(pixels.toBufferedImage(width, height), "png", file)
        println("compose-preview: ${'$'}{file.absolutePath}")
    }

    private fun composeOpenedSelectFrame(width: Int, height: Int): ComposeComponentFrame {
        val content: context(Composer)
        () -> Unit = {
            Column {
                Spacer(Modifier.height(80.dp))
                ShadcnSelect(
                    items = listOf(
                        "Apple",
                        "Banana",
                        "Blueberry",
                        "Grapes",
                        "Pineapple",
                    ).map(::ShadcnSelectItem),
                    selectedIndex = 1,
                    expanded = previewSelectExpanded,
                    onExpandedChange = { previewSelectExpanded = it },
                    onItemSelected = {},
                    id = "preview.select",
                    modifier = Modifier.width(172.dp).testTag("preview.select.trigger"),
                )
            }
        }
        val session = composeTestSession(width, height) { provideShadcnTheme(theme) { content() } }
        previewSelectExpanded = false
        session.frame()
        session.click("preview.select.trigger")
        return session.frame()
    }

    private fun composeOpenedComboboxFrame(width: Int, height: Int): ComposeComponentFrame {
        val content: context(Composer)
        () -> Unit = {
            ShadcnCombobox(
                items = listOf("Kotlin", "Java", "Swift", "Rust").map(::ShadcnComboboxItem),
                selectedIndex = null,
                expanded = true,
                onExpandedChange = {},
                onItemSelected = {},
                id = "preview.combobox",
                modifier = Modifier.width(200.dp),
            )
        }
        val session = composeTestSession(width, height) { provideShadcnTheme(theme) { content() } }
        session.frame()
        return session.frame()
    }

    private var previewSelectExpanded = false

    private fun composeFocusedFieldAndSpinnerFrame(width: Int, height: Int): ComposeComponentFrame {
        val content: context(Composer)
        () -> Unit = {
            provideShadcnTheme(theme) {
                Column(Modifier.padding(16.dp)) {
                    ShadcnInput(
                        state = TextFieldState("Focused input"),
                        modifier = Modifier.width(220.dp).testTag("preview.focused-input"),
                    )
                    ShadcnSpinner(Modifier.padding(top = 16.dp))
                }
            }
        }
        val session = composeTestSession(width, height, content = content)
        session.frame()
        return session.click("preview.focused-input")
    }

    @Test
    fun textRendersEveryVariant() {
        val width = 300
        val height = 250
        write(
            "text",
            width,
            height,
            composeFrame(width, height) {
                provideShadcnTheme(theme) {
                    Column(Modifier.padding(16.dp)) {
                        ShadcnTextVariant.entries.forEach { variant ->
                            ShadcnText(variant.name, variant = variant)
                        }
                    }
                }
            },
        )
    }

    @Test
    fun alertAvatarAndTooltipRender() {
        val width = 300
        val height = 260
        write(
            "alert-avatar-tooltip",
            width,
            height,
            composeFrame(width, height) {
                provideShadcnTheme(theme) {
                    Column(Modifier.padding(16.dp)) {
                        ShadcnAlertVariant.entries.forEach { variant ->
                            ShadcnAlert(
                                title = variant.name,
                                description = "Something happened.",
                                variant = variant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Row {
                            ShadcnAvatarSizeVariant.entries.forEach { size ->
                                ShadcnAvatar("AB", Modifier.padding(end = 8.dp), size = size)
                            }
                        }
                        ShadcnTooltip("Copy to clipboard")
                    }
                }
            },
        )
    }

    @Test
    fun fieldsTabsAndBreadcrumbRender() {
        val width = 340
        val height = 260
        write(
            "fields",
            width,
            height,
            composeFrame(width, height) {
                provideShadcnTheme(theme) {
                    Column(Modifier.padding(16.dp)) {
                        ShadcnBreadcrumb(listOf("Home", "Docs", "Components"))
                        ShadcnTabs(
                            selectedValue = "0",
                            onSelectedChange = {},
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            tab(value = "0", label = "Account")
                            tab(value = "1", label = "Password")
                        }
                        ShadcnText(
                            "Email",
                            variant = ShadcnTextVariant.Muted,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        ShadcnInput(TextFieldState("hello@example.com"))
                        ShadcnText(
                            "Message",
                            variant = ShadcnTextVariant.Muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        ShadcnTextarea(TextFieldState("Tell us more."))
                    }
                }
            },
        )
    }

    @Test
    fun overlaysRender() {
        val width = 340
        val height = 300
        write(
            "overlays",
            width,
            height,
            composeFrame(width, height) {
                provideShadcnTheme(theme) {
                    Column(Modifier.padding(16.dp)) {
                        ShadcnSelectTrigger(null, Modifier.padding(bottom = 8.dp))
                        ShadcnSelectTrigger("Apple", Modifier.padding(bottom = 12.dp))
                        ShadcnDropdownMenu(
                            listOf(
                                ShadcnMenuItem("Profile"),
                                ShadcnMenuItem("Settings"),
                                ShadcnMenuSeparator,
                                ShadcnMenuItem("Delete", destructive = true),
                                ShadcnMenuItem("Archived", enabled = false),
                            ),
                            Modifier.padding(bottom = 12.dp),
                        )
                        ShadcnPopover {
                            ShadcnText(
                                "A popover body.",
                                variant = ShadcnTextVariant.Small,
                            )
                        }
                    }
                }
            },
        )
    }

    @Test
    fun controlledSelectRendersOpen() {
        write(
            "select-open",
            300,
            240,
            composeOpenedSelectFrame(300, 240),
        )
    }

    @Test
    fun controlledComboboxRendersOpen() {
        val frame = composeOpenedComboboxFrame(300, 300)
        val trigger = frame.onNodeWithTag("preview.combobox.trigger").getBoundsInRoot()
        val content = frame.onNodeWithTag("preview.combobox.content").getBoundsInRoot()
        assertEquals(trigger.width, content.width)
        assertEquals(trigger.bottom + 6, content.top)
        write(
            "combobox-open",
            300,
            300,
            frame,
        )
    }

    @Test
    fun focusedInputAndSpinnerRenderSmoothStrokes() {
        write(
            "focused-input-spinner",
            300,
            100,
            composeFocusedFieldAndSpinnerFrame(300, 100),
        )
    }

    @Test
    fun buttonRendersEveryVariant() {
        val width = 220
        val height = 260
        write(
            "button",
            width,
            height,
            composeFrame(width, height) {
                provideShadcnTheme(theme) {
                    Column(Modifier.padding(16.dp)) {
                        ShadcnButtonVariant.entries.forEach { variant ->
                            ShadcnButton(variant.name, variant = variant)
                        }
                    }
                }
            },
        )
    }

    @Test
    fun buttonRendersEverySize() {
        val width = 220
        val height = 300
        write(
            "button-sizes",
            width,
            height,
            composeFrame(width, height) {
                provideShadcnTheme(theme) {
                    Column(Modifier.padding(16.dp)) {
                        ShadcnButtonSizeVariant.entries.forEach { size ->
                            ShadcnButton(if (size.square) "@" else size.name, size = size)
                        }
                    }
                }
            },
        )
    }

    @Test
    fun buttonIconsRenderThePinnedLucideGlyphs() {
        val width = 220
        val height = 72
        write(
            "button-icons",
            width,
            height,
            composeFrame(width, height) {
                provideShadcnTheme(theme) {
                    Row(
                        horizontalArrangement = Arrangement.spacedByHorizontal(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShadcnButton(size = ShadcnButtonSizeVariant.Icon) { ShadcnIcon(ShadcnIcons.camera) }
                        ShadcnButton(
                            variant = ShadcnButtonVariant.Outline,
                            size = ShadcnButtonSizeVariant.IconSm,
                        ) {
                            ShadcnIcon(ShadcnIcons.save)
                        }
                        ShadcnButton(
                            "Save scene",
                            variant = ShadcnButtonVariant.Outline,
                            leadingIcon = ShadcnIcons.save,
                        )
                    }
                }
            },
        )
    }

    @Test
    fun switchAndCheckboxRenderBothStates() {
        val width = 160
        val height = 140
        write(
            "switch-checkbox",
            width,
            height,
            composeFrame(width, height) {
                provideShadcnTheme(theme) {
                    Column(Modifier.padding(16.dp)) {
                        ShadcnText("Switch", variant = ShadcnTextVariant.Muted)
                        ShadcnSwitch(checked = true)
                        ShadcnSwitch(checked = false)
                        ShadcnText("Checkbox", variant = ShadcnTextVariant.Muted)
                        ShadcnCheckbox(checked = true)
                        ShadcnCheckbox(checked = false)
                    }
                }
            },
        )
    }

    @Test
    fun badgeRendersEveryVariant() {
        val width = 200
        val height = 220
        write(
            "badge",
            width,
            height,
            composeFrame(width, height) {
                provideShadcnTheme(theme) {
                    Column(Modifier.padding(16.dp)) {
                        ShadcnBadgeVariant.entries.forEach { variant ->
                            ShadcnBadge(variant.name, variant = variant)
                        }
                    }
                }
            },
        )
    }

    @Test
    fun cardRendersWithItsContent() {
        val width = 280
        val height = 170
        write(
            "card",
            width,
            height,
            composeFrame(width, height) {
                provideShadcnTheme(theme) {
                    Column(Modifier.padding(16.dp)) {
                        ShadcnCard(Modifier.size(240.dp, 130.dp)) {
                            Column {
                                ShadcnText("Card title", variant = ShadcnTextVariant.Large)
                                ShadcnText(
                                    "A bordered, filled panel.",
                                    variant = ShadcnTextVariant.Muted,
                                )
                            }
                        }
                    }
                }
            },
        )
    }

    @Test
    fun fieldsInsideFullWidthCardRenderAtTheCardWidth() {
        val width = 360
        val height = 180
        write(
            "fields-full-width-card",
            width,
            height,
            composeFrame(width, height) {
                provideShadcnTheme(theme) {
                    ShadcnCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ShadcnInput(TextFieldState("Full name"))
                            ShadcnTextarea(TextFieldState("Biography"))
                        }
                    }
                }
            },
        )
    }

    /**
     * Regression test for a bug found by inspecting a rendered PNG: `shadcnSurface(bordered =
     * true)`'s border used to be four independently-filled edge strips, each requesting the full
     * corner radius on a strip only stroke-width px thick -- which cannot represent a curve, so
     * the rendered corner was a plain square. `BorderNode` now draws one continuous stroked
     * rounded-rect outline instead. The assertion below is geometric, not "something was drawn":
     * a real rounded corner leaves the outline's own bounding-box corner unpainted (the arc sets
     * back from it by the radius), while the old four-strip bug painted exactly that pixel.
     */
    @Test
    fun surfaceRendersBorderedRoundedCorner() {
        val width = 200
        val height = 120
        val frame = composeFrame(width, height) {
            provideShadcnTheme(theme) {
                io.github.awakelab.awake.ui.shadcn.components.shadcnSurface(
                    Modifier.padding(16.dp).size(160.dp, 80.dp),
                ) {
                    ShadcnText("border test", variant = ShadcnTextVariant.Muted)
                }
            }
        }
        assertTrue(frame.primitives.isNotEmpty(), "surface-border-debug drew nothing")

        val outline = frame.primitives
            .filterIsInstance<io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive.Mesh>()
            .firstOrNull()
        assertTrue(outline != null, "the border no longer draws as a tessellated ring")
        // The ring's opaque core, not the whole mesh: an anti-aliased edge adds a transparent
        // fringe outside the painted band, and sampling the raster at the mesh's outer rim would
        // read background no matter what the border did.
        val ring = outline.placedMesh()
        val bounds = io.github.awakelab.awake.core.graphics2d.ColoredTriangleMesh(
            // The fringe's outer rim is exactly alpha 0; the painted band keeps the border token's
            // own alpha, which is translucent white here rather than opaque.
            ring.vertices.filter { it.color.a > 0f },
            ring.indices,
        ).bounds()

        val pixels = frame.primitives.rasterize(
            width,
            height,
            background = theme.palette.background,
            font = UiFonts.default(),
        )

        fun brightnessAt(px: Int, py: Int): Float {
            val i = (py.coerceIn(0, height - 1) * width + px.coerceIn(0, width - 1)) * 4
            val r = pixels[i].toUByte().toInt() / 255f
            val g = pixels[i + 1].toUByte().toInt() / 255f
            val b = pixels[i + 2].toUByte().toInt() / 255f
            return (r + g + b) / 3f
        }

        // The border token is translucent white blended over the theme background, not an opaque
        // colour to match exactly -- so this compares brightness against the untouched background
        // rather than the raw (unblended) token.
        val emptyBrightness = with(theme.palette.background) { (r + g + b) / 3f }
        val cornerBrightness = brightnessAt(bounds.x.toInt(), bounds.y.toInt())
        val topEdgeBrightness =
            brightnessAt((bounds.x + bounds.width / 2f).toInt(), bounds.y.toInt())

        assertTrue(
            topEdgeBrightness > emptyBrightness + 0.05f,
            "expected the border to lighten the straight top edge, got brightness $topEdgeBrightness " +
                "over a background of $emptyBrightness",
        )
        assertTrue(
            kotlin.math.abs(cornerBrightness - emptyBrightness) < 0.02f,
            "the outline's bounding-box corner is painted -- that is a square corner, not a " +
                "rounded one; got brightness $cornerBrightness over a background of $emptyBrightness",
        )

        val out = File("build/reports/compose-preview").apply { mkdirs() }
        val file = File(out, "surface-border-debug.png")
        ImageIO.write(pixels.toBufferedImage(width, height), "png", file)
        println("compose-preview: ${'$'}{file.absolutePath}")
    }

    @Test
    fun sliderRendersAcrossItsRange() {
        val width = 260
        val height = 190

        val frame = composeFrame(width, height) {
            provideShadcnTheme(theme) {
                Column(Modifier.padding(20.dp)) {
                    listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { value ->
                        ShadcnSlider(value = value, modifier = Modifier.size(220.dp, 26.dp))
                    }
                    ShadcnSlider(
                        value = 0.4f,
                        modifier = Modifier.size(220.dp, 26.dp),
                        enabled = false,
                    )
                }
            }
        }

        assertTrue(frame.primitives.isNotEmpty(), "the slider drew nothing")
        println(frame.printToString())

        val out = File("build/reports/compose-preview").apply { mkdirs() }
        val file = File(out, "slider.png")
        // `rasterize` returns raw RGBA, not an encoded image -- writing it straight to a .png is a
        // file no viewer can open.
        // The theme's own background, not white. `ShadcnThemeConfig.dark` defaults to true, so a
        // white canvas renders a dark-theme slider as an inverted-looking mess and reads as a bug in
        // the recipe -- which is exactly how the first version of this preview was misread.
        // The font is not optional for anything with text: `rasterize`'s `font` defaults to null,
        // and a glyph with no font rasterizes as a magenta block. The same default that
        // `LocalFont` provides, so the raster matches what the engine measured against.
        val pixels = frame.primitives.rasterize(
            width,
            height,
            background = theme.palette.background,
            font = UiFonts.default(),
        )
        ImageIO.write(pixels.toBufferedImage(width, height), "png", file)
        println("compose-preview: ${file.absolutePath}")
    }
}
