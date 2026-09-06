/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.state

import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.editor.EditorEffectRouter
import com.awakekt.awake.editor.EditorHistory
import com.awakekt.awake.editor.EditorMode
import com.awakekt.awake.editor.EditorPlugin
import com.awakekt.awake.editor.EditorPluginRegistry
import com.awakekt.awake.editor.EditorProviders
import com.awakekt.awake.editor.EditorSession
import com.awakekt.awake.editor.EditorState
import com.awakekt.awake.editor.EditorStore
import com.awakekt.awake.editor.EditorTool
import com.awakekt.awake.editor.ai.AiEditorPlugin
import com.awakekt.awake.editor.core.asset.AssetConverterPlugin
import com.awakekt.awake.editor.core.asset.AssetConverterRegistry
import com.awakekt.awake.editor.core.files.EditorFileChooser
import com.awakekt.awake.editor.core.files.createPlatformFileChooser
import com.awakekt.awake.editor.keybinding.EditorKeymapBuilder
import com.awakekt.awake.editor.panels.files.EditorAudioFileViewer
import com.awakekt.awake.editor.panels.files.EditorModelFileViewer
import com.awakekt.awake.editor.panels.files.EditorTerrainFileViewer
import com.awakekt.awake.editor.panels.files.PlainTextFileViewer
import com.awakekt.awake.editor.physics.PhysicsEditorPlugin
import com.awakekt.awake.editor.render.RenderEditorPlugin
import com.awakekt.awake.editor.scene.inspector.sceneCoreComponentFactories
import com.awakekt.awake.editor.scene.plugin.AssetResolverPlugin
import com.awakekt.awake.editor.scene.plugin.SceneSystemPlugin
import com.awakekt.awake.editor.scene.viewport.SceneCameraProjection
import com.awakekt.awake.editor.scene.viewport.SceneEditorCameraController
import com.awakekt.awake.editor.scene.viewport.SceneEditorCameraState
import com.awakekt.awake.scene.authoring.SceneAssetsDsl
import com.awakekt.awake.scene.authoring.SceneSystemsDsl
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.studio.plugins.SkeletalAnimationPlugin
import com.awakekt.awake.studio.plugins.SpinAnimationPlugin
import com.awakekt.awake.ui.builder.plugin.UiBuilderEditorPlugin

/** Supplies Studio's fixture and camera policy to generic editor state. */
internal class StudioEditorBridge(
    private val studio: StudioStore,
    val converters: AssetConverterRegistry = AssetConverterRegistry(),
    customPlugins: List<EditorPlugin> = emptyList(),
    val keymapBuilder: EditorKeymapBuilder? = null,
    val fileChooser: EditorFileChooser = createPlatformFileChooser(),
    val persistence: com.awakekt.awake.studio.plugins.StudioPluginPersistence =
        com.awakekt.awake.studio.plugins.InMemoryStudioPluginPersistence(),
    val pluginRepository: com.awakekt.awake.studio.plugins.repository.StudioPluginRepository =
        com.awakekt.awake.studio.plugins.repository.DefaultStudioPluginRepository(persistence = persistence),
    val scope: kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
) {
    val store = EditorStore(EditorState(activeTool = EditorTool.Translate))
    val providers = EditorProviders().apply {
        register(PlainTextFileViewer())
        register(EditorModelFileViewer())
        register(EditorAudioFileViewer())
        register(EditorTerrainFileViewer())
        // The scene's own vocabulary. A plugin brings its own factories with it -- see the physics
        // one below -- but Transform and SpinControl belong to no feature.
        registerAll(sceneCoreComponentFactories())
    }

    val systemPlugins = mutableListOf<SceneSystemPlugin>()
    val resolverPlugins = mutableListOf<AssetResolverPlugin>()

    /**
     * Kept as a field, not discarded after installing: it is what a UI listing installed plugins
     * reads, and the thing that rejects a duplicate id or an incompatible API version.
     */
    val plugins = EditorPluginRegistry(providers)

    init {
        installPlugin(PhysicsEditorPlugin())
        installPlugin(AiEditorPlugin())
        installPlugin(RenderEditorPlugin())
        installPlugin(SkeletalAnimationPlugin())
        installPlugin(SpinAnimationPlugin(store))
        installPlugin(UiBuilderEditorPlugin())
        installPlugin(com.awakekt.awake.studio.plugins.StudioPrimitiveAssetsPlugin())
        customPlugins.forEach(::installPlugin)
        val disabledIds = persistence.loadDisabledPluginIds()
        persistence.loadInstalled().forEach { manifest ->
            val pluginId = com.awakekt.awake.editor.core.plugin.EditorPluginId(manifest.id)
            if (manifest.id !in disabledIds && plugins.installed.none { it.id == pluginId }) {
                try {
                    installPlugin(
                        com.awakekt.awake.studio.plugins.DynamicStudioExtensionPlugin(manifest),
                    )
                } catch (_: Exception) {
                    // Ignore corrupted or duplicate entries
                }
            }
        }
        pluginRepository.addLifecycleListener(
            object : com.awakekt.awake.studio.plugins.repository.PluginLifecycleListener {
                override fun onPluginInstalled(manifest: com.awakekt.awake.editor.core.plugin.PluginManifest) {
                    log.info { "Lifecycle: plugin installed '${manifest.id}'" }
                    val pluginId = com.awakekt.awake.editor.core.plugin.EditorPluginId(manifest.id)
                    if (plugins.installed.none { it.id == pluginId }) {
                        try {
                            installPlugin(com.awakekt.awake.studio.plugins.DynamicStudioExtensionPlugin(manifest))
                        } catch (e: Exception) {
                            log.error(e) { "Failed to install dynamic plugin: ${manifest.id}" }
                        }
                    }
                }

                override fun onPluginUninstalled(id: com.awakekt.awake.editor.core.plugin.EditorPluginId) {
                    log.info { "Lifecycle: plugin uninstalled '${id.value}'" }
                    uninstallPlugin(id)
                }

                override fun onPluginStateChanged(
                    id: com.awakekt.awake.editor.core.plugin.EditorPluginId,
                    enabled: Boolean,
                ) {
                    log.info { "Lifecycle: plugin '${id.value}' state changed to enabled=$enabled" }
                    if (enabled) {
                        if (plugins.installed.none { it.id == id }) {
                            val manifest = persistence.loadInstalled().firstOrNull { it.id == id.value }
                                ?: pluginRepository.findById(id.value)?.manifest
                            if (manifest != null) {
                                try {
                                    installPlugin(com.awakekt.awake.studio.plugins.DynamicStudioExtensionPlugin(manifest))
                                } catch (e: Exception) {
                                    log.error(e) { "Failed to activate dynamic plugin: ${manifest.id}" }
                                }
                            }
                        }
                    } else {
                        deactivatePlugin(id)
                    }
                }
            },
        )
    }

    private companion object {
        val log = Logger("studio.plugins")
    }

    /**
     * Installs an [EditorPlugin] and registers any asset converters, systems, and resolvers it contributes.
     */
    fun installPlugin(plugin: EditorPlugin) {
        log.info { "Installing plugin '${plugin.metadata.id.value}' v${plugin.metadata.version}" }
        plugins.install(plugin)
        if (plugin is AssetConverterPlugin) {
            converters.registerAll(plugin.converters)
        }
        if (plugin is SceneSystemPlugin) {
            systemPlugins.add(plugin)
        }
        if (plugin is AssetResolverPlugin) {
            resolverPlugins.add(plugin)
        }
    }

    /**
     * Deactivates a plugin at runtime by removing all its contributed providers and systems.
     */
    fun deactivatePlugin(pluginId: com.awakekt.awake.editor.core.plugin.EditorPluginId): Boolean {
        log.info { "Deactivating plugin '${pluginId.value}' (dock tabs and systems unregistered)" }
        systemPlugins.removeAll { it.metadata.id == pluginId }
        resolverPlugins.removeAll { it.metadata.id == pluginId }
        return plugins.uninstall(pluginId)
    }

    /**
     * Uninstalls an [EditorPlugin] by its ID, removing it both from runtime and persistence.
     */
    fun uninstallPlugin(pluginId: com.awakekt.awake.editor.core.plugin.EditorPluginId): Boolean {
        log.info { "Uninstalling plugin '${pluginId.value}'" }
        val result = deactivatePlugin(pluginId)
        if (result) {
            val remaining = persistence.loadInstalled().filter { it.id != pluginId.value }
            persistence.saveInstalled(remaining)
        }
        return result
    }

    /**
     * Persists an installed extension manifest to storage.
     */
    fun persistExtension(manifest: com.awakekt.awake.editor.core.plugin.PluginManifest) {
        log.info { "Persisting extension manifest '${manifest.id}'" }
        val current = persistence.loadInstalled().filter { it.id != manifest.id }
        persistence.saveInstalled(current + manifest)
    }

    fun registerSystems(dsl: SceneSystemsDsl) {
        systemPlugins.forEach { it.registerSystems(dsl) }
    }

    fun registerAssets(dsl: SceneAssetsDsl) {
        resolverPlugins.forEach { it.registerAssets(dsl) }
    }

    val history = EditorHistory()
    val session: EditorSession = object : EditorSession {
        override val mode get() = store.state.mode
        override fun startPlay() = studio.startPlay()
        override fun stopPlay() = studio.stopPlay()
    }
    private val effects = EditorEffectRouter(session, viewportPicker = { null })
    fun frame() = effects.drain(store)
}

internal class StudioEditorBridgeSystem(private val bridge: StudioEditorBridge) : System {
    override fun update(world: World, delta: Float) = bridge.frame()
}

internal class StudioSceneEditorCameraController(
    private val studio: StudioStore,
    private val editor: EditorStore,
) : SceneEditorCameraController {
    override val state
        get() = SceneEditorCameraState(
            sceneViewActive = editor.state.mode == EditorMode.Edit,
            mode = studio.state.value.camera.mode,
            projection = studio.state.value.camera.projection.toSceneCameraProjection(),
        )

    override fun selectCameraMode(mode: CameraMode) =
        studio.dispatch(StudioContract.Intent.SetCameraMode(mode))
}

internal fun StudioContract.Projection.toSceneCameraProjection() = when (this) {
    StudioContract.Projection.Perspective -> SceneCameraProjection.Perspective
    StudioContract.Projection.Orthographic -> SceneCameraProjection.Orthographic
}
