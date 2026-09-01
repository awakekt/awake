/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.studio.ui

import io.github.awakelab.awake.asset.shaderpack.LitShadowUniformLayout
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.input.key.onKeyEvent
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.core.logging.LogRingBuffer
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.editor.ProvideAwakeEditor
import io.github.awakelab.awake.editor.scene.gizmo.SceneOrientationGizmo
import io.github.awakelab.awake.editor.scene.handleSceneEditorShortcut
import io.github.awakelab.awake.editor.scene.hierarchy.SceneHierarchyPanel
import io.github.awakelab.awake.editor.scene.inspector.SceneInspectorPanel
import io.github.awakelab.awake.editor.scene.viewport.SceneCameraPreview
import io.github.awakelab.awake.editor.scene.viewport.SceneViewportPanel
import io.github.awakelab.awake.editor.scene.viewport.SceneViewportPanelModel
import io.github.awakelab.awake.editor.scene.viewport.SceneViewportPanelTags
import io.github.awakelab.awake.editor.scene.viewport.SceneViewportRect
import io.github.awakelab.awake.editor.shell.AwakeEditorShell
import io.github.awakelab.awake.editor.shell.EditorScaffold
import io.github.awakelab.awake.editor.toolbar.EditorHistoryButtons
import io.github.awakelab.awake.editor.toolbar.EditorPlayButton
import io.github.awakelab.awake.editor.toolbar.EditorSaveButton
import io.github.awakelab.awake.render.renderer.createMaterial
import io.github.awakelab.awake.scene.core.Name
import io.github.awakelab.awake.scene.rendering.debug.WorldDebugSettings
import io.github.awakelab.awake.scene.runtime.LocalFrameStats
import io.github.awakelab.awake.scene.runtime.LocalRenderer
import io.github.awakelab.awake.scene.runtime.LocalWorld
import io.github.awakelab.awake.studio.StudioHostResources
import io.github.awakelab.awake.studio.state.StudioContract
import io.github.awakelab.awake.studio.state.StudioEditorBridge
import io.github.awakelab.awake.studio.state.StudioStore
import io.github.awakelab.awake.studio.state.viewportControlBinding
import io.github.awakelab.awake.ui.shadcn.shadcnThemeValues

internal val StudioTheme = shadcnThemeValues(dark = true)

/**
 * The docked shell chrome: a full-width top bar, a three-way resizable split
 * (hierarchy | viewport | inspector), and a full-width status bar -- flush to every frame edge.
 *
 * **The workspace takes `weight(1f)`.** The ui-core version computed its height explicitly, as
 * shell height minus the top bar minus the status bar minus both hairlines, because a weight fill
 * left a gap exactly the status bar's height: the resizable group ran its own dry-count pass inside
 * the outer column's weight-distribution trial and the two disagreed. There is one pass now, so
 * there is nothing to disagree with, and every one of those subtractions is a place the arithmetic
 * could drift from what was actually laid out.
 */
context(_: Composer)
internal fun StudioShell(
    store: StudioStore,
    editorBridge: StudioEditorBridge = StudioEditorBridge(store),
    backend: String,
    // Defaulted so a test that only wants the shell need not build one; the module passes the
    // objects its own systems write to.
    resources: StudioHostResources = StudioHostResources(
        SceneViewportRect(),
        SceneCameraPreview(),
        SceneOrientationGizmo { renderer -> renderer.createMaterial(LitShadowUniformLayout) },
        LogRingBuffer(),
    ),
    onEntityMetadataCopied: (source: Entity, target: Entity) -> Unit = { _, _ -> },
) {
    val world = LocalWorld.current
    val renderer = LocalRenderer.current
    val stats = LocalFrameStats.current
    val playing = editorBridge.store.state.mode == io.github.awakelab.awake.editor.EditorMode.Play

    ProvideAwakeEditor(editorBridge.session, editorBridge.providers, editorBridge.store, editorBridge.history) {
        EditorScaffold(
            modifier = Modifier.onKeyEvent { event ->
                handleSceneEditorShortcut(
                    event,
                    world,
                    editorBridge.store,
                    editorBridge.history,
                    editorBridge.providers,
                    onEntityMetadataCopied,
                )
            },
            header = {
                StudioTopBar(
                    sceneTitle = "Rotating cube",
                    store = store,
                    historyAction = {
                        EditorSaveButton(
                            onSave = { store.dispatch(StudioContract.Intent.SaveScene) },
                            tag = "studio-top-bar-save",
                        )
                        EditorHistoryButtons(tag = "studio-top-bar-history")
                    },
                    playAction = { EditorPlayButton(Modifier.testTag("studio-top-bar-play")) },
                )
            },
            footer = {
                StudioStatusBar(
                    mode = if (playing) "Play mode" else "Edit mode",
                    backend = backend,
                    entityCount = world.namedEntityCount(),
                    fps = stats.fps,
                    frameTimeMs = stats.frameTimeMs,
                    phases = stats.phases,
                )
            },
        ) {
            StudioWorkspace(store, resources, onEntityMetadataCopied)
        }
    }
}

/**
 * The Studio host supplies one fixed scene fixture to the generic three-panel editor shell.
 */
context(composer: Composer)
private fun StudioWorkspace(
    store: StudioStore,
    resources: StudioHostResources,
    onEntityMetadataCopied: (source: Entity, target: Entity) -> Unit,
) {
    AwakeEditorShell(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        toolbar = {},
        viewportPickingEnabled = false,
        hierarchyTag = "studio-panel-sidebar",
        viewportTag = "studio-panel-viewport",
        inspectorTag = "studio-panel-inspector",
        handleTags = listOf("studio-panel-handle-left", "studio-panel-handle-right"),
        hierarchy = { SceneHierarchyPanel(composer, onEntityMetadataCopied) },
        viewport = { StudioViewportPanel(store, resources.viewportRect, resources.orientationGizmo, resources.cameraPreview) },
        inspector = { SceneInspectorPanel(composer) },
        dock = { StudioDock(store, resources) },
        dockTag = "studio-panel-dock",
    )
}

context(_: Composer)
private fun StudioViewportPanel(
    store: StudioStore,
    viewportRect: SceneViewportRect,
    orientationGizmo: SceneOrientationGizmo,
    cameraPreview: SceneCameraPreview,
) {
    val world = LocalWorld.current
    val renderer = LocalRenderer.current
    val debugSettings = world.family<WorldDebugSettings>().components().firstOrNull()
    val (viewState, viewActions) = viewportControlBinding(
        store,
        renderer,
        debugSettings,
        cameraPreview,
        orientationGizmo,
    )

    SceneViewportPanel(
        model = SceneViewportPanelModel(
            controlState = viewState,
            controlActions = viewActions,
            onAlignViewToCamera = { store.dispatch(StudioContract.Intent.AlignViewToCamera) },
            viewportRect = viewportRect,
            orientationGizmo = orientationGizmo,
            cameraPreview = cameraPreview,
        ),
        tags = SceneViewportPanelTags(
            displayControls = "studio-display-pill",
            debugControls = "studio-debug-pill",
            toolPalette = "studio-tool-pill",
            cameraControls = "studio-camera-pill",
            cameraMode = "studio-view-mode",
            cameraProjection = "studio-view-projection",
        ),
    )
}

private fun World.namedEntityCount(): Int {
    var count = 0
    queryEach<Name> { _, _ -> count++ }
    return count
}
