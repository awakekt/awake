/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.LayoutScopeMarker
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
 * Both, deliberately, though not for the reason an earlier version of this comment gave. It claimed
 * `@DslMarker` only constrains receivers. Checked against Kotlin 2.4.10, three things are true:
 *
 * - A context parameter never exposes its type's members implicitly. `context(ColumnScope)` does
 *   **not** make `weight()` callable, with or without a marker -- that is the change from context
 *   *receivers*. So a scope has to be a receiver for `weight(1f)` to read as itself.
 * - A context *requirement* does resolve outward through nesting: a `context(_: ColumnScope) fun`
 *   is reachable inside a nested Row, because the ColumnScope is still in scope.
 * - `@DslMarker` **does** stop that, and says so -- "cannot be called in this context with an
 *   implicit receiver".
 *
 * So the marker earns its place on both mechanisms; the receiver is what makes the DSL read.
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
