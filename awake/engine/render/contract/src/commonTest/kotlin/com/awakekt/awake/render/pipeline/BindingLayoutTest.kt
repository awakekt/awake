/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BindingLayoutTest {

    @Test
    fun standardLayoutPreservesTheCurrentSceneAbi() {
        assertEquals(0, BindingLayout.Standard.slot(BindingSemantic.Material))
        assertEquals(1, BindingLayout.Standard.slot(BindingSemantic.ShadowDepth))
        assertEquals(3, BindingLayout.Standard.slot(BindingSemantic.JointPalette))
    }

    @Test
    fun authoredLayoutCanMoveSemanticSlots() {
        val layout = BindingLayout.of(
            BindingSemantic.Material to 2,
            BindingSemantic.ShadowDepth to 4,
        )

        assertEquals(2, layout.slot(BindingSemantic.Material))
        assertEquals(4, layout.slot(BindingSemantic.ShadowDepth))
        assertFailsWith<IllegalArgumentException> {
            layout.slot(BindingSemantic.JointPalette)
        }
    }

    @Test
    fun duplicateSemanticDeclarationsFail() {
        assertFailsWith<IllegalArgumentException> {
            BindingLayout.of(
                BindingSemantic.Material to 0,
                BindingSemantic.Material to 1,
            )
        }
    }

    /** The point of the sealed hierarchy: a consumer names a group the engine does not know. */
    @Test
    fun aCustomSemanticTakesASlotAlongsideTheNamedOnes() {
        val splat = BindingSemantic.Custom("terrainSplat")
        val layout = BindingLayout.of(
            BindingSemantic.Material to 0,
            splat to 2,
        )

        assertEquals(2, layout.slot(splat))
        assertEquals(2, layout.slot(BindingSemantic.Custom("terrainSplat")))
        assertFalse(layout.contains(BindingSemantic.Custom("somethingElse")))
    }

    /** Value equality, not identity -- [BindingLayout] keys a map on these. */
    @Test
    fun customSemanticsWithTheSameNameCollide() {
        assertFailsWith<IllegalArgumentException> {
            BindingLayout.of(
                BindingSemantic.Custom("terrainSplat") to 0,
                BindingSemantic.Custom("terrainSplat") to 1,
            )
        }
    }

    @Test
    fun anUnnamedCustomSemanticFails() {
        assertFailsWith<IllegalArgumentException> { BindingSemantic.Custom(" ") }
    }
}
