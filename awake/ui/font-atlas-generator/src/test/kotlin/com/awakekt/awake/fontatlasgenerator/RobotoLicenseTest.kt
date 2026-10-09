/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The atlas object, and only it, carries Roboto's license. */
class RobotoLicenseTest {

    @Test
    fun wrapsTheObjectAndLeavesTheHeaderAndImportsOutside() {
        val generated = "/*\n * header\n */\npackage p\n\nimport kotlin.Int\n\n/**\n * Doc.\n */\ninternal object A {\n  val b: Int = 1\n}\n"

        // REUSE-IgnoreStart
        val expected = "/*\n * header\n */\npackage p\n\nimport kotlin.Int\n\n" +
            "// SPDX-SnippetBegin\n" +
            "// SPDX-SnippetCopyrightText: 2011 The Roboto Project Authors (https://github.com/googlefonts/roboto-classic)\n" +
            "// SPDX-License-Identifier: OFL-1.1\n" +
            "\n" +
            "/**\n * Doc.\n */\ninternal object A {\n  val b: Int = 1\n}\n" +
            "// SPDX-SnippetEnd\n"
        // REUSE-IgnoreEnd

        assertEquals(expected, markRobotoSnippet(generated))
    }

    @Test
    fun refusesSourceWithoutAnObjectKDoc() {
        assertFailsWith<IllegalStateException> { markRobotoSnippet("package p\n\ninternal object A\n") }
    }
}
