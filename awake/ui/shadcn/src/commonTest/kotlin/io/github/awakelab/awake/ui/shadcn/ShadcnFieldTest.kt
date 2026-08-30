/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.compose.ui.semantics.SemanticsTreeBuilder
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.ui.shadcn.components.ShadcnFieldOrientation
import io.github.awakelab.awake.ui.shadcn.components.shadcnField
import io.github.awakelab.awake.ui.shadcn.components.shadcnFieldContent
import io.github.awakelab.awake.ui.shadcn.components.shadcnFieldError
import io.github.awakelab.awake.ui.shadcn.components.shadcnFieldGroup
import io.github.awakelab.awake.ui.shadcn.components.ShadcnFieldLabel
import io.github.awakelab.awake.ui.shadcn.components.shadcnFieldSet
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Field family's spacing, against numbers read out of the browser.
 *
 * Every constant here came from `getBoundingClientRect`/`getComputedStyle` on the `field-anatomy`
 * reference case, not from reading Tailwind's scale and hoping. That is the difference that caught
 * three defects on the first pass: a legend missing its `mb-3` (36px to the group upstream, 24 in
 * the port), and a description and an error message rendered at weight 500 where the browser says
 * 400.
 *
 * The gaps are what this component *is*. A field's own `gap-3` against a group's `gap-7` is what
 * makes a form read as groups of related lines rather than one evenly spaced list, so an assertion
 * that only checked "the parts are present" would pass on a form nobody can scan.
 */
class ShadcnFieldTest {

    private val width = 420

    private fun frame(content: context(Composer) () -> Unit): List<SemanticsNode> {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) { provideShadcnTheme(shadcnThemeValues(dark = true)) { content() } }
        root.layoutTree(Constraints.of(width, width, 0, 800))
        return SemanticsTreeBuilder().build(root)
    }

    private fun List<SemanticsNode>.tag(tag: String): SemanticsNode =
        firstNotNullOfOrNull { search(it, tag) } ?: error("no node tagged '$tag'")

    private fun search(node: SemanticsNode, tag: String): SemanticsNode? =
        if (node.testTag == tag) node else node.children.firstNotNullOfOrNull { search(it, tag) }

    @Test
    fun aFieldSpacesItsPartsBy12() {
        val nodes = frame {
            shadcnField {
                Spacer(Modifier.fillMaxWidth().height(10.dp).testTag("first"))
                Spacer(Modifier.fillMaxWidth().height(10.dp).testTag("second"))
            }
        }
        val first = nodes.tag("first")
        val second = nodes.tag("second")

        assertEquals(FIELD_GAP, second.y - (first.y + first.height), "a field is `gap-3`")
    }

    @Test
    fun aGroupSpacesItsFieldsWiderThanAFieldSpacesItsParts() {
        val nodes = frame {
            shadcnFieldGroup {
                Spacer(Modifier.fillMaxWidth().height(10.dp).testTag("one"))
                Spacer(Modifier.fillMaxWidth().height(10.dp).testTag("two"))
            }
        }
        val one = nodes.tag("one")
        val two = nodes.tag("two")

        assertEquals(GROUP_GAP, two.y - (one.y + one.height), "a group is `gap-7`")
        assertTrue(GROUP_GAP > FIELD_GAP, "the two gaps must differ or the grouping is invisible")
    }

    @Test
    fun aSetSpacesItsChildrenBy24() {
        val nodes = frame {
            shadcnFieldSet {
                Spacer(Modifier.fillMaxWidth().height(10.dp).testTag("a"))
                Spacer(Modifier.fillMaxWidth().height(10.dp).testTag("b"))
            }
        }
        val a = nodes.tag("a")
        val b = nodes.tag("b")

        assertEquals(SET_GAP, b.y - (a.y + a.height), "a field set is `gap-6`")
    }

    @Test
    fun aFieldContentColumnTakesTheWidthTheControlLeaves() {
        val nodes = frame {
            shadcnField(orientation = ShadcnFieldOrientation.Horizontal) {
                shadcnFieldContent(Modifier.fillRemaining().testTag("content")) {
                    ShadcnFieldLabel("Notifications")
                }
                Spacer(Modifier.height(20.dp).testTag("control"))
            }
        }
        val content = nodes.tag("content")

        // `flex-1`: everything except the control and the 12px gap. The control here is a
        // zero-width spacer, so the whole width less the gap.
        assertEquals(width - FIELD_GAP, content.width, "the content column did not claim the slack")
    }

    @Test
    fun anEmptyErrorDrawsNothing() {
        // Upstream returns null rather than an empty row. A field that reserves space for an error
        // moves its whole form the first time one appears.
        val nodes = frame {
            shadcnField {
                ShadcnFieldLabel("Handle")
                shadcnFieldError(emptyList(), Modifier.testTag("error"))
            }
        }

        assertEquals(null, nodes.firstNotNullOfOrNull { search(it, "error") })
    }

    @Test
    fun repeatedErrorMessagesCollapseToOne() {
        // Two rules on one input failing with the same text is the normal case with a form library,
        // which is why upstream de-duplicates rather than listing it twice.
        val nodes = frame {
            shadcnField {
                shadcnFieldError(
                    listOf("Handle is required", "Handle is required", "  "),
                    Modifier.testTag("error"),
                )
            }
        }
        val error = nodes.tag("error")

        assertEquals(1, error.children.size, "duplicates and blanks must not each take a line")
    }

    private companion object {
        /** `gap-3`, measured at 12px. */
        const val FIELD_GAP = 12

        /** `gap-7`, measured at 28px. */
        const val GROUP_GAP = 28

        /** `gap-6`, measured at 24px. */
        const val SET_GAP = 24
    }
}
