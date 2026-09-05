/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.state

import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.editor.EditorEffectRouter
import io.github.awakelab.awake.editor.EditorHistory
import io.github.awakelab.awake.editor.EditorMode
import io.github.awakelab.awake.editor.EditorPlugin
import io.github.awakelab.awake.editor.EditorPluginRegistry
import io.github.awakelab.awake.editor.EditorProviders
import io.github.awakelab.awake.editor.EditorSession
import io.github.awakelab.awake.editor.EditorState
import io.github.awakelab.awake.editor.EditorStore
import io.github.awakelab.awake.editor.EditorTool
import io.github.awakelab.awake.editor.ai.AiEditorPlugin
import io.github.awakelab.awake.editor.core.asset.AssetConverterPlugin
import io.github.awakelab.awake.editor.core.asset.AssetConverterRegistry
import io.github.awakelab.awake.editor.core.files.EditorFileChooser
import io.github.awakelab.awake.editor.core.files.createPlatformFileChooser
import io.github.awakelab.awake.editor.keybinding.EditorKeymapBuilder
import io.github.awakelab.awake.editor.panels.files.EditorAudioFileViewer
import io.github.awakelab.awake.editor.panels.files.EditorModelFileViewer
import io.github.awakelab.awake.editor.panels.files.EditorTerrainFileViewer
import io.github.awakelab.awake.editor.panels.files.PlainTextFileViewer
import io.github.awakelab.awake.editor.physics.PhysicsEditorPlugin
import io.github.awakelab.awake.editor.render.RenderEditorPlugin
import io.github.awakelab.awake.editor.scene.inspector.sceneCoreComponentFactories
import io.github.awakelab.awake.editor.scene.plugin.AssetResolverPlugin
import io.github.awakelab.awake.editor.scene.plugin.SceneSystemPlugin
import io.github.awakelab.awake.editor.scene.viewport.SceneCameraProjection
import io.github.awakelab.awake.editor.scene.viewport.SceneEditorCameraController
import io.github.awakelab.awake.editor.scene.viewport.SceneEditorCameraState
import io.github.awakelab.awake.scene.authoring.SceneAssetsDsl
import io.github.awakelab.awake.scene.authoring.SceneSystemsDsl
import io.github.awakelab.awake.scene.controls.camera.CameraMode
import io.github.awakelab.awake.studio.plugins.SkeletalAnimationPlugin
import io.github.awakelab.awake.studio.plugins.SpinAnimationPlugin
import io.github.awakelab.awake.ui.builder.plugin.UiBuilderEditorPlugin

/** Supplies Studio's fixture and camera policy to generic editor state. */
internal class StudioEditorBridge(
    private val studio: StudioStore,
    val converters: AssetConverterRegistry = AssetConverterRegistry(),
    customPlugins: List<EditorPlugin> = emptyList(),
    val keymapBuilder: EditorKeymapBuilder? = null,
    val fileChooser: EditorFileChooser = createPlatformFileChooser(),
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
        installPlugin(io.github.awakelab.awake.studio.plugins.StudioPrimitiveAssetsPlugin())
        customPlugins.forEach(::installPlugin)
    }

    /**
     * Installs an [EditorPlugin] and registers any asset converters, systems, and resolvers it contributes.
     */
    fun installPlugin(plugin: EditorPlugin) {
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
