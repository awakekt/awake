// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui

import io.github.ronjunevaldoz.awake.compose.foundation.layout.LayoutScopeMarker
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeComposer {
    val emitted = mutableListOf<String>()
}

private interface FakeModifier {
    companion object : FakeModifier
}

@LayoutScopeMarker
private interface FakeRowScope {
    fun FakeModifier.weight(value: Float): String = "weight($value)"
}

@LayoutScopeMarker
private interface FakeColumnScope {
    fun FakeModifier.alignStart(): String = "alignStart"
}

context(composer: FakeComposer)
private fun fakeRow(content: FakeRowScope.() -> Unit) {
    composer.emitted += "row{"
    object : FakeRowScope {}.content()
    composer.emitted += "}"
}

context(composer: FakeComposer)
private fun fakeColumn(content: FakeColumnScope.() -> Unit) {
    composer.emitted += "column{"
    object : FakeColumnScope {}.content()
    composer.emitted += "}"
}

context(composer: FakeComposer)
private fun record(value: String) {
    composer.emitted += value
}

/**
 * The scope is a **receiver** on the content lambda; the composer is a **context parameter**.
 *
 * Both, deliberately: `@DslMarker` only makes implicit *receivers* mutually exclusive. Two context
 * parameters of different scope types would both stay resolvable, which is exactly the leak the
 * marker exists to stop.
 *
 * The rejection itself is a compile error, so it cannot be asserted here. Verified out-of-band
 * against Kotlin 2.4.10 -- calling a `ColumnScope` member inside a nested `Row` gives
 * `'fun FakeModifier.alignStart(): String' cannot be called in this context with an implicit
 * receiver`. What this test covers is the half that can silently break: that the composer context
 * still resolves through nested receiver lambdas.
 */
class LayoutScopeMarkerTest {

    @Test
    fun theComposerContextSurvivesNestedScopeReceivers() {
        val composer = FakeComposer()
        with(composer) {
            fakeColumn {
                record(FakeModifier.alignStart())
                fakeRow {
                    // Two receiver lambdas deep, and `record` still resolves its composer.
                    record(FakeModifier.weight(1f))
                }
            }
        }
        assertEquals(
            listOf("column{", "alignStart", "row{", "weight(1.0)", "}", "}"),
            composer.emitted,
        )
    }
}
