/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.ContentScale
import com.awakekt.awake.compose.foundation.Image
import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.getValue
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.runtime.setValue
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/** The samples on the "UI with AwakeKt Compose" guide, each run the way the page describes. */
class ComposeDocsSampleTest {

    @Test
    fun composeFrameLaysOutTheCardWithItsPaddingAndGap() {
        // --8<-- [start:compose-frame]
        val frame = composeFrame(width = 320, height = 200) { PlayerCard("Harbor Town") }

        frame.onNodeWithTag("avatar").assertBoundsInRoot(ComposeTestBounds(left = 16, top = 16, width = 40, height = 40))
        // --8<-- [end:compose-frame]
        val title = frame.onNodeWithTag("title").getBoundsInRoot()
        assertEquals(16 + 40 + 12, title.left, "the row's 12 dp gap sits between avatar and title")
    }

    @Test
    fun rememberedStateSurvivesFromOneFrameToTheNext() {
        // --8<-- [start:session]
        val session = composeTestSession(width = 320, height = 120) { CounterScreen() }
        session.frame().onAllNodes(hasTestTag("reset")).assertCountEquals(0)

        session.click("increment").onNodeWithTag("reset").assertExists()
        session.click("reset").onAllNodes(hasTestTag("reset")).assertCountEquals(0)
        // --8<-- [end:session]
    }

    @Test
    fun anImageFillsItsNodeAndIsDescribed() {
        val frame = composeFrame(64, 64) { Checker() }
        val pixels = frame.primitives.rasterize(64, 64)

        assertEquals(-1, pixels[0].toInt(), "the top-left quarter is the image's white pixel")
        frame.onNode(hasLabel("Checker")).assertExists()
    }
}

// --8<-- [start:layout]
context(_: Composer)
fun PlayerCard(name: String) {
    Row(
        modifier = Modifier.background(Color(0.1f, 0.1f, 0.12f, 1f)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedByHorizontal(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(Color(0.3f, 0.5f, 0.9f, 1f)).testTag("avatar"))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, Modifier.testTag("title"))
            Text("Level 3")
        }
    }
}
// --8<-- [end:layout]

// --8<-- [start:state]
context(_: Composer)
fun CounterScreen() {
    var count by remember { mutableStateOf(0) }
    Counter(count, onIncrement = { count++ }, onReset = { count = 0 })
}

context(_: Composer)
fun Counter(count: Int, onIncrement: () -> Unit, onReset: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedByHorizontal(8.dp)) {
        Text("Count: $count")
        Box(Modifier.testTag("increment").clickable(onClick = onIncrement).padding(8.dp)) { Text("+") }
        if (count > 0) {
            Box(Modifier.testTag("reset").clickable(onClick = onReset).padding(8.dp)) { Text("Reset") }
        }
    }
}
// --8<-- [end:state]

// --8<-- [start:image]
/** 2 x 2 RGBA8, rows top to bottom: white, black, then black, white. */
private val checker = ImageBitmap(
    width = 2,
    height = 2,
    pixels = byteArrayOf(
        -1, -1, -1, -1, 0, 0, 0, -1,
        0, 0, 0, -1, -1, -1, -1, -1,
    ),
)

context(_: Composer)
fun Checker() {
    Image(checker, contentDescription = "Checker", modifier = Modifier.size(64.dp), contentScale = ContentScale.FillBounds)
}
// --8<-- [end:image]
