/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeComposer {
    val emitted = mutableListOf<String>()
}

context(composer: FakeComposer)
private fun text(value: String) {
    composer.emitted += value
}

context(composer: FakeComposer)
private fun column(content: () -> Unit) {
    composer.emitted += "column{"
    content()
    composer.emitted += "}"
}

/** The marker form -- "require a composer in scope, never reference it". */
context(_: FakeComposer)
private fun spacing(multiplier: Int): Int = multiplier * BASE_SPACING

private const val BASE_SPACING = 4

/**
 * A context parameter is this engine's `@Composable` -- the calling convention with no compiler
 * plugin to version-lock against Kotlin.
 *
 * Runs on all five targets because wasm and Native lower IR differently, so a regression shows up
 * here by name rather than as an unexplained engine build failure.
 */
class ContextParameterCallingConventionTest {

    @Test
    fun aPlainLambdaBodySeesTheEnclosingComposerContext() {
        val composer = FakeComposer()
        with(composer) {
            column {
                // The nesting that makes `Column { Text(...) }` work: `column`'s context parameter
                // is in lexical scope inside its content lambda.
                text("hi")
            }
        }
        assertEquals(listOf("column{", "hi", "}"), composer.emitted)
    }

    @Test
    fun anonymousContextParametersResolve() {
        val composer = FakeComposer()
        with(composer) {
            assertEquals(8, spacing(2))
        }
    }
}
