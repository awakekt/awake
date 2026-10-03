/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.plugin

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.core.di.Container
import com.awakekt.awake.core.di.Module
import com.awakekt.awake.core.di.container
import com.awakekt.awake.core.di.module
import kotlin.jvm.JvmInline

@JvmInline
value class ProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "A provider ID must not be blank." }
    }
}

enum class EditorProviderKind {
    Component,
    Asset,
    Environment,
    Animation,
    Build,

    /** A panel in the bottom tray. Implement [PanelProvider] to draw it. */
    BottomPanel,

    /** A toolbar control. Implement [ToolbarProvider] to draw it. */
    Toolbar,

    /** A panel in the left sidebar, beside the hierarchy. Implement [PanelProvider] to draw it. */
    Sidebar,

    /** A panel in the right inspector, beside the entity inspector. Implement [PanelProvider] to draw it. */
    InspectorPanel,

    /** Keyboard actions. Implement [KeybindingProvider]. */
    Keybinding,

    /** A central workspace canvas, such as a node graph. Implement [WorkspaceProvider] to draw it. */
    Workspace,

    /** An insertable entity template. Implement [EntityTemplateProvider]. */
    EntityTemplate,

    /** ECS simulation systems contributed to the active scene loop by an EditorPlugin. */
    SceneSystems,

    /** Interactive tools (e.g. terrain brush, foliage scatter, vertex painter) that receive viewport hover/drag. */
    ViewportTool,

    /** A floating card over the viewport. Implement [FloatingCardProvider] to draw it. */
    FloatingCard,
}

data class ProviderMetadata(
    val id: ProviderId,
    val displayName: String,
) {
    init {
        require(displayName.isNotBlank()) { "A provider display name must not be blank." }
    }
}

data class ProviderConfiguration(
    val version: Int,
    val payload: String,
) {
    init {
        require(version >= 1) { "A provider configuration version must be positive." }
    }
}

enum class ValidationSeverity { Warning, Error }

data class ValidationMessage(
    val severity: ValidationSeverity,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "A validation message must not be blank." }
    }
}

/** Versioned codec contract. Persisted payloads remain opaque to the generic editor. */
interface ProviderCodec {
    val currentVersion: Int

    fun validate(configuration: ProviderConfiguration): List<ValidationMessage>
}

/**
 * KMP-safe editor extension point. Providers own their policy and resources; the editor only
 * orders, resolves, validates, and disposes them.
 */
interface EditorProvider {
    val metadata: ProviderMetadata
    val kind: EditorProviderKind
    val codec: ProviderCodec

    fun dispose() {}
}

interface ComponentProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.Component
}

interface AssetProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.Asset
}

interface EnvironmentProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.Environment
}

interface AnimationProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.Animation
}

interface BuildProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.Build

    /** Cancels provider-owned work before editor shutdown or an explicit user cancellation. */
    fun cancel()
}

/**
 * A provider that draws its own panel, so a plugin can ship UI against this contract alone.
 *
 * Its [kind] picks the host slot and must be one of [PANEL_KINDS]. The host owns the tab, labels it
 * with [ProviderMetadata.displayName], and calls [content] inside it on every frame it is visible.
 */
interface PanelProvider : EditorProvider {
    override val codec: ProviderCodec get() = NoProviderConfiguration

    /** Draws the panel body. */
    context(_: Composer)
    fun content()

    companion object {
        /** The slots a [PanelProvider] may fill. */
        val PANEL_KINDS: Set<EditorProviderKind> = setOf(
            EditorProviderKind.BottomPanel,
            EditorProviderKind.Sidebar,
            EditorProviderKind.InspectorPanel,
        )
    }
}

/**
 * Ordered registry for the public editor provider kinds.
 *
 * IDs are global across kinds: an ambiguous provider ID would make persisted extension data
 * impossible to resolve safely. Registration order is preserved for deterministic presentation.
 */
@Suppress("TooManyFunctions")
class ProviderRegistry {
    private val ordered = mutableListOf<EditorProvider>()
    private val byId = mutableMapOf<ProviderId, EditorProvider>()
    private var disposed = false

    val all: List<EditorProvider> get() = ordered

    fun register(provider: EditorProvider) {
        registerAll(listOf(provider))
    }

    /**
     * Registers a batch atomically. This lets an extension contribute several providers without
     * leaving a partially registered capability behind when one of its IDs conflicts.
     */
    fun registerAll(providers: List<EditorProvider>) {
        check(!disposed) { "Cannot register a provider after the registry is disposed." }
        val duplicateId = providers
            .groupingBy { it.metadata.id }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateId == null) {
            "More than one provider was supplied for '${duplicateId?.value}'."
        }
        val registeredId = providers
            .asSequence()
            .map { it.metadata.id }
            .firstOrNull { it in byId }
        require(registeredId == null) {
            "A provider is already registered for '${registeredId?.value}'."
        }
        val misplacedPanel = providers.firstOrNull { it is PanelProvider && it.kind !in PanelProvider.PANEL_KINDS }
        require(misplacedPanel == null) {
            "Panel provider '${misplacedPanel?.metadata?.id?.value}' has kind ${misplacedPanel?.kind}, " +
                "which is not a panel slot."
        }
        providers.forEach { provider ->
            byId[provider.metadata.id] = provider
            ordered += provider
        }
    }

    /**
     * Unregisters a single provider and disposes it.
     */
    fun unregister(provider: EditorProvider): Boolean {
        if (byId.remove(provider.metadata.id) != null) {
            ordered.remove(provider)
            provider.dispose()
            return true
        }
        return false
    }

    /**
     * Unregisters a batch of providers and disposes them in reverse order.
     */
    fun unregisterAll(providers: List<EditorProvider>) {
        providers.asReversed().forEach(::unregister)
    }

    fun find(id: ProviderId): EditorProvider? = byId[id]

    fun ofKind(kind: EditorProviderKind): List<EditorProvider> = ordered.filter { it.kind == kind }

    fun validate(
        id: ProviderId,
        configuration: ProviderConfiguration,
    ): List<ValidationMessage> = requireNotNull(find(id)) {
        "No provider is registered for '${id.value}'."
    }.codec.validate(configuration)

    fun cancelBuilds() {
        ordered.filterIsInstance<BuildProvider>().forEach(BuildProvider::cancel)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        ordered.asReversed().forEach(EditorProvider::dispose)
        ordered.clear()
        byId.clear()
    }

    /** Exports registered providers as an Awake DI [Module]. */
    fun toDiModule(): Module = module {
        instance(this@ProviderRegistry)
        ordered.forEach { provider ->
            instance(provider, qualifier = provider.metadata.id.value)
        }
    }

    /** Creates an Awake DI [Container] populated with registered providers. */
    fun toContainer(): Container = container(toDiModule())
}

// ── Backward-compatible typealiases (compile-time only, zero runtime cost) ───
@Deprecated("Use ProviderId", ReplaceWith("ProviderId"))
typealias EditorProviderId = ProviderId

@Deprecated("Use ProviderMetadata", ReplaceWith("ProviderMetadata"))
typealias EditorProviderMetadata = ProviderMetadata

@Deprecated("Use ProviderConfiguration", ReplaceWith("ProviderConfiguration"))
typealias EditorProviderConfiguration = ProviderConfiguration

@Deprecated("Use ValidationSeverity", ReplaceWith("ValidationSeverity"))
typealias EditorValidationSeverity = ValidationSeverity

@Deprecated("Use ValidationMessage", ReplaceWith("ValidationMessage"))
typealias EditorValidationMessage = ValidationMessage

@Deprecated("Use ProviderCodec", ReplaceWith("ProviderCodec"))
typealias EditorProviderCodec = ProviderCodec

@Deprecated("Use ProviderRegistry", ReplaceWith("ProviderRegistry"))
typealias EditorProviders = ProviderRegistry

@Deprecated("Use ComponentProvider", ReplaceWith("ComponentProvider"))
typealias EditorComponentProvider = ComponentProvider

@Deprecated("Use AssetProvider", ReplaceWith("AssetProvider"))
typealias EditorAssetProvider = AssetProvider

@Deprecated("Use EnvironmentProvider", ReplaceWith("EnvironmentProvider"))
typealias EditorEnvironmentProvider = EnvironmentProvider

@Deprecated("Use AnimationProvider", ReplaceWith("AnimationProvider"))
typealias EditorAnimationProvider = AnimationProvider

@Deprecated("Use BuildProvider", ReplaceWith("BuildProvider"))
typealias EditorBuildProvider = BuildProvider
