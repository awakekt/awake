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

/**
 * Unique identifier for an editor provider.
 *
 * @property value Underlying string representation of the provider ID.
 */
@JvmInline
value class ProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "A provider ID must not be blank." }
    }
}

/**
 * Categories of extension points supported by the editor.
 */
enum class EditorProviderKind {
    /** Inspector fields for a component type. Implement [ComponentInspectorProvider]. */
    Component,

    /** Asset pipeline extension or custom asset browser integration. */
    Asset,

    /** Environment lighting, skybox, or atmosphere configuration. */
    Environment,

    /** Skeletal or clip animation tool integration. */
    Animation,

    /** Build target or cooking pipeline contributor. */
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

    /** ECS systems that run on the edited world. Implement [SceneSystemsProvider]. */
    SceneSystems,

    /** A tool that takes viewport hover and drag, such as a brush. Implement [ViewportToolProvider]. */
    ViewportTool,

    /** A floating card over the viewport. Implement [FloatingCardProvider] to draw it. */
    FloatingCard,
}

/**
 * Identification and display metadata for an editor provider.
 *
 * @property id Globally unique provider identifier.
 * @property displayName Human-readable label displayed in editor UI surfaces.
 */
data class ProviderMetadata(
    val id: ProviderId,
    val displayName: String,
) {
    init {
        require(displayName.isNotBlank()) { "A provider display name must not be blank." }
    }
}

/**
 * Serialized configuration state associated with an editor provider.
 *
 * @property version Schema version number of the configuration payload.
 * @property payload Serialized string payload (e.g. JSON or binary text).
 */
data class ProviderConfiguration(
    val version: Int,
    val payload: String,
) {
    init {
        require(version >= 1) { "A provider configuration version must be positive." }
    }
}

/**
 * Severity level of an editor configuration validation finding.
 */
enum class ValidationSeverity {
    /** Non-fatal warning that does not prevent editor operations. */
    Warning,

    /** Critical error preventing valid execution or configuration save. */
    Error,
}

/**
 * Diagnostic message produced during provider configuration validation.
 *
 * @property severity Severity classification of the finding.
 * @property message Human-readable description of the validation issue.
 */
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
    /** The active configuration schema version handled by this codec. */
    val currentVersion: Int

    /**
     * Validates [configuration] against the provider's schema rules.
     *
     * @param configuration The configuration payload to validate.
     * @return List of validation findings, empty if configuration is valid.
     */
    fun validate(configuration: ProviderConfiguration): List<ValidationMessage>
}

/**
 * KMP-safe editor extension point. Providers own their policy and resources; the editor only
 * orders, resolves, validates, and disposes them.
 */
interface EditorProvider {
    /** Identification and display metadata for this provider. */
    val metadata: ProviderMetadata

    /** The category of editor slot or capability this provider contributes to. */
    val kind: EditorProviderKind

    /** Configuration serialization and validation codec. */
    val codec: ProviderCodec

    /** Releases any resources held by this provider upon deregistration or shutdown. */
    fun dispose() {}
}

/** Editor provider specializing in ECS component editing and inspection. */
interface ComponentProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.Component
}

/** Editor provider specializing in asset authoring, import, and management. */
interface AssetProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.Asset
}

/** Editor provider specializing in environment settings and atmosphere configuration. */
interface EnvironmentProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.Environment
}

/** Editor provider specializing in skeletal and clip animation editing. */
interface AnimationProvider : EditorProvider {
    override val kind: EditorProviderKind get() = EditorProviderKind.Animation
}

/** Editor provider specializing in asset cooking or packaging pipelines. */
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

    context(_: Composer)
    /** Draws the panel body inside the active composer. */
    fun content()

    /** Constants defining panel provider placement rules. */
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

    /** List of all currently registered editor providers in deterministic registration order. */
    val all: List<EditorProvider> get() = ordered

    /**
     * Registers a single provider in the registry.
     *
     * @param provider The editor provider to register.
     */
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

    /**
     * Looks up a registered provider by its unique [id].
     *
     * @param id Provider identifier to search for.
     * @return The matching [EditorProvider], or `null` if not registered.
     */
    fun find(id: ProviderId): EditorProvider? = byId[id]

    /**
     * Filters registered providers matching the specified [kind].
     *
     * @param kind Category of providers to retrieve.
     * @return List of matching registered providers.
     */
    fun ofKind(kind: EditorProviderKind): List<EditorProvider> = ordered.filter { it.kind == kind }

    /**
     * Validates a configuration payload using the codec registered for [id].
     *
     * @param id Identifier of the target provider.
     * @param configuration Configuration payload to validate.
     * @return List of validation diagnostic messages.
     * @throws IllegalArgumentException If no provider is registered for [id].
     */
    fun validate(
        id: ProviderId,
        configuration: ProviderConfiguration,
    ): List<ValidationMessage> = requireNotNull(find(id)) {
        "No provider is registered for '${id.value}'."
    }.codec.validate(configuration)

    /**
     * Cancels all in-flight build or cooking operations across registered [BuildProvider] instances.
     */
    fun cancelBuilds() {
        ordered.filterIsInstance<BuildProvider>().forEach(BuildProvider::cancel)
    }

    /**
     * Disposes all registered providers in reverse registration order and clears the registry.
     */
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
