/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.uishowcase.ui.pages.inputs

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.heroicons.icon.HeroIcons
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcaseCategory
import io.github.awakelab.awake.sample.uishowcase.ui.ShowcasePage
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonGroup
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonGroupOrientation
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonGroupSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.shadcnIcon
import io.github.awakelab.awake.ui.shadcn.components.shadcnMuted

private val outline = HeroIcons.Outline24

/** Which button-group action is currently selected in the hero demo. */
private class SelectedAction {
    var value: String = "Save"
}

internal val ButtonGroupPage = ShowcasePage(
    id = "button-group",
    title = "Button Group",
    category = ShowcaseCategory.Inputs,
    description = "Buttons joined into a single control, sharing an outer border and corner radius.",
    usageCode = """
ShadcnButtonGroup {
    button("Left", variant = ShadcnButtonVariant.Outline)
    button("Middle", variant = ShadcnButtonVariant.Outline)
    button("Right", variant = ShadcnButtonVariant.Outline)
}
    """.trimIndent(),
    referenceExample = "registry/new-york-v4/examples/button-group-demo.tsx",
    previewHeight = 580,
    notes = listOf(
        "Supports unified orientation control via ShadcnButtonGroupOrientation (Horizontal / Vertical).",
        "Joined buttons use BorderSides and corner radii to share seamless 1px boundary dividers.",
        "Use separator() or hairline dividers when explicit separators are needed between buttons.",
    ),
    hero = {
        val selectedAction = remember { SelectedAction() }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShadcnButtonGroup {
                button(
                    "Save",
                    variant = if (selectedAction.value == "Save") ShadcnButtonVariant.Default else ShadcnButtonVariant.Outline,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = { selectedAction.value = "Save" },
                )
                button(
                    "Play",
                    variant = if (selectedAction.value == "Play") ShadcnButtonVariant.Default else ShadcnButtonVariant.Outline,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = { selectedAction.value = "Play" },
                )
                button(
                    "Console",
                    variant = if (selectedAction.value == "Console") ShadcnButtonVariant.Default else ShadcnButtonVariant.Outline,
                    size = ShadcnButtonSizeVariant.Sm,
                    onClick = { selectedAction.value = "Console" },
                )
            }
            shadcnMuted("Selected action: ${selectedAction.value}")
        }
    },
    variants = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ShadcnText("Horizontal Group (Toolbar / Segmented Control)")
            ShadcnButtonGroup {
                button("", variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Icon, content = {
                    shadcnIcon(outline.cursorArrowRays, modifier = Modifier.size(16.dp))
                })
                button("", variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Icon, content = {
                    shadcnIcon(outline.arrowsPointingOut, modifier = Modifier.size(16.dp))
                })
                button("", variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Icon, content = {
                    shadcnIcon(outline.arrowPath, modifier = Modifier.size(16.dp))
                })
                button("", variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Icon, content = {
                    shadcnIcon(outline.arrowsPointingIn, modifier = Modifier.size(16.dp))
                })
            }

            Spacer(Modifier.height(8.dp))

            ShadcnText("Vertical Group (Unified Orientation Parameter)")
            ShadcnButtonGroup(
                orientation = ShadcnButtonGroupOrientation.Vertical,
                modifier = Modifier.width(120.dp),
            ) {
                button("Top", variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Sm)
                button("Center", variant = ShadcnButtonVariant.Default, size = ShadcnButtonSizeVariant.Sm)
                button("Bottom", variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Sm)
            }
        }
    },
    states = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShadcnText("Subtle / Secondary Variant Group")
            ShadcnButtonGroup {
                button("Option A", variant = ShadcnButtonVariant.Secondary, size = ShadcnButtonSizeVariant.Sm)
                button("Option B", variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Sm)
                button("Option C", variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Sm)
            }
        }
    },
)
