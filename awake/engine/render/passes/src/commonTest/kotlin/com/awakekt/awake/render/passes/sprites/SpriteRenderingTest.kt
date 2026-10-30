/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.sprites

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.passes.uniforms.SpriteExtraUniformLayout
import com.awakekt.awake.render.passes.uniforms.SpriteFields
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpriteRenderingTest {
    private val input = SpriteDrawInput("sheet", Mat4().translate(3f, 4f, 5f), columns = 2, rows = 2, frame = 3, pixelsPerUnit = 8f)

    @Test
    fun cellPixelsDetermineLocalSizeWithoutScalingTranslation() {
        val model = spriteModel(input, 32, 16)
        assertEquals(2f, model.data[0])
        assertEquals(1f, model.data[5])
        assertEquals(3f, model.data[12])
        assertEquals(4f, model.data[13])
        assertEquals(5f, model.data[14])
        assertEquals(1f, input.model.data[0], "the caller's world transform stays intact")
    }

    @Test
    fun frameAndFlipsStayWithinTheSameCellAndCarryAlphaTint() {
        val normal = spriteUniforms(input, 32, 16)
        val flipped = spriteUniforms(input.copy(flipX = true, flipY = true, tint = Color(0.2f, 0.4f, 0.6f, 0.5f)), 32, 16)
        assertContentEquals(floatArrayOf(0.5f, 0.5f, 0.5f, 0f), normal.copyOfRange(0, 4))
        assertContentEquals(floatArrayOf(-0.5f, -0.5f, 1f, 0.5f), flipped.copyOfRange(0, 4))
        val tint = SpriteExtraUniformLayout.offsetOf(SpriteFields.Tint)
        assertContentEquals(floatArrayOf(0.2f, 0.4f, 0.6f, 0.5f), flipped.copyOfRange(tint, tint + 4))
    }

    // A 2x2 sheet of 32x16 pixels at 8 pixels a unit: each cell is 2 units wide and 1 high.
    private val cell = input.copy(model = Mat4())

    /** Where the quad's corner at ([x], [y]) of SpriteQuadGeometry lands in the world. */
    private fun Mat4.corner(x: Float, y: Float): Pair<Float, Float> =
        (data[0] * x + data[4] * y + data[12]) to (data[1] * x + data[5] * y + data[13])

    private fun spans(input: SpriteDrawInput): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val model = spriteModel(input, 32, 16)
        val (left, bottom) = model.corner(-0.5f, -0.5f)
        val (right, top) = model.corner(0.5f, 0.5f)
        return (left to right) to (bottom to top)
    }

    @Test
    fun theDefaultPivotIsTheCentreAsBefore() {
        assertEquals((-1f to 1f) to (-0.5f to 0.5f), spans(cell))
    }

    @Test
    fun aPivotAtTheBottomMiddleStandsTheSpriteOnItsPosition() {
        assertEquals((-1f to 1f) to (0f to 1f), spans(cell.copy(pivotX = 0.5f, pivotY = 0f)))
    }

    @Test
    fun aPivotAtTheBottomLeftPutsTheSpriteUpAndToTheRight() {
        assertEquals((0f to 2f) to (0f to 1f), spans(cell.copy(pivotX = 0f, pivotY = 0f)))
    }

    @Test
    fun theSpriteTurnsAndScalesAboutItsPivot() {
        // A quarter turn and a doubling, placed at (3, 4): the pivot, the bottom-left corner, stays there.
        val turned = cell.copy(model = Mat4().translate(3f, 4f, 0f).rotateZ(HALF_PI).scale(2f, 2f, 1f), pivotX = 0f, pivotY = 0f)
        val model = spriteModel(turned, 32, 16)

        val (pivotX, pivotY) = model.corner(-0.5f, -0.5f)
        assertEquals(3f, pivotX, EPSILON)
        assertEquals(4f, pivotY, EPSILON)
        // The bottom-right corner, 2 units right of the pivot unturned, is 4 units above it turned and doubled.
        val (cornerX, cornerY) = model.corner(0.5f, -0.5f)
        assertEquals(3f, cornerX, EPSILON)
        assertEquals(8f, cornerY, EPSILON)
    }

    @Test
    fun aFlippedSpriteMirrorsAboutItsPivot() {
        // Pivot a quarter of the way in from the left: unflipped the cell spans -0.5..1.5, flipped -1.5..0.5.
        assertEquals((-0.5f to 1.5f) to (-0.5f to 0.5f), spans(cell.copy(pivotX = 0.25f)))
        assertEquals((-1.5f to 0.5f) to (-0.5f to 0.5f), spans(cell.copy(pivotX = 0.25f, flipX = true)))
        assertEquals((-1f to 1f) to (0f to 1f), spans(cell.copy(pivotY = 1f, flipY = true)))
    }

    @Test
    fun anUnboundedPivotIsRefusedAndAnUnsetOneIsTheCentre() {
        assertFailsWith<IllegalArgumentException> { spriteModel(cell.copy(pivotX = Float.NaN), 32, 16) }
        assertEquals(DEFAULT_SPRITE_PIVOT, SpriteDrawInput("sheet", Mat4()).pivotX)
    }

    @Test
    fun malformedSheetsFailBeforeTheyCanAddressAnotherCell() {
        for (bad in listOf(input.copy(columns = 0), input.copy(frame = 4), input.copy(columns = 65536, rows = 65536), input.copy(pixelsPerUnit = Float.NaN))) {
            assertFailsWith<IllegalArgumentException> { spriteUniforms(bad, 32, 16) }
        }
        assertFailsWith<IllegalArgumentException> { spriteUniforms(input, 31, 16) }
        assertFailsWith<IllegalArgumentException> { spriteModel(input.copy(worldSize = -0.1f), 32, 16) }
        assertFailsWith<IllegalArgumentException> { spriteModel(input.copy(worldSize = Float.NaN), 32, 16) }
    }

    @Test
    fun explicitWorldSizeScalesCellHeightAndPreservesCellAspectRatio() {
        // 32x16 sheet in 2x2 grid -> each cell is 16x8 (2:1 aspect). At worldSize = 0.5, width is 1.0 and height is 0.5.
        val model = spriteModel(cell.copy(worldSize = 0.5f), 32, 16)
        assertEquals(1f, model.data[0], EPSILON)
        assertEquals(0.5f, model.data[5], EPSILON)
    }

    private companion object {
        const val HALF_PI = (PI / 2).toFloat()
        const val EPSILON = 1e-5f
    }
}
