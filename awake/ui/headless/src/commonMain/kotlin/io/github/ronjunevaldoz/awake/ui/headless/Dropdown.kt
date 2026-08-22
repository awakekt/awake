// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.headless

import io.github.ronjunevaldoz.awake.ui.UiSemanticRole
import io.github.ronjunevaldoz.awake.core.math2d.dp
import io.github.ronjunevaldoz.awake.ui.api.layout.Dimension
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.ui.headless.internal.controls.drawDropdownTriggerContent
import io.github.ronjunevaldoz.awake.ui.headless.internal.layout.withIntrinsicLabelWidth
import io.github.ronjunevaldoz.awake.ui.modifier.Modifier
import io.github.ronjunevaldoz.awake.ui.modifier.UiModifier
import io.github.ronjunevaldoz.awake.ui.modifier.fillMaxWidth
import io.github.ronjunevaldoz.awake.ui.modifier.height
import io.github.ronjunevaldoz.awake.core.math2d.px
import io.github.ronjunevaldoz.awake.ui.scope.recordSemantic
import io.github.ronjunevaldoz.awake.ui.style.Style
import io.github.ronjunevaldoz.awake.ui.headless.internal.controls.contextMenuTrigger as primitiveContextMenuTrigger
import io.github.ronjunevaldoz.awake.ui.headless.internal.controls.filterOptionsByQuery as primitiveFilterOptionsByQuery
import io.github.ronjunevaldoz.awake.ui.headless.internal.controls.select as primitiveSelect
import io.github.ronjunevaldoz.awake.ui.headless.internal.controls.buttonSlot as primitiveButtonSlot

/** Generic select behavior with a stable Headless receiver. */
fun UiScope.select(
    id: String,
    options: List<String>,
    selectedIndex: Int? = null,
    modifier: UiModifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    style: Style = Style.Empty,
    selectedStyle: Style? = null,
    optionStyle: Style? = null,
    surfaceStyle: Style? = null,
): Int? = primitive.primitiveSelect(
    id = id,
    options = options,
    selectedIndex = selectedIndex ?: -1,
    modifier = modifier,
    style = style,
    selectedStyle = selectedStyle,
    optionStyle = optionStyle,
    surfaceStyle = surfaceStyle,
    enabled = enabled,
    placeholder = placeholder,
)

/** Generic searchable combobox behavior; visual policy is supplied by the calling skin. */
fun UiScope.combobox(
    id: String,
    options: List<String>,
    selectedIndex: Int? = null,
    modifier: UiModifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    filterPlaceholder: String = "Search...",
    emptyLabel: String = "No results found.",
    style: Style = Style.Empty,
    selectedStyle: Style? = null,
    optionStyle: Style = Style.Empty,
    filterStyle: Style = Style.Empty,
    // The panel behind the filter field and option rows. Without one the popup draws no
    // background, border or radius at all and its rows float over whatever is beneath them --
    // there was previously no parameter for it, so no skin could supply one. `popup()` is pure
    // positioning by design, so the surface belongs to the caller the same way
    // `shadcnDropdownMenu` already wraps its own popup body.
    //
    // Null means no panel, not an unstyled one: `surface()` falls back to
    // `neutralSurfaceDefaults`, which carries a background and 8dp of padding, so wrapping
    // unconditionally would hand a caller who asked for nothing a grey panel and an inset it
    // never requested.
    surfaceStyle: Style? = null,
): Int? {
    val popupState = rememberPopupState(id, key = "expanded")
    val filterState = rememberStateValue(id, key = "filter") { "" }
    val selectedLabel = options.getOrNull(selectedIndex ?: -1) ?: placeholder
    // Same trigger sizing as primitiveSelect. buttonSlot only derives an intrinsic width from a
    // label passed to it, and this widget draws its label separately through
    // drawDropdownTriggerContent, so the slot never saw one: the trigger had no intrinsic size
    // and stretched to whatever its parent offered, overflowing any container narrower than the
    // available width. `extraWidth` reserves the 16dp chevron and 8dp gap that content draws.
    //
    // Sized to every label it can show, not just the current one. The popup takes the trigger's
    // width, so measuring only the selection let a short one ("Astro") shrink the panel below
    // what its own filter placeholder needed, which then rendered as "Search fra" -- and made
    // the trigger jump width on each pick.
    val triggerModifier = primitive.withIntrinsicLabelWidth(
        modifier = modifier,
        labels = options + placeholder + filterPlaceholder,
        style = Style.Empty,
        defaults = style,
        extraWidth = 24f.dp,
    )
    val trigger = primitive.primitiveButtonSlot(
        id = "$id.trigger",
        modifier = triggerModifier.height(modifier.heightDimension ?: Dimension.Fixed(36f.dp)),
        style = style,
        enabled = enabled,
    )
    if (trigger.clicked) {
        val opening = !popupState.expanded
        popupState.toggle()
        if (opening) filterState.value = ""
    }
    primitive.drawDropdownTriggerContent(
        slot = trigger.slot,
        label = selectedLabel,
        expanded = popupState.expanded,
        style = style,
        // The Dropdown node below owns `id`; the label is a separate text node. Passing `id`
        // here made both claim it, which is a duplicate semantic id. primitiveSelect has
        // always used the `.label` suffix -- this widget just never matched it.
        semanticId = "$id.label",
        isPlaceholder = selectedIndex == null,
    )
    primitive.recordSemantic(
        role = UiSemanticRole.Dropdown,
        id = id,
        label = selectedLabel,
        bounds = trigger.slot,
        selected = popupState.expanded,
    )

    var picked: Int? = null
    val popupResult = popup(
        id = id,
        anchorSlot = trigger.slot,
        expanded = popupState.expanded,
        width = Dimension.Fixed(trigger.slot.width.px),
        height = Dimension.WrapContent,
        positionProvider = UiPopupDefaults.dropdown(offsetY = 4f.dp),
    ) {
        val panelBody: ColumnScope.() -> Unit = {
            val query = textField(
                id = "$id.filter",
                value = filterState.value,
                placeholder = filterPlaceholder,
                modifier = Modifier.fillMaxWidth().height(36f.dp),
                style = filterStyle,
                enabled = enabled,
            )
            filterState.value = query
            separator(id = "$id.filterSeparator")
            val filtered = primitiveFilterOptionsByQuery(options, query)
            if (filtered.isEmpty()) {
                text(emptyLabel, modifier = Modifier.fillMaxWidth(), style = optionStyle)
            } else {
                filtered.forEach { indexed ->
                    // Layer, don't replace: a selected style carries the selection's own background
                // and foreground, not a whole row spec, so swapping it in wholesale dropped the
                // option padding and left the selected row's label flush against the panel edge.
                // primitiveSelect already composes these the same way.
                val rowStyle = if (indexed.index == selectedIndex && selectedStyle != null) {
                    optionStyle then selectedStyle
                } else {
                    optionStyle
                }
                    if (
                        button(
                            id = "$id.option.${indexed.index}",
                            label = indexed.value,
                            modifier = Modifier.fillMaxWidth().height(32f.dp),
                            style = rowStyle,
                            enabled = enabled,
                            // Start-aligned like menuItem -- see its note on button()'s
                            // centered default.
                            centered = false,
                            semanticRole = UiSemanticRole.MenuItem,
                        )
                    ) {
                        picked = indexed.index
                    }
                }
            }
        }
        if (surfaceStyle != null) {
            surface(id = "$id.surface", modifier = Modifier.fillMaxWidth(), style = surfaceStyle) { panelBody() }
        } else {
            panelBody()
        }
    }
    if (popupResult.dismissed || picked != null) {
        popupState.close()
        filterState.value = ""
    }
    return picked
}

/** Secondary-click detection and cursor anchoring without exposing Core's trigger type. */
fun UiScope.contextMenuTrigger(
    id: String,
    expanded: Boolean,
    target: Rectangle,
): HeadlessContextMenuTrigger {
    val trigger = primitive.primitiveContextMenuTrigger(id, expanded, target)
    return HeadlessContextMenuTrigger(trigger.shouldOpen, trigger.anchor)
}

data class HeadlessContextMenuTrigger(
    val shouldOpen: Boolean,
    val anchor: Rectangle,
)
