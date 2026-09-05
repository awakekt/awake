/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.awakelab.awake.ui.builder.model

/**
 * Definition of a palette template available in the UI builder.
 */
data class UiComponentTemplate(
    val type: String,
    val category: String,
    val displayName: String,
    val isContainer: Boolean,
    val defaultProps: Map<String, String> = emptyMap(),
    val defaultStyle: UiStyleSpec = UiStyleSpec(),
)

/**
 * Registry of available component templates in Awake UI Builder.
 */
object UiComponentRegistry {
    val templates: List<UiComponentTemplate> = listOf(
        // Layout Containers
        UiComponentTemplate(
            type = "Container.Column",
            category = "Containers",
            displayName = "Column Stack",
            isContainer = true,
            defaultStyle = UiStyleSpec(width = "fill", height = "auto", padding = "8.dp", gap = "8.dp"),
        ),
        UiComponentTemplate(
            type = "Container.Row",
            category = "Containers",
            displayName = "Row Stack",
            isContainer = true,
            defaultStyle = UiStyleSpec(width = "fill", height = "auto", padding = "8.dp", gap = "8.dp"),
        ),
        UiComponentTemplate(
            type = "Container.Card",
            category = "Containers",
            displayName = "Card Panel",
            isContainer = true,
            defaultStyle = UiStyleSpec(width = "fill", height = "auto", padding = "16.dp", gap = "8.dp"),
        ),

        // Actions & Controls
        UiComponentTemplate(
            type = "Widget.ShadcnButton",
            category = "Actions",
            displayName = "Button",
            isContainer = false,
            defaultProps = mapOf("label" to "Button", "variant" to "default"),
        ),
        UiComponentTemplate(
            type = "Widget.ShadcnBadge",
            category = "Actions",
            displayName = "Badge",
            isContainer = false,
            defaultProps = mapOf("label" to "Badge", "variant" to "default"),
        ),

        // Forms & Inputs
        UiComponentTemplate(
            type = "Widget.ShadcnInput",
            category = "Inputs",
            displayName = "Input Field",
            isContainer = false,
            defaultProps = mapOf("placeholder" to "Enter text..."),
        ),
        UiComponentTemplate(
            type = "Widget.ShadcnSwitch",
            category = "Inputs",
            displayName = "Switch Toggle",
            isContainer = false,
            defaultProps = mapOf("label" to "Enable Option"),
        ),
        UiComponentTemplate(
            type = "Widget.ShadcnCheckbox",
            category = "Inputs",
            displayName = "Checkbox",
            isContainer = false,
            defaultProps = mapOf("label" to "Accept Terms"),
        ),

        // Typography & Display
        UiComponentTemplate(
            type = "Widget.ShadcnText",
            category = "Typography",
            displayName = "Text Heading",
            isContainer = false,
            defaultProps = mapOf("text" to "Heading Text"),
        ),
        UiComponentTemplate(
            type = "Widget.ShadcnSeparator",
            category = "Typography",
            displayName = "Separator",
            isContainer = false,
            defaultStyle = UiStyleSpec(width = "fill", height = "1.dp"),
        ),
    )

    fun findTemplate(type: String): UiComponentTemplate? = templates.firstOrNull { it.type == type }
}
