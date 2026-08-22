// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.designsystem

import io.github.ronjunevaldoz.awake.ui.api.layout.Dimension
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.core.math2d.px
import io.github.ronjunevaldoz.awake.ui.designsystem.components.ShadcnDropdownMenuItem
import io.github.ronjunevaldoz.awake.ui.designsystem.components.shadcnDropdownMenu
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the border-box translation rule for bordered shadcn surfaces.
 *
 * shadcn sizes with CSS `border-box`: a `border` consumes layout space, so a container that is
 * `p-1` plus a 1px border insets its content by 5px, not 4px. Awake's border does not work that
 * way -- it is paint-only and reserves no layout space (`surfaceCore` insets by
 * `resolved.contentPadding` alone, never `borderWidth`), which is deliberately Compose-faithful:
 * Compose's `Modifier.border` is a draw modifier, not a layout one, which is why Compose code
 * chains `.border(...).padding(...)` when it wants content inset.
 *
 * The consequence is a translation rule, not a core bug: a shadcn recipe carrying a border must
 * fold that border width into its own `contentPadding`. This test pins the arithmetic for
 * `shadcnDropdownMenu`, whose drift is what surfaced the rule -- items previously sat at x=4
 * width=152 inside a 160px surface instead of the reference's x=5 width=150.
 *
 * Nine other bordered styles share the same shape (`shadcnAlertStyle`, `shadcnTableStyle`,
 * `shadcnToastStyle`, `shadcnKbdStyle`, `shadcnTabStyle`, `shadcnSurfaceStyle`,
 * `shadcnPopoverContentStyle`, `shadcnSheetSurfaceStyle`, `shadcnDrawerSurfaceStyle`) and are NOT
 * covered here -- none has a registered parity case yet, so there is no captured reference to
 * assert against, and guessing one would pin an unverified number. Extend this test as each gains
 * real reference evidence.
 */
class ShadcnBorderBoxInsetTest {

    @Test
    fun dropdownItemsAreInsetByBorderPlusPaddingNotPaddingAlone() {
        val surfaceWidth = 160f
        // shadcn's DropdownMenuContent: `p-1` (4dp) + a 1dp border, which border-box makes a 5dp
        // content inset on every side.
        val expectedInset = 5f

        var surface: Rectangle? = null
        var firstItem: Rectangle? = null

        val frame = renderShadcnComponent(width = 400f, height = 300f) { _ ->
            shadcnDropdownMenu(
                id = "border-box-dropdown",
                anchorSlot = Rectangle(0f, 0f, surfaceWidth, 0f),
                expanded = true,
                items = listOf(
                    ShadcnDropdownMenuItem(label = "One"),
                    ShadcnDropdownMenuItem(label = "Two"),
                ),
                width = Dimension.Fixed(surfaceWidth.px),
            )
        }

        surface = frame.boundsOrNull("border-box-dropdown.surface")
        firstItem = frame.boundsOrNull("border-box-dropdown.item.0")

        val surfaceBounds = requireNotNull(surface) { "dropdown surface did not report semantic bounds" }
        val itemBounds = requireNotNull(firstItem) { "dropdown item.0 did not report semantic bounds" }

        assertEquals(
            expectedInset,
            itemBounds.x - surfaceBounds.x,
            0.01f,
            "item must be inset by border(1) + padding(4); a 4px inset means the border width was dropped",
        )
        assertEquals(
            surfaceBounds.width - expectedInset * 2f,
            itemBounds.width,
            0.01f,
            "item width must lose both side insets; 152 inside a 160 surface means border was not counted",
        )
    }
}
