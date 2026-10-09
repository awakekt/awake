/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

// The tags below are written into the generated atlases; they do not describe this file.
// REUSE-IgnoreStart
private const val SNIPPET_BEGIN =
    "// SPDX-SnippetBegin\n" +
        "// SPDX-SnippetCopyrightText: 2011 The Roboto Project Authors (https://github.com/googlefonts/roboto-classic)\n" +
        "// SPDX-License-Identifier: OFL-1.1\n" +
        "\n"
private const val SNIPPET_END = "// SPDX-SnippetEnd\n"
// REUSE-IgnoreEnd

private const val OBJECT_KDOC = "\n/**\n"

/**
 * [source], one generated atlas file, with its object marked as Roboto's: an `OFL-1.1` SPDX snippet.
 *
 * An atlas packed from Roboto is the font in another format, which the SIL Open Font License counts
 * as a modified version of the font, so the object stays under the font's license. The package and
 * imports above it keep the file's Apache-2.0 header, which the repository's formatter owns.
 */
internal fun markRobotoSnippet(source: String): String {
    val kdoc = source.indexOf(OBJECT_KDOC)
    check(kdoc >= 0) { "The generated source has no object KDoc to start the Roboto license snippet at" }
    val objectStart = kdoc + 1
    return source.substring(0, objectStart) + SNIPPET_BEGIN + source.substring(objectStart) + SNIPPET_END
}
