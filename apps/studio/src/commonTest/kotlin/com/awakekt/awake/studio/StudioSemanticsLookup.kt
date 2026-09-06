/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio

import com.awakekt.awake.compose.ui.semantics.SemanticsNode

/**
 * Finds a tagged node anywhere in a frame's tree.
 *
 * Recursive because the compose tree keeps its nesting -- `ui-core` published one flat list, so a
 * `firstOrNull` over the top level used to be enough and silently stops being enough here.
 */
internal fun List<SemanticsNode>.findByTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { node ->
        if (node.testTag == tag) node else node.children.findByTag(tag)
    }
