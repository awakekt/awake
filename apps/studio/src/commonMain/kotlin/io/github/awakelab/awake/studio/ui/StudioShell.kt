/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package io.github.awakelab.awake.studio.ui

import io.github.awakelab.awake.asset.shaderpack.LitShadowUniformLayout
import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.Row
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.foundation.layout.fillMaxSize
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.padding
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.runtime.key
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.input.key.onKeyEvent
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.core.logging.LogRingBuffer
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.editor.LocalEditorProviders
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
import io.github.awakelab.awake.editor.shell.EditorInspectorTab
import io.github.awakelab.awake.editor.shell.EditorScaffold
import io.github.awakelab.awake.editor.shell.EditorShellConfig
import io.github.awakelab.awake.editor.shell.EditorShellState
import io.github.awakelab.awake.editor.shell.EditorSidebarTab
import io.github.awakelab.awake.editor.shell.inspectorContributions
import io.github.awakelab.awake.editor.shell.rememberEditorShellState
import io.github.awakelab.awake.editor.shell.sidebarContributions
import io.github.awakelab.awake.editor.toolbar.EditorDocumentButtons
import io.github.awakelab.awake.editor.toolbar.EditorPlayButton
import io.github.awakelab.awake.render.renderer.createMaterial
import io.github.awakelab.awake.scene.core.Name
import io.github.awakelab.awake.scene.rendering.debug.WorldDebugSettings
import io.github.awakelab.awake.scene.runtime.LocalFrameStats
import io.github.awakelab.awake.scene.runtime.LocalRenderer
import io.github.awakelab.awake.scene.runtime.LocalWorld
import io.github.awakelab.awake.studio.StudioHostResources
import io.github.awakelab.awake.studio.fixture.StudioSceneRegistry
import io.github.awakelab.awake.studio.state.StudioContract
import io.github.awakelab.awake.studio.state.StudioEditorBridge
import io.github.awakelab.awake.studio.state.StudioStore
import io.github.awakelab.awake.studio.state.viewportControlBinding
import io.github.awakelab.awake.studio.ui.dialogs.StudioLicenseDialog
import io.github.awakelab.awake.studio.ui.dialogs.StudioMarketplaceDialog
import io.github.awakelab.awake.studio.ui.dialogs.StudioSettingsDialog
import io.github.awakelab.awake.studio.ui.theme.StudioThemeState
import io.github.awakelab.awake.tailwind.Tw
import io.github.awakelab.awake.ui.shadcn.ShadcnBaseColor
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButton
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnButtonVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSeparator
import io.github.awakelab.awake.ui.shadcn.components.ShadcnText
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTextVariant
import io.github.awakelab.awake.ui.shadcn.shadcnThemeValues
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme

internal val StudioTheme = shadcnThemeValues(baseColor = ShadcnBaseColor.Zinc, dark = true)

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

    val keymap = io.github.awakelab.awake.compose.runtime.remember(world) {
        buildStudioKeymap(world, editorBridge, onEntityMetadataCopied)
    }

    val dialogState = io.github.awakelab.awake.compose.runtime.remember { StudioDialogState() }
    val themeState = io.github.awakelab.awake.compose.runtime.remember { StudioThemeState() }

    provideShadcnTheme(themeState.themeValues) {
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
                        keymap = keymap,
                    )
                },
                header = { StudioHeader(store, editorBridge, dialogState, themeState) },
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
                Box(Modifier.fillMaxSize()) {
                    StudioWorkspace(store, resources, onEntityMetadataCopied)
                    StudioDialogHost(dialogState, editorBridge, themeState)
                }
            }
        }
    }
}

context(_: Composer)
private fun StudioHeader(
    store: StudioStore,
    editorBridge: StudioEditorBridge,
    dialogState: StudioDialogState,
    themeState: StudioThemeState,
) {
    StudioTopBar(
        sceneTitle = StudioSceneRegistry.findById(store.state.value.activeSceneId)?.title ?: "Rotating cube",
        store = store,
        historyAction = {
            EditorDocumentButtons(
                onSave = { store.dispatch(StudioContract.Intent.SaveScene) },
                tag = "studio-top-bar-history",
            )
        },
        playAction = { EditorPlayButton(Modifier.testTag("studio-top-bar-play")) },
        controls = StudioTopBarControls(
            onOpenMarketplace = { dialogState.showMarketplace = true },
            onOpenLicense = { dialogState.showLicense = true },
            onOpenSettings = { dialogState.showSettings = true },
            onToggleDarkMode = { themeState.isDark = !themeState.isDark },
            isDarkMode = themeState.isDark,
            onOpenSceneFile = {
                editorBridge.fileChooser.openFile(
                    title = "Open Awake Scene",
                    extensions = listOf("awakescene", "scene.json"),
                ) {}
            },
            onImportAssetFile = {
                editorBridge.fileChooser.openFile(
                    title = "Import 3D Asset",
                    extensions = listOf("glb", "gltf", "fbx", "obj"),
                ) {}
            },
        ),
    )
}

private fun buildStudioKeymap(
    world: World,
    editorBridge: StudioEditorBridge,
    onEntityMetadataCopied: (source: Entity, target: Entity) -> Unit,
): io.github.awakelab.awake.editor.keybinding.EditorKeymap {
    val base = io.github.awakelab.awake.editor.keybinding.EditorKeymap(
        io.github.awakelab.awake.editor.scene.session.createSceneEditorKeybindings(world, onEntityMetadataCopied),
    )
    for (contrib in editorBridge.providers.all.filterIsInstance<io.github.awakelab.awake.editor.core.keybinding.EditorKeybindingContribution>()) {
        for (binding in contrib.bindings) {
            base.register(binding)
        }
    }
    editorBridge.keymapBuilder?.applyTo(base)
    return base
}

context(_: Composer)
private fun StudioDialogHost(
    dialogState: StudioDialogState,
    editorBridge: StudioEditorBridge,
    themeState: StudioThemeState,
) {
    StudioMarketplaceDialog(
        visible = dialogState.showMarketplace,
        onDismissRequest = { dialogState.showMarketplace = false },
        editorBridge = editorBridge,
        onRequireLicense = {
            dialogState.showMarketplace = false
            dialogState.showLicense = true
        },
    )
    StudioLicenseDialog(
        visible = dialogState.showLicense,
        onDismissRequest = { dialogState.showLicense = false },
    )
    StudioSettingsDialog(
        visible = dialogState.showSettings,
        onDismissRequest = { dialogState.showSettings = false },
        themeState = themeState,
    )
}

private class StudioDialogState(
    var showMarketplace: Boolean = false,
    var showLicense: Boolean = false,
    var showSettings: Boolean = false,
)

/**
 * The Studio host supplies one fixed scene fixture to the generic three-panel editor shell.
 */
context(composer: Composer)
private fun StudioWorkspace(
    store: StudioStore,
    resources: StudioHostResources,
    onEntityMetadataCopied: (source: Entity, target: Entity) -> Unit,
) {
    val shellState = rememberEditorShellState(initialDockExpanded = true)
    AwakeEditorShell(
        state = shellState,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        config = EditorShellConfig(
            viewportPickingEnabled = false,
            hierarchyTag = "studio-panel-sidebar",
            viewportTag = "studio-panel-viewport",
            inspectorTag = "studio-panel-inspector",
            dockTag = "studio-panel-dock",
            handleTags = listOf("studio-panel-handle-left", "studio-panel-handle-right"),
        ),
    ) {
        hierarchy { StudioLeftSidebar(shellState, onEntityMetadataCopied) }
        viewport { StudioViewportPanel(store, resources.viewportRect, resources.orientationGizmo, resources.cameraPreview) }
        inspector { StudioRightInspector(shellState) }
        dock { StudioDock(store, resources) }
    }
}

context(composer: Composer)
private fun StudioLeftSidebar(
    shellState: EditorShellState,
    onEntityMetadataCopied: (source: Entity, target: Entity) -> Unit,
) {
    val providers = LocalEditorProviders.current
    val contributions = providers.sidebarContributions()
    if (contributions.isEmpty()) {
        SceneHierarchyPanel(composer, onEntityMetadataCopied)
        return
    }

    val selectedTabId = shellState.activeSidebarTab ?: "hierarchy"
    val tabs = listOf(EditorSidebarTab("hierarchy", "Hierarchy")) + contributions.map { it.tab }

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Tw.Spacing.s2, vertical = Tw.Spacing.s1),
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isSelected = tab.id == selectedTabId
                ShadcnButton(
                    onClick = { shellState.activeSidebarTab = tab.id },
                    variant = if (isSelected) ShadcnButtonVariant.Secondary else ShadcnButtonVariant.Ghost,
                    size = ShadcnButtonSizeVariant.Sm,
                ) {
                    ShadcnText(tab.label, variant = ShadcnTextVariant.Xs)
                }
            }
        }
        ShadcnSeparator()
        key(selectedTabId) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (selectedTabId == "hierarchy") {
                    SceneHierarchyPanel(composer, onEntityMetadataCopied)
                } else {
                    contributions.firstOrNull { it.tab.id == selectedTabId }?.content()
                }
            }
        }
    }
}

context(composer: Composer)
private fun StudioRightInspector(shellState: EditorShellState) {
    val providers = LocalEditorProviders.current
    val contributions = providers.inspectorContributions()
    if (contributions.isEmpty()) {
        SceneInspectorPanel(composer)
        return
    }

    val selectedTabId = shellState.activeInspectorTab ?: "inspector"
    val tabs = listOf(EditorInspectorTab("inspector", "Inspector")) + contributions.map { it.tab }

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Tw.Spacing.s2, vertical = Tw.Spacing.s1),
            horizontalArrangement = Arrangement.spacedByHorizontal(Tw.Spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isSelected = tab.id == selectedTabId
                ShadcnButton(
                    onClick = { shellState.activeInspectorTab = tab.id },
                    variant = if (isSelected) ShadcnButtonVariant.Secondary else ShadcnButtonVariant.Ghost,
                    size = ShadcnButtonSizeVariant.Sm,
                ) {
                    ShadcnText(tab.label, variant = ShadcnTextVariant.Xs)
                }
            }
        }
        ShadcnSeparator()
        key(selectedTabId) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (selectedTabId == "inspector") {
                    SceneInspectorPanel(composer)
                } else {
                    contributions.firstOrNull { it.tab.id == selectedTabId }?.content()
                }
            }
        }
    }
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
