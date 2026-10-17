/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/** Themes, skins and slots: layered, tokened, drawn per state with fades and frames, custom content, and a player's layout. */
class CanvasThemeTest {
    private val items = mutableListOf<CanvasItem>()
    private val host = ComposeHost()
    private val time = TestTimeSource()
    private var controls: CanvasControls? = null
    private var slotContent: Map<String, CanvasSlotContent> = emptyMap()

    private fun element(kind: CanvasElementKind, configure: CanvasElement.() -> Unit): CanvasElement =
        CanvasElement().apply { this.kind = kind; offsetX = 0f; offsetY = 0f; clock = time }.apply(configure)
            .also { items += CanvasItem(items.size + 1, it) }

    private fun frame(vararg touches: PointerFrame): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, pointers = touches.toList())) {
            CanvasOverlay(items, showTouchControls = true, controls = controls, slotContent = slotContent)
        }

    private fun FrameOutput.slots(name: String): List<SemanticsNode> = semantics.all().filter { it.testTag == "canvas-slot-$name" }

    @Test
    fun aSkinLayersPresetThenThemeThenTheElementsOwnFieldByField() {
        val theme = CanvasControlTheme(
            preset = CanvasThemePreset.MinimalFlat,
            skin = CanvasControlSkin(joystick = CanvasJoystickSkin(knob = CanvasSlot(size = 40f))),
        )
        val own = CanvasControlSkin(joystick = CanvasJoystickSkin(knob = CanvasSlot(style = CanvasStyle(background = "#112233"))))

        val knob = assertNotNull(theme.resolve(own)?.joystick?.knob)

        assertEquals(40f, knob.size, "the theme's size")
        assertEquals("#112233", knob.style?.background, "the element's colour")
        assertEquals(true, knob.circle, "the preset's shape")
        assertEquals(1.1f, knob.states[CanvasControlState.Pressed]?.scale, "and the preset's press")
    }

    @Test
    fun tokensNameTheThemesColoursAndImages() {
        val theme = CanvasControlTheme(
            preset = CanvasThemePreset.Textured,
            colors = mapOf("accent" to "#00FF00"),
            images = mapOf("knob" to CanvasImage("art/thumb.png", regionWidth = 64)),
            skin = CanvasControlSkin(button = CanvasButtonSkin(icon = CanvasSlot(style = CanvasStyle(image = CanvasImage("@knob", tint = "#FF000080"))))),
        )

        val skin = assertNotNull(theme.resolve(null))

        assertEquals("#00FF00", skin.joystick?.sprintRing?.style?.borderColor, "the theme's colour replaces the preset's token")
        assertEquals("art/thumb.png", skin.joystick?.knob?.style?.image?.path, "and its image the preset's")
        val icon = assertNotNull(skin.button?.icon?.style?.image)
        assertEquals(64, icon.regionWidth, "an image token is the whole image")
        assertEquals("#FF000080", icon.tint, "with what the slot sets over it")
        assertEquals("controls/pad.png", skin.joystick?.base?.style?.image?.path, "a token the theme leaves is the preset's")
    }

    @Test
    fun everyPresetNamesOnlyTokensItHasAndAThemeReportsOnesItDoesNot() {
        for (preset in CanvasThemePreset.entries) {
            assertEquals(emptyList(), CanvasControlTheme(preset = preset).problems(), "$preset")
        }
        val stray = CanvasControlTheme(
            preset = CanvasThemePreset.None,
            skin = CanvasControlSkin(look = CanvasLookSkin(feedback = CanvasSlot(style = CanvasStyle(background = "@nowhere")))),
        )
        assertEquals(listOf("skin names the token @nowhere, which neither the theme nor its preset has"), stray.problems())
    }

    @Test
    fun aThemedStickDrawsItsSlotsAndAPlainOneDrawsNone() {
        element(CanvasElementKind.Joystick) { width = 100f; height = 100f; joystick = CanvasJoystick(sprintThreshold = 0.8f) }
        assertTrue(frame().slots(CanvasSlots.JOYSTICK_KNOB).isEmpty(), "with no theme, the plain look")

        controls = CanvasControls(theme = CanvasControlTheme())
        val idle = frame()
        assertEquals(1, idle.slots(CanvasSlots.JOYSTICK_BASE).size)
        assertEquals(1, idle.slots(CanvasSlots.JOYSTICK_KNOB).size)
        assertTrue(idle.slots(CanvasSlots.JOYSTICK_SPRINT_RING).isEmpty(), "the ring shows only while sprinting")

        frame(PointerFrame(1, 50, 50, down = true, pressed = true))
        val sprinting = frame(PointerFrame(1, 50, 2, down = true))
        assertEquals(1, sprinting.slots(CanvasSlots.JOYSTICK_SPRINT_RING).size)
        assertEquals(1, sprinting.slots(CanvasSlots.JOYSTICK_ARROWS).size, "pushed, its arrow shows")
    }

    @Test
    fun aStateTheSlotDoesNotChangeFallsBackToABroaderOne() {
        val slot = CanvasSlot(states = mapOf(CanvasControlState.Pressed to CanvasSlotOverride(scale = 2f), CanvasControlState.Idle to CanvasSlotOverride(alpha = 0.5f)))

        assertEquals(2f, slot.overrideFor(CanvasControlState.Held)?.scale, "held falls back to pressed")
        assertEquals(2f, slot.overrideFor(CanvasControlState.Cancelling)?.scale, "cancelling to aiming, then pressed")
        assertEquals(0.5f, slot.overrideFor(CanvasControlState.CoolingDown)?.alpha, "and the rest to idle")
    }

    @Test
    fun aChangeOfStateFadesTheOldLookOutOverTheTransition() {
        val icon = CanvasSlot(style = CanvasStyle(background = "#FFFFFF"), transitionSeconds = 1f)
        val button = element(CanvasElementKind.Button) { width = 80f; height = 80f; button = CanvasButton(); skin = CanvasControlSkin(button = CanvasButtonSkin(icon = icon)) }
        assertEquals(1, frame().slots(CanvasSlots.BUTTON_ICON).size)

        button.enabled = false
        assertEquals(2, frame().slots(CanvasSlots.BUTTON_ICON).size, "both looks draw while one fades into the other")
        time += 500.milliseconds
        assertEquals(2, frame().slots(CanvasSlots.BUTTON_ICON).size)
        time += 600.milliseconds
        assertEquals(1, frame().slots(CanvasSlots.BUTTON_ICON).size, "after the fade, only the new one")
    }

    @Test
    fun framesPlayAtTheirRateAndHoldOrLoop() {
        val frames = CanvasFrames(listOf(CanvasImage("a.png"), CanvasImage("b.png"), CanvasImage("c.png")), fps = 10f)

        assertEquals("a.png", frames.imageAt(0f)?.path)
        assertEquals("b.png", frames.imageAt(0.15f)?.path)
        assertEquals("a.png", frames.imageAt(0.3f)?.path, "it loops")
        assertEquals("c.png", frames.copy(loop = false).imageAt(5f)?.path, "or holds the last")
    }

    @Test
    fun aCustomSlotIsGivenTheControlsLiveState() {
        val seen = mutableListOf<CanvasControlState>()
        slotContent = mapOf("my-knob" to { info -> seen += info.state })
        val stick = element(CanvasElementKind.Joystick) {
            width = 100f; height = 100f
            skin = CanvasControlSkin(joystick = CanvasJoystickSkin(knob = CanvasSlot(custom = "my-knob")))
        }

        frame()
        frame(PointerFrame(1, 50, 50, down = true, pressed = true))
        frame(PointerFrame(1, 60, 50, down = true))

        assertEquals(CanvasControlState.Idle, seen.first())
        assertEquals(CanvasControlState.Pressed, seen.last())
        stick.previewState = CanvasControlState.Disabled
        frame(PointerFrame(1, 60, 50, down = true))
        assertEquals(CanvasControlState.Disabled, seen.last(), "an editor's preview state wins")
    }

    @Test
    fun aPlayersLayoutMovesScalesAndFadesControls() {
        val jump = element(CanvasElementKind.Button) { anchor = CanvasAnchor.BottomRight; width = 80f; height = 80f; touchOnly = true; controlId = "jump" }
        val label = element(CanvasElementKind.Text) { width = 80f; height = 20f }
        val plain = frame()
        assertEquals(320, plain.semantics.all().first { it.testTag == "canvas-element-1" }.x)

        controls = CanvasControls(playerLayout = CanvasPlayerLayout(scale = 1.5f).moved("jump", dx = -40f, dy = 0f))
        val moved = frame().semantics.all().first { it.testTag == "canvas-element-1" }

        assertEquals(120, moved.width, "every touch control is half as big again")
        assertEquals(400 - 120 - 40, moved.x, "and this one moved 40 left, still against its corner")
        assertEquals(80, frame().semantics.all().first { it.testTag == "canvas-element-2" }.width, "a control that is not touch-only keeps its size")
        assertNull(CanvasPlayerLayout().adjustOf(label))
        assertTrue(jump.screenBounds!!.width > 119f, "its box is reported at its new size")
    }

    @Test
    fun aPlayersLayoutSavesAsJson() {
        val layout = CanvasPlayerLayout(scale = 1.2f, opacity = 0.7f).moved("fire", 12f, -8f)

        assertEquals(layout, CanvasPlayerLayout.fromJson(layout.toJson()))
        assertEquals(listOf("controls.fire.scale must be above 0"), layout.copy(controls = mapOf("fire" to CanvasControlAdjust(scale = 0f))).problems())
    }

    private fun List<SemanticsNode>.all(): List<SemanticsNode> = flatMap { listOf(it) + it.children.all() }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300
    }
}
