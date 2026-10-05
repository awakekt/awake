/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.vector.ImageVector
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlin.jvm.JvmInline

/** The codec for a provider that stores no configuration. */
object NoProviderConfiguration : ProviderCodec {
    override val currentVersion: Int = 1

    override fun validate(configuration: ProviderConfiguration): List<ValidationMessage> = emptyList()
}

/** A toolbar control a plugin draws. The host places it and uses `metadata.displayName` as its tooltip. */
interface ToolbarProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.Toolbar
    override val codec: ProviderCodec get() = NoProviderConfiguration

    context(_: Composer)
    /** Draws the toolbar control inside the active composer. */
    fun content()
}

/**
 * A workspace a plugin adds to the editor's central area, such as a node graph or a level map.
 * The host owns the workspace switcher, labels the entry with `metadata.displayName`, and sizes
 * the canvas through [content]'s modifier.
 */
interface WorkspaceProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.Workspace
    override val codec: ProviderCodec get() = NoProviderConfiguration

    context(_: Composer)
    /**
     * Draws the workspace canvas inside [modifier].
     *
     * @param modifier Layout modifier applied to the workspace canvas container.
     */
    fun content(modifier: Modifier)
}

/** The host's open floating cards, as a card sees them. Cards are identified by their provider ID. */
interface FloatingCardDeck {
    /**
     * Opens the floating card designated by [card] with an optional initialization [payload].
     *
     * @param card Unique identifier of the card provider to open.
     * @param payload Optional initialization data passed to the card.
     */
    fun open(card: ProviderId, payload: Any? = null)

    /**
     * Closes the floating card designated by [card].
     *
     * @param card Unique identifier of the card provider to close.
     */
    fun close(card: ProviderId)

    /**
     * Returns whether the floating card designated by [card] is currently open.
     *
     * @param card Unique identifier of the card provider to query.
     * @return `true` if the card is open, `false` otherwise.
     */
    fun isOpen(card: ProviderId): Boolean
}

/**
 * A floating card a plugin draws over the viewport. The host owns the deck: where cards dock, their
 * order, and their frame, which shows [icon] and `metadata.displayName`.
 */
interface FloatingCardProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.FloatingCard
    override val codec: ProviderCodec get() = NoProviderConfiguration

    /** Glyph for the card header, or null for none. */
    val icon: ImageVector? get() = null

    context(_: Composer)
    /**
     * Draws the card body. [payload] is whatever the card was opened with.
     *
     * @param deck The active floating card deck managing open cards.
     * @param payload Optional caller-supplied argument when the card was opened.
     */
    fun content(deck: FloatingCardDeck, payload: Any?)
}

/**
 * Stable identity of a keyboard action, e.g. `com.example.weather.toggle-rain`.
 *
 * @property value Underlying string representation of the action ID.
 */
@JvmInline
value class ActionId(val value: String) {
    init {
        require(value.isNotBlank()) { "Action ID must not be blank." }
    }
}

/**
 * A key plus modifiers. The host decides how a platform's command key maps to [ctrl].
 *
 * @property key The primary keyboard key.
 * @property ctrl Whether the Control modifier key must be held.
 * @property shift Whether the Shift modifier key must be held.
 * @property alt Whether the Alt modifier key must be held.
 */
data class KeyChord(
    val key: Key,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
)

/**
 * One keyboard action. The host owns the keymap: user rebinding, conflict handling, and when
 * [execute] runs. [execute] returns true when it handled the key.
 *
 * @property id Stable action identifier.
 * @property displayName Human-readable action name presented in settings and menus.
 * @property defaultChords Default key chords assigned to trigger this action.
 * @property category Grouping category in the shortcut configuration UI.
 * @property execute Callback executed when triggered, returning `true` if consumed.
 */
data class Keybinding(
    val id: ActionId,
    val displayName: String,
    val defaultChords: List<KeyChord>,
    val category: String = "General",
    val execute: () -> Boolean,
)

/** Keyboard actions a plugin contributes. */
interface KeybindingProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.Keybinding
    override val codec: ProviderCodec get() = NoProviderConfiguration

    /** Keybindings contributed by this provider. */
    val bindings: List<Keybinding>
}

/**
 * An entity a user can insert from the editor. [configure] adds scene components to the new
 * entity; it never creates GPU resources, which the render systems build from those components.
 *
 * @property id Unique identifier of this entity template.
 * @property displayName Human-readable template name shown in creation menus.
 * @property category Submenu or palette category under which this template is listed.
 * @property defaultName Initial display name assigned to entities instantiated from this template.
 * @property configure Callback initializing components on the newly created entity.
 */
data class EntityTemplate(
    val id: String,
    val displayName: String,
    val category: String,
    val defaultName: String = displayName,
    val configure: (world: World, entity: Entity) -> Unit,
)

/** An insertable entity template a plugin contributes. The host owns the menu and the insertion. */
interface EntityTemplateProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.EntityTemplate
    override val codec: ProviderCodec get() = NoProviderConfiguration

    /** Entity template contributed by this provider. */
    val template: EntityTemplate
}
