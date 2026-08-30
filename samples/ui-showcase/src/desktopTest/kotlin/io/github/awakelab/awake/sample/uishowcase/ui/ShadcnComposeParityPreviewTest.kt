/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.foundation.rememberScrollState
import io.github.awakelab.awake.compose.foundation.verticalScroll
import io.github.awakelab.awake.compose.foundation.text.TextFieldState
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.testing.captureImage
import io.github.awakelab.awake.compose.testing.captureSemantics
import io.github.awakelab.awake.compose.testing.ComposeComponentFrame
import io.github.awakelab.awake.compose.testing.composeFrame
import io.github.awakelab.awake.compose.testing.composeTestSession
import io.github.awakelab.awake.compose.testing.rasterizeLayoutOverlay
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.draw.drawWithContent
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.DrawShape
import io.github.awakelab.awake.core.graphics2d.DrawStroke
import io.github.awakelab.awake.core.graphics2d.toPath
import io.github.awakelab.awake.core.math2d.Rectangle
import io.github.awakelab.awake.core.math2d.Dp as StrokeWidth
import io.github.awakelab.awake.core.math2d.sp
import io.github.awakelab.awake.core.text.font.UiFonts
import io.github.awakelab.awake.render.testing.writePng
import io.github.awakelab.awake.render.testing.PixelMap
import io.github.awakelab.awake.sample.uishowcase.state.UiShowcaseRuntimeState
import io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs.TextFieldPage
import io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs.TextareaPage
import io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs.ComboboxPage
import io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs.TextareaPage
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAlertVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadgeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonGroupOrientation
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonGroup
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCard
import io.github.awakelab.awake.ui.shadcn.components.ShadcnMenuItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.components.shadcnAlert
import io.github.awakelab.awake.ui.shadcn.components.ShadcnBadge
import io.github.awakelab.awake.ui.shadcn.components.ShadcnCheckbox
import io.github.awakelab.awake.ui.shadcn.components.shadcnDropdownMenu
import io.github.awakelab.awake.ui.shadcn.components.ShadcnFieldLabel
import io.github.awakelab.awake.ui.shadcn.components.ShadcnFocusRingMode
import io.github.awakelab.awake.ui.shadcn.components.ShadcnInput
import io.github.awakelab.awake.ui.shadcn.components.ShadcnInputOtp
import io.github.awakelab.awake.ui.shadcn.components.ShadcnIcons
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted
import io.github.awakelab.awake.ui.shadcn.components.shadcnIcon
import io.github.awakelab.awake.ui.shadcn.components.shadcnPopover
import io.github.awakelab.awake.ui.shadcn.components.ShadcnProgress
import io.github.awakelab.awake.ui.shadcn.components.ShadcnRadioGroup
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSelect
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSelectItem
import io.github.awakelab.awake.ui.shadcn.components.ShadcnRangeSlider
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSelectTrigger
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSlider
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSelectTrigger
import io.github.awakelab.awake.ui.shadcn.components.shadcnSwitch
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTabs
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.shadcnTextarea
import io.github.awakelab.awake.ui.shadcn.components.shadcnSurface
import io.github.awakelab.awake.ui.shadcn.components.shadcnSidebar
import io.github.awakelab.awake.ui.shadcn.components.shadcnSidebarMenuItem
import io.github.awakelab.awake.ui.shadcn.shadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import io.github.awakelab.awake.vulkan.renderer.VulkanUiPreviewCapture
import io.github.awakelab.awake.vulkan.utils.VkResultException
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class ShadcnComposeParityPreviewTest {

    @Test
    fun showcaseSidebarClicksAndScrollsOnlyItsContent() {
        val session = composeTestSession(width = 1200, height = 900) {
            ShowcaseApp(UiShowcaseRuntimeState())
        }

        val initial = session.frame()
        val cardTag = "showcase.sidebar.page.card"
        val aspectRatioTag = "showcase.sidebar.page.aspect-ratio"
        val firstItemBefore = initial.onNodeWithTag(aspectRatioTag).getBoundsInRoot()
        val cardBefore = initial.onNodeWithTag(cardTag).getBoundsInRoot()
        val headerBefore = initial.onNodeWithTag("showcase.sidebar.header").getBoundsInRoot()
        val footerBefore = initial.onNodeWithTag("showcase.sidebar.footer").getBoundsInRoot()
        val contentBefore = initial.onNodeWithTag("showcase.sidebar.content").getBoundsInRoot()
        val pageBefore = initial.onNodeWithTag("showcase.page.aspect-ratio").getBoundsInRoot()
        val initialAspectRatio = initial.flatSemantics().single { it.testTag == aspectRatioTag }
        assertTrue(
            initialAspectRatio.config[SemanticsProperties.Selected] == true,
            "initial sidebar item does not publish its active state",
        )
        assertTrue(
            cardBefore.top >= contentBefore.top && cardBefore.bottom <= contentBefore.bottom,
            "test target is not visible: card=$cardBefore content=$contentBefore",
        )
        assertTrue(pageBefore.left >= 288, "sidebar inset overlaps the sidebar: page=$pageBefore")

        val clicked = session.click(cardTag)
        val card = clicked.flatSemantics().single { it.testTag == cardTag }
        assertTrue(clicked.flatSemantics().any { it.testTag == "showcase.page.card" }, "clicked sidebar item did not update page content")
        assertTrue(card.config[SemanticsProperties.Selected] == true, "clicked sidebar item did not become active: card=$cardBefore")

        session.frame(FrameInput(1200, 900, pointerX = 140, pointerY = 380, scrollDeltaY = -1f))
        val scrolled = session.frame()
        val firstItemAfter = scrolled.onNodeWithTag(aspectRatioTag).getBoundsInRoot()
        val headerAfter = scrolled.onNodeWithTag("showcase.sidebar.header").getBoundsInRoot()
        val footerAfter = scrolled.onNodeWithTag("showcase.sidebar.footer").getBoundsInRoot()
        assertTrue(firstItemAfter.top < firstItemBefore.top, "sidebar content did not move after wheel input")
        assertTrue(headerAfter == headerBefore, "sidebar header moved with scrollable content")
        assertTrue(footerAfter == footerBefore, "sidebar footer moved with scrollable content")
    }

    private val cachedFont = UiFonts.default()

    /**
     * Writes the Vulkan preview PNG, or explains why it could not.
     *
     * The GPU pass asserts nothing -- it produces a diagnostic image beside the CPU raster, and
     * every real assertion in these tests runs off the CPU frame either way. Letting a device
     * that will not open fail the build turns an optional artifact into a hard requirement, and
     * it fails non-deterministically: `vkCreateInstance` starts refusing under load, so which
     * preview test breaks depends on what else Gradle happens to be running. Both
     * `renderStrokeAaDiagnostic` and `renderComboboxShowcasePage` have failed this way while
     * passing when their module runs alone.
     *
     * Only device creation is tolerated. A capture that starts and then fails is a real defect
     * and still fails the test.
     */
    private fun writeGpuPreview(
        previewName: String,
        outDir: File,
        width: Int,
        height: Int,
        frame: ComposeComponentFrame,
        bg: Color,
    ) {
        val gpuFile = File(outDir, "$previewName-vulkan.png")
        val capture = try {
            VulkanUiPreviewCapture(width, height)
        } catch (failure: VkResultException) {
            println(
                "Skipped Vulkan preview for '$previewName': no device available " +
                    "(${failure.message}). The CPU raster and every assertion in this test ran.",
            )
            return
        }
        capture.use {
            runBlocking {
                it.capture(frame.primitives, cachedFont).compositeOver(bg).writePng(gpuFile)
            }
        }
        println("Generated Vulkan preview: ${gpuFile.absolutePath}")
    }

    private fun writePreview(
        previewName: String,
        width: Int = 600,
        height: Int = 300,
        dark: Boolean = false,
        settleFrames: Int = 1,
        focusTag: String? = null,
        scrollDeltasY: List<Float> = emptyList(),
        gpuPreview: Boolean = false,
        debugOverlay: Boolean = false,
        content: context(Composer) () -> Unit,
    ) {
        val outDir = File("build/ui-previews").apply { mkdirs() }
        val theme = shadcnThemeValues(dark = dark)
        val frame = if (settleFrames == 1 && focusTag == null) {
            composeFrame(width, height) {
                provideShadcnTheme(theme) { content() }
            }
        } else {
            val session = composeTestSession(width, height) {
                provideShadcnTheme(theme) { content() }
            }
            var output = session.frame()
            focusTag?.let { output = session.click(it) }
            scrollDeltasY.forEach { delta ->
                output = session.frame(
                    FrameInput(
                        viewportWidth = width,
                        viewportHeight = height,
                        pointerX = 100,
                        pointerY = 400,
                        scrollDeltaY = delta,
                    ),
                )
            }
            repeat((settleFrames - 1).coerceAtLeast(0)) {
                output = session.frame()
            }
            output
        }
        assertTrue(frame.primitives.isNotEmpty(), "$previewName drew nothing")

        val pngFile = File(outDir, "$previewName.png")
        val jsonFile = File(outDir, "$previewName.json")

        val bg = theme.palette.background
        frame.captureImage(pngFile, width, height, background = bg, font = cachedFont)
        if (debugOverlay) {
            val debugFile = File(outDir, "$previewName-layout-debug.png")
            PixelMap(width, height, frame.rasterizeLayoutOverlay(width, height, bg, cachedFont))
                .writePng(debugFile)
            println("Generated layout debug preview: ${debugFile.absolutePath}")
        }
        if (gpuPreview) {
            writeGpuPreview(previewName, outDir, width, height, frame, bg)
        }

        val semanticsList = frame.captureSemantics().nodes.mapNotNull { node ->
            val tag = node.testTag ?: return@mapNotNull null
            val inset = node.contentPadding?.let {
                ",\"contentPadding\":{\"start\":${it.start},\"top\":${it.top},\"end\":${it.end},\"bottom\":${it.bottom}}"
            }.orEmpty()
            val borderWidth = node.borderWidth?.let { ",\"borderWidth\":$it" }.orEmpty()
            val cornerRadius = node.cornerRadius?.let { ",\"borderRadius\":$it" }.orEmpty()
            """{"id":"$tag","bounds":{"x":${node.bounds.left}.0,"y":${node.bounds.top}.0,"w":${node.bounds.width}.0,"h":${node.bounds.height}.0}$inset$borderWidth$cornerRadius}"""
        }
        val jsonContent = """{"width":$width,"height":$height,"semantics":[${semanticsList.joinToString(",")}]}"""
        jsonFile.writeText(jsonContent)
        println("Generated preview: ${pngFile.absolutePath}")
    }

    @Test
    fun renderBadgeVariants() {
        listOf(false, true).forEach { dark ->
            val themeSuffix = if (dark) "dark" else "light"
            writePreview(
                previewName = "awake-badge-variants-$themeSuffix",
                width = 400,
                height = 100,
                dark = dark,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedByHorizontal(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShadcnBadge("Default", modifier = Modifier.testTag("badge.Default"), variant = ShadcnBadgeVariant.Default)
                    ShadcnBadge("Secondary", modifier = Modifier.testTag("badge.Secondary"), variant = ShadcnBadgeVariant.Secondary)
                    ShadcnBadge("Destructive", modifier = Modifier.testTag("badge.Destructive"), variant = ShadcnBadgeVariant.Destructive)
                    ShadcnBadge("Outline", modifier = Modifier.testTag("badge.Outline"), variant = ShadcnBadgeVariant.Outline)
                }
            }
        }
    }

    @Test
    fun renderButtonVariants() {
        listOf(false, true).forEach { dark ->
            val themeSuffix = if (dark) "dark" else "light"
            writePreview(
                previewName = "awake-button-variants-$themeSuffix",
                width = 600,
                height = 100,
                dark = dark,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedByHorizontal(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShadcnButton("Default", modifier = Modifier.testTag("parity-default"), variant = ShadcnButtonVariant.Default)
                    ShadcnButton("Secondary", modifier = Modifier.testTag("parity-secondary"), variant = ShadcnButtonVariant.Secondary)
                    ShadcnButton("Outline", modifier = Modifier.testTag("parity-outline"), variant = ShadcnButtonVariant.Outline)
                    ShadcnButton("Ghost", modifier = Modifier.testTag("parity-ghost"), variant = ShadcnButtonVariant.Ghost)
                    ShadcnButton("Destructive", modifier = Modifier.testTag("parity-destructive"), variant = ShadcnButtonVariant.Destructive)
                    ShadcnButton("Link", modifier = Modifier.testTag("parity-link"), variant = ShadcnButtonVariant.Link)
                }
            }
        }
    }

    @Test
    fun renderButtonIcons() {
        listOf(false, true).forEach { dark ->
            val themeSuffix = if (dark) "dark" else "light"
            writePreview("awake-button-icons-$themeSuffix", width = 300, height = 80, dark = dark) {
                Row(horizontalArrangement = Arrangement.spacedByHorizontal(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShadcnButton(Modifier.testTag("parity-icon-default"), size = ShadcnButtonSizeVariant.Icon) {
                        shadcnIcon(ShadcnIcons.camera)
                    }
                    ShadcnButton(Modifier.testTag("parity-icon-outline"), variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.IconSm) {
                        shadcnIcon(ShadcnIcons.save)
                    }
                    ShadcnButton(
                        "Save scene",
                        modifier = Modifier.testTag("parity-icon-label"),
                        variant = ShadcnButtonVariant.Outline,
                        leadingIcon = ShadcnIcons.save,
                    )
                }
            }
        }
    }

    @Test
    fun renderButtonSizes() {
        listOf(false, true).forEach { dark ->
            val themeSuffix = if (dark) "dark" else "light"
            writePreview("awake-button-sizes-$themeSuffix", width = 260, height = 80, dark = dark) {
                Row(
                    horizontalArrangement = Arrangement.spacedByHorizontal(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShadcnButton("Small", modifier = Modifier.testTag("parity-size-small"), size = ShadcnButtonSizeVariant.Sm)
                    ShadcnButton("Default", modifier = Modifier.testTag("parity-size-default"))
                    ShadcnButton("Large", modifier = Modifier.testTag("parity-size-large"), size = ShadcnButtonSizeVariant.Lg)
                }
            }
        }
    }

    @Test
    fun renderDisabledButtons() {
        listOf(false, true).forEach { dark ->
            val themeSuffix = if (dark) "dark" else "light"
            writePreview("awake-button-disabled-$themeSuffix", width = 260, height = 80, dark = dark) {
                Row(
                    horizontalArrangement = Arrangement.spacedByHorizontal(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShadcnButton("Default", modifier = Modifier.testTag("parity-disabled-default"), enabled = false)
                    ShadcnButton("Outline", modifier = Modifier.testTag("parity-disabled-outline"), variant = ShadcnButtonVariant.Outline, enabled = false)
                    ShadcnButton("Destructive", modifier = Modifier.testTag("parity-disabled-destructive"), variant = ShadcnButtonVariant.Destructive, enabled = false)
                }
            }
        }
    }

    @Test
    fun renderButtonGroup() {
        listOf(false, true).forEach { dark ->
            val themeSuffix = if (dark) "dark" else "light"
            writePreview(
                previewName = "awake-button-group-basic-$themeSuffix",
                width = 300,
                height = 100,
                dark = dark,
            ) {
                ShadcnButtonGroup(modifier = Modifier.testTag("parity-button-group")) {
                    button("Archive", modifier = Modifier.testTag("parity-button-group.archive"), variant = ShadcnButtonVariant.Outline)
                    button("Report", modifier = Modifier.testTag("parity-button-group.report"), variant = ShadcnButtonVariant.Outline)
                }
            }
            writePreview(
                previewName = "awake-button-group-vertical-$themeSuffix",
                width = 200,
                height = 160,
                dark = dark,
            ) {
                ShadcnButtonGroup(
                    modifier = Modifier.testTag("parity-button-group-vertical").width(156.dp),
                    orientation = ShadcnButtonGroupOrientation.Vertical,
                ) {
                    button("Archive", modifier = Modifier.testTag("parity-button-group-vertical.archive"), variant = ShadcnButtonVariant.Outline)
                    button("Report", modifier = Modifier.testTag("parity-button-group-vertical.report"), variant = ShadcnButtonVariant.Outline)
                }
            }
        }
    }

    @Test
    fun renderSliderStates() {
        listOf(false, true).forEach { dark ->
            writePreview(
                previewName = "awake-slider-states-${if (dark) "dark" else "light"}",
                width = 300,
                height = 40,
                dark = dark,
            ) {
                ShadcnSlider(
                    value = 50f,
                    min = 0f,
                    max = 100f,
                    modifier = Modifier.width(300.dp).testTag("parity-slider"),
                )
            }
        }
    }

    @Test
    fun renderRangeSliderStates() {
        listOf(false, true).forEach { dark ->
            writePreview(
                previewName = "awake-range-slider-states-${if (dark) "dark" else "light"}",
                width = 300,
                height = 40,
                dark = dark,
            ) {
                ShadcnRangeSlider(
                    start = 25f,
                    end = 75f,
                    min = 0f,
                    max = 100f,
                    modifier = Modifier.width(300.dp).testTag("parity-range-slider"),
                )
            }
        }
    }

    @Test
    fun renderInputOtp() {
        listOf(false, true).forEach { dark ->
            val themeSuffix = if (dark) "dark" else "light"
            writePreview(
                previewName = "awake-input-otp-basic-$themeSuffix",
                width = 300,
                height = 100,
                dark = dark,
            ) {
                ShadcnInputOtp(value = "482910", length = 6, modifier = Modifier.testTag("parity-input-otp"))
            }
            writePreview(
                previewName = "awake-input-otp-grouped-$themeSuffix",
                width = 320,
                height = 100,
                dark = dark,
            ) {
                ShadcnInputOtp(value = "934182", length = 6, groupSize = 3, modifier = Modifier.testTag("parity-input-otp-grouped"))
            }
        }
    }

    @Test
    fun renderCardLogin() {
        writePreview(
            previewName = "awake-card-light",
            width = 340,
            height = 260,
            dark = false,
        ) {
            ShadcnCard(Modifier.testTag("parity-card").width(288.dp)) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s6),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(Tw.Spacing.s6).padding(horizontal = Tw.Spacing.s6),
                    ) {
                        ShadcnText(
                            "Login to your account",
                            modifier = Modifier.testTag("parity-card.title"),
                            variant = ShadcnTextVariant.Large,
                            lineHeight = 16f.sp,
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Tw.Spacing.s6).testTag("parity-card.content"),
                        verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s3),
                    ) {
                        ShadcnText(
                            "Email",
                            modifier = Modifier.testTag("parity-card.label"),
                            variant = ShadcnTextVariant.Small,
                            lineHeight = 14f.sp,
                        )
                        ShadcnInput(
                            TextFieldState(""),
                            modifier = Modifier.testTag("parity-card.email"),
                        )
                        ShadcnButton(
                            "Login",
                            modifier = Modifier.fillMaxWidth().testTag("parity-card.login"),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun renderDropdownMenu() {
        writePreview(
            previewName = "awake-dropdown-menu-states-light",
            width = 300,
            height = 250,
            dark = false,
        ) {
            shadcnDropdownMenu(
                listOf(
                    ShadcnMenuItem("My Account"),
                    ShadcnMenuItem("Edit"),
                    ShadcnMenuItem("Duplicate"),
                    ShadcnMenuItem("Delete", destructive = true),
                ),
                modifier = Modifier.testTag("parity-dropdown.surface").width(160.dp),
                id = "parity-dropdown",
            )
        }
    }

    @Test
    fun renderPopover() {
        writePreview(
            previewName = "awake-popover-states-light",
            width = 300,
            height = 200,
            dark = false,
        ) {
            // shadcnPopover has no open/close state machine yet -- anchoring and the overlay
            // lifecycle are deferred to the layer system in 07-overlay-layering.md. The reference
            // case forces `<Popover open>` and crops to the content box, so this preview matches
            // it by laying the two nodes out directly with the reference's trigger-to-content gap.
            Column(verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s1)) {
                ShadcnButton(
                    "Open popover",
                    variant = ShadcnButtonVariant.Outline,
                    modifier = Modifier.testTag("parity-popover.trigger"),
                )
                shadcnPopover(modifier = Modifier.testTag("parity-popover.content"), width = 260.dp) {
                    ShadcnText("Place content for the popover here.", variant = ShadcnTextVariant.Small)
                }
            }
        }
    }

    @Test
    fun renderAlertVariants() {
        writePreview(
            previewName = "awake-alert-variants-light",
            width = 300,
            height = 220,
            dark = false,
        ) {
            Column(
                Modifier.width(272.dp),
                verticalArrangement = Arrangement.spacedBy(Tw.Spacing.s4),
            ) {
                shadcnAlert(
                    title = "You can add components",
                    description = "Use the CLI to add components to your project.",
                    modifier = Modifier.testTag("parity-alert-default"),
                )
                shadcnAlert(
                    title = "Unable to process your payment.",
                    description = "Please verify your billing information and try again.",
                    variant = ShadcnAlertVariant.Destructive,
                    modifier = Modifier.testTag("parity-alert-destructive"),
                )
            }
        }
    }

    @Test
    fun renderSwitchAndCheckbox() {
        listOf(false, true).forEach { dark ->
            val themeSuffix = if (dark) "dark" else "light"
            writePreview("awake-switch-variants-$themeSuffix", width = 200, height = 100, dark = dark) {
                Row(horizontalArrangement = Arrangement.spacedByHorizontal(16.dp)) {
                    shadcnSwitch(checked = false, modifier = Modifier.testTag("parity-switch-off"))
                    shadcnSwitch(checked = true, modifier = Modifier.testTag("parity-switch-on"))
                    shadcnSwitch(checked = false, enabled = false, modifier = Modifier.testTag("parity-switch-disabled"))
                }
            }
            writePreview("awake-checkbox-states-$themeSuffix", width = 200, height = 100, dark = dark) {
                Row(horizontalArrangement = Arrangement.spacedByHorizontal(16.dp)) {
                    ShadcnCheckbox(checked = false, modifier = Modifier.testTag("parity-checkbox-unchecked"))
                    ShadcnCheckbox(checked = true, modifier = Modifier.testTag("parity-checkbox-checked"))
                    ShadcnCheckbox(checked = false, enabled = false, modifier = Modifier.testTag("parity-checkbox-disabled"))
                }
            }
        }
    }

    @Test
    fun renderRadioGroup() {
        listOf(false, true).forEach { dark ->
            val themeSuffix = if (dark) "dark" else "light"
            writePreview(
                previewName = "awake-radiogroup-$themeSuffix",
                width = 300,
                height = 150,
                dark = dark,
            ) {
                ShadcnRadioGroup(
                    options = listOf("Default", "Comfortable", "Compact"),
                    selected = 1,
                )
            }
        }
    }

    @Test
    fun renderInputStates() {
        listOf(false, true).forEach { dark ->
            writePreview("awake-textfield-states-${if (dark) "dark" else "light"}", 300, 200, dark) {
                Column(Modifier.width(256.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShadcnInput(
                        TextFieldState(""),
                        placeholder = "Placeholder",
                        modifier = Modifier.testTag("parity-field-1"),
                    )
                    ShadcnInput(TextFieldState("Typed text"), modifier = Modifier.testTag("parity-field-2"))
                    ShadcnInput(
                        TextFieldState(""),
                        enabled = false,
                        placeholder = "Disabled",
                        modifier = Modifier.testTag("parity-field-3"),
                    )
                }
            }
        }
    }

    @Test
    fun renderFocusedInput() {
        writePreview(
            previewName = "awake-textfield-focused-light",
            width = 300,
            height = 100,
            focusTag = "parity-focused-field",
            settleFrames = 8,
            gpuPreview = true,
        ) {
            ShadcnInput(
                TextFieldState("Focused input"),
                modifier = Modifier.testTag("parity-focused-field"),
            )
        }
    }

    @Test
    fun renderFocusedInputStyleOnly() {
        writePreview(
            previewName = "awake-textfield-focused-style-only-light",
            width = 300,
            height = 100,
            focusTag = "parity-focused-field",
            settleFrames = 8,
        ) {
            ShadcnInput(
                TextFieldState("Focused input"),
                focusRingMode = ShadcnFocusRingMode.StyleOnly,
                modifier = Modifier.testTag("parity-focused-field"),
            )
        }
    }

    @Test
    fun renderStrokeAaDiagnostic() {
        writePreview(
            previewName = "awake-stroke-aa-diagnostic",
            width = 360,
            height = 220,
            gpuPreview = true,
        ) {
            val dark = Color(r = 0.07f, g = 0.07f, b = 0.07f)
            val accent = Color(r = 0f, g = 0.4f, b = 1f)

            Column(
                modifier = Modifier.padding(30.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .height(40.dp)
                        .drawWithContent {
                            drawContent()
                            drawStrokedPath(
                                DrawShape.RoundedRectangle(StrokeWidth(10.dp.value)).toPath(
                                    Rectangle(0f, 0f, width.toFloat(), height.toFloat()),
                                ),
                                DrawStroke(width = StrokeWidth(1.dp.value)),
                                dark,
                            )
                        },
                )
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .height(40.dp)
                        .drawWithContent {
                            drawContent()
                            drawStrokedPath(
                                DrawShape.RoundedRectangle(StrokeWidth(13.dp.value)).toPath(
                                    Rectangle(-1.5f, -1.5f, width.toFloat() + 1.5f, height.toFloat() + 1.5f),
                                ),
                                DrawStroke(width = StrokeWidth(3.dp.value)),
                                accent,
                            )
                        },
                )
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .height(40.dp)
                        .drawWithContent {
                            drawContent()
                            drawStrokedPath(
                                DrawShape.RoundedRectangle(StrokeWidth(10.dp.value)).toPath(
                                    Rectangle(0f, 0f, width.toFloat(), height.toFloat()),
                                ),
                                DrawStroke(width = StrokeWidth(1.dp.value)),
                                dark,
                            )
                            drawStrokedPath(
                                DrawShape.RoundedRectangle(StrokeWidth(13.dp.value)).toPath(
                                    Rectangle(-1.5f, -1.5f, width.toFloat() + 1.5f, height.toFloat() + 1.5f),
                                ),
                                DrawStroke(width = StrokeWidth(3.dp.value)),
                                accent,
                            )
                        },
                )
            }
        }
    }

    @Test
    fun renderSelectClosed() {
        writePreview(
            previewName = "awake-select-closed-light",
            width = 300,
            height = 100,
            dark = false,
        ) {
            ShadcnSelectTrigger(null, placeholder = "Select a fruit", modifier = Modifier.testTag("parity-select").width(172.dp))
        }
    }

    @Test
    fun renderSelectOpen() {
        listOf(false, true).forEach { dark ->
            writePreview(
                previewName = "awake-select-open-${if (dark) "dark" else "light"}",
                width = 300,
                height = 240,
                dark = dark,
                settleFrames = 3,
            ) {
                ShadcnSelect(
                    items = listOf("Apple", "Banana", "Blueberry", "Grapes", "Pineapple").map(::ShadcnSelectItem),
                    selectedIndex = 1,
                    expanded = true,
                    onExpandedChange = {},
                    onItemSelected = {},
                    id = "parity-select",
                    modifier = Modifier.width(172.dp),
                )
            }
        }
    }

    @Test
    fun renderSelectScrolled() {
        listOf(false, true).forEach { dark ->
            writePreview(
                previewName = "awake-select-scrolled-${if (dark) "dark" else "light"}",
                width = 300,
                height = 240,
                dark = dark,
                settleFrames = 3,
            ) {
                ShadcnSelect(
                    items = (1..10).map { ShadcnSelectItem("Item $it") },
                    selectedIndex = 5,
                    expanded = true,
                    onExpandedChange = {},
                    onItemSelected = {},
                    id = "parity-select.scrolled",
                    modifier = Modifier.width(172.dp),
                )
            }
        }
    }

    @Test
    fun renderProgressAndTabs() {
        listOf(false, true).forEach { dark ->
            val themeSuffix = if (dark) "dark" else "light"
            writePreview("awake-progress-$themeSuffix", width = 300, height = 120, dark = dark) {
                Column(Modifier.width(212.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ShadcnProgress(progress = 0.25f, modifier = Modifier.testTag("parity-progress-1"))
                    ShadcnProgress(progress = 0.65f, modifier = Modifier.testTag("parity-progress-2"))
                }
            }
        }
        listOf(false, true).forEach { dark ->
            writePreview(
                previewName = "awake-tabs-${if (dark) "dark" else "light"}",
                width = 300,
                height = 100,
                dark = dark,
            ) {
                ShadcnTabs(
                    selectedValue = "account",
                    onSelectedChange = {},
                    modifier = Modifier.testTag("parity-tabs.track"),
                ) {
                    tab("account", "Account")
                    tab("password", "Password")
                }
            }
        }
    }

    @Test
    fun renderTextFieldShowcasePage() {
        writePreview(
            previewName = "awake-text-field-showcase-page",
            width = TextFieldPage.previewWidth,
            height = TextFieldPage.previewHeight,
        ) {
            TextFieldPage.hero(UiShowcaseRuntimeState())
        }
    }

    @Test
    fun renderComboboxShowcasePage() {
        writePreview(
            previewName = "awake-combobox-showcase-page",
            width = ComboboxPage.previewWidth,
            height = ComboboxPage.previewHeight,
            gpuPreview = true,
        ) {
            ComboboxPage.hero(UiShowcaseRuntimeState())
        }
    }

    @Test
    fun renderTextFieldShowcaseTypedPage() {
        writePreview(
            previewName = "awake-text-field-showcase-typed-page",
            width = TextFieldPage.previewWidth,
            height = TextFieldPage.previewHeight,
        ) {
            val name = TextFieldState("dsdsadasdasdasdas")
            val email = TextFieldState("asdasdasd")
            val bio = TextFieldState(
                "A longer biography that must remain inside the full-width textarea.\n"
                    + "This second line proves multiline layout and caret line metrics.",
            )
            shadcnMuted("Single-line and multi-line keyboard-driven text input controls with focus ring bounds.")
            Column(Modifier.fillMaxWidth()) {
                ShadcnCard(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShadcnText("Text Input & Area Interactive Preview")
                        ShadcnFieldLabel("Full Name")
                        ShadcnInput(name)
                        ShadcnFieldLabel("Email Address")
                        ShadcnInput(email)
                        ShadcnFieldLabel("Biography")
                        shadcnTextarea(bio)
                    }
                }
            }
        }
    }

    @Test
    fun textInputCodeSamplesDescribeTheirRenderedFields() {
        val textFieldCode = TextFieldPage.usageCode
        assertTrue("rememberTextFieldState()" in textFieldCode)
        assertTrue("ShadcnFieldLabel(\"Full Name\")" in textFieldCode)
        assertTrue("ShadcnFieldLabel(\"Email Address\")" in textFieldCode)
        assertTrue("ShadcnFieldLabel(\"Biography\")" in textFieldCode)
        assertTrue("shadcnTextarea(bio)" in textFieldCode)
        assertTrue("contentPadding = 0.dp" in textFieldCode)
        assertTrue("padding(8.dp)" in textFieldCode)

        val textareaCode = TextareaPage.usageCode
        assertTrue("ShadcnFieldLabel(\"Biography\")" in textareaCode)
        assertTrue("shadcnTextarea(bio)" in textareaCode)
        assertTrue("contentPadding = 0.dp" in textareaCode)
        assertTrue("padding(8.dp)" in textareaCode)
    }

    @Test
    fun renderTextFieldCatalogPage() {
        val nav = ShowcaseNavState().apply { selectedPageId = TextFieldPage.id }
        writePreview(
            previewName = "awake-text-field-catalog-page",
            width = 1200,
            height = 900,
        ) {
            ShowcasePageContent(UiShowcaseRuntimeState(), nav, showInlineMenu = false)
        }
    }

    @Test
    fun renderTextFieldCatalogShellPage() {
        val nav = ShowcaseNavState().apply { selectedPageId = TextFieldPage.id }
        writePreview(
            previewName = "awake-text-field-catalog-shell-page",
            width = 1200,
            height = 900,
        ) {
            Row(
                Modifier.padding(24.dp).fillMaxWidth().height(852.dp),
                horizontalArrangement = Arrangement.spacedByHorizontal(20.dp),
            ) {
                Box(Modifier.width(264.dp).height(852.dp)) {}
                Column(Modifier.weight(1f).height(852.dp)) {
                    shadcnSurface(Modifier.fillMaxWidth().height(852.dp)) {
                        ShowcasePageContent(UiShowcaseRuntimeState(), nav, showInlineMenu = false)
                    }
                }
            }
        }
    }

    @Test
    fun renderCompactShowcaseShell() {
        writePreview(
            previewName = "awake-showcase-compact-shell",
            width = 600,
            height = 900,
            settleFrames = 2,
        ) {
            ShowcaseApp(UiShowcaseRuntimeState())
        }
    }

    @Test
    fun renderDesktopShowcaseShell() {
        writePreview(
            previewName = "awake-showcase-desktop-shell",
            width = 1200,
            height = 900,
            settleFrames = 2,
        ) {
            ShowcaseApp(UiShowcaseRuntimeState())
        }
    }

    @Test
    fun renderDesktopShowcaseShellAfterScrollingBackUp() {
        writePreview(
            previewName = "awake-showcase-desktop-shell-scrolled-back-up",
            width = 1200,
            height = 900,
            settleFrames = 2,
            scrollDeltasY = List(30) { -1f } + List(15) { 1f },
            debugOverlay = true,
        ) {
            ShowcaseApp(UiShowcaseRuntimeState())
        }
    }

    @Test
    fun renderSidebarClipDiagnosticAfterScrollingBackUp() {
        writePreview(
            previewName = "awake-sidebar-clip-diagnostic-scrolled-back-up",
            width = 760,
            height = 760,
            settleFrames = 2,
            scrollDeltasY = List(18) { -1f } + List(9) { 1f },
            debugOverlay = true,
        ) {
            val menuScroll = rememberScrollState()
            Column {
                ShadcnText("OUTSIDE TOP")
                Spacer(Modifier.height(8.dp))
                shadcnSidebar(
                    Modifier.width(360.dp).height(560.dp),
                    header = {
                        ShadcnButton("PINNED HEADER", modifier = Modifier.fillMaxWidth())
                    },
                    footer = {
                        ShadcnButton("PINNED FOOTER", modifier = Modifier.fillMaxWidth())
                    },
                ) {
                    Column(Modifier.fillMaxWidth().verticalScroll(menuScroll)) {
                        Spacer(Modifier.height(72.dp))
                        repeat(14) { index ->
                            shadcnSidebarMenuItem("Menu item $index")
                            if (index == 4 || index == 9) Spacer(Modifier.height(56.dp))
                        }
                        Spacer(Modifier.height(72.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                ShadcnText("OUTSIDE BOTTOM")
            }
        }
    }

    @Test
    fun renderTextareaCatalogShellPage() {
        val nav = ShowcaseNavState().apply { selectedPageId = TextareaPage.id }
        writePreview(
            previewName = "awake-textarea-catalog-shell-page",
            width = 1200,
            height = 900,
        ) {
            Row(
                Modifier.padding(24.dp).fillMaxWidth().height(852.dp),
                horizontalArrangement = Arrangement.spacedByHorizontal(20.dp),
            ) {
                Box(Modifier.width(264.dp).height(852.dp)) {}
                Column(Modifier.weight(1f).height(852.dp)) {
                    shadcnSurface(Modifier.fillMaxWidth().height(852.dp)) {
                        ShowcasePageContent(UiShowcaseRuntimeState(), nav, showInlineMenu = false)
                    }
                }
            }
        }
    }

}
