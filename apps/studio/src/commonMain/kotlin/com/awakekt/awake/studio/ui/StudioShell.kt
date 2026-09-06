/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.studio.ui

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.compose.di.rememberResolveOrNull
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxHeight
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.input.key.onKeyEvent
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.core.logging.LogRingBuffer
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.editor.LocalEditorProviders
import com.awakekt.awake.editor.ProvideAwakeEditor
import com.awakekt.awake.editor.scene.gizmo.SceneOrientationGizmo
import com.awakekt.awake.editor.scene.handleSceneEditorShortcut
import com.awakekt.awake.editor.scene.hierarchy.SceneHierarchyPanel
import com.awakekt.awake.editor.scene.inspector.SceneInspectorPanel
import com.awakekt.awake.editor.scene.viewport.SceneCameraPreview
import com.awakekt.awake.editor.scene.viewport.SceneViewportPanel
import com.awakekt.awake.editor.scene.viewport.SceneViewportPanelModel
import com.awakekt.awake.editor.scene.viewport.SceneViewportPanelTags
import com.awakekt.awake.editor.scene.viewport.SceneViewportRect
import com.awakekt.awake.editor.shell.AwakeEditorShell
import com.awakekt.awake.editor.shell.EditorInspectorTab
import com.awakekt.awake.editor.shell.EditorScaffold
import com.awakekt.awake.editor.shell.EditorShellConfig
import com.awakekt.awake.editor.shell.EditorShellState
import com.awakekt.awake.editor.shell.EditorSidebarTab
import com.awakekt.awake.editor.shell.inspectorContributions
import com.awakekt.awake.editor.shell.rememberEditorShellState
import com.awakekt.awake.editor.shell.sidebarContributions
import com.awakekt.awake.editor.toolbar.EditorDocumentButtons
import com.awakekt.awake.editor.toolbar.EditorPlayButton
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.fromWorld
import com.awakekt.awake.scene.rendering.debug.WorldDebugSettings
import com.awakekt.awake.scene.runtime.LocalFrameStats
import com.awakekt.awake.scene.runtime.LocalRenderer
import com.awakekt.awake.scene.runtime.LocalWorld
import com.awakekt.awake.studio.StudioHostResources
import com.awakekt.awake.studio.fixture.StudioSceneRegistry
import com.awakekt.awake.studio.state.StudioContract
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.studio.state.StudioStore
import com.awakekt.awake.studio.state.viewportControlBinding
import com.awakekt.awake.studio.ui.dialogs.StudioLicenseDialog
import com.awakekt.awake.studio.ui.dialogs.StudioMarketplaceDialog
import com.awakekt.awake.studio.ui.dialogs.StudioSettingsDialog
import com.awakekt.awake.studio.ui.theme.StudioThemeState
import com.awakekt.awake.tailwind.Tw
import com.awakekt.awake.ui.shadcn.ShadcnBaseColor
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnSeparator
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnToastState
import com.awakekt.awake.ui.shadcn.components.ShadcnToaster
import com.awakekt.awake.ui.shadcn.shadcnThemeValues
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlinx.coroutines.launch

private val studioLog = Logger("studio.shell")

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
    val playing = editorBridge.store.state.mode == com.awakekt.awake.editor.EditorMode.Play

    val keymap = com.awakekt.awake.compose.runtime.remember(world) {
        buildStudioKeymap(world, editorBridge, onEntityMetadataCopied)
    }

    val dialogState = com.awakekt.awake.compose.runtime.remember { StudioDialogState() }
    val themeState = com.awakekt.awake.compose.runtime.remember { StudioThemeState() }
    val toastState = rememberResolveOrNull<ShadcnToastState>() ?: com.awakekt.awake.compose.runtime.remember { ShadcnToastState() }

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
                header = { StudioHeader(store, editorBridge, dialogState, themeState, toastState, world, resources) },
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
                    ShadcnToaster(toastState)
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
    toastState: ShadcnToastState,
    world: World,
    resources: StudioHostResources,
) {
    val sceneTitle = StudioSceneRegistry.findById(store.state.value.activeSceneId)?.title ?: "Rotating cube"
    StudioTopBar(
        sceneTitle = sceneTitle,
        store = store,
        historyAction = {
            EditorDocumentButtons(
                onSave = {
                    try {
                        val path = store.state.value.lastSavedTo ?: "scene.json"
                        val sceneDoc = SceneLoader.fromWorld(world, name = sceneTitle)
                        val json = SceneLoader.encode(sceneDoc)
                        resources.files.writeText(path, json)
                        studioLog.info { "Saved scene '$sceneTitle' to $path (${sceneDoc.nodes.size} root nodes)" }
                        toastState.show("Saved scene to $path", title = "Scene Saved")
                        store.dispatch(StudioContract.Intent.SaveScene)
                    } catch (e: Exception) {
                        studioLog.error(e) { "Failed to save scene: ${e.message}" }
                        toastState.show("Failed to save scene: ${e.message}", title = "Save Failed")
                    }
                },
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
                editorBridge.scope.launch {
                    val file = editorBridge.fileChooser.openFile(
                        title = "Open Awake Scene",
                        extensions = listOf("awakescene", "scene.json"),
                    )
                    if (file != null) {
                        studioLog.info { "Opened scene file: $file" }
                        toastState.show("Opened scene file: $file", title = "Scene Opened")
                        store.dispatch(StudioContract.Intent.SelectFile(file))
                    }
                }
            },
            onOpenProjectFolder = {
                editorBridge.scope.launch {
                    val folder = editorBridge.fileChooser.openDirectory(
                        title = "Open Project Folder",
                    )
                    if (folder != null) {
                        val scanned = resources.files.scanProjectDirectory(folder)
                        studioLog.info { "Opened project folder '$folder' (found ${scanned.size} files)" }
                        toastState.show("Loaded ${scanned.size} files from project", title = "Project Opened")
                        store.dispatch(StudioContract.Intent.SelectFile(folder))
                    }
                }
            },
            onSaveSceneAs = {
                editorBridge.scope.launch {
                    val target = editorBridge.fileChooser.saveFile(
                        title = "Save Scene As",
                        defaultFileName = "scene.json",
                        extensions = listOf("json", "awakescene"),
                    )
                    if (target != null) {
                        try {
                            val sceneDoc = SceneLoader.fromWorld(world, name = sceneTitle)
                            val json = SceneLoader.encode(sceneDoc)
                            resources.files.writeText(target, json)
                            studioLog.info { "Saved scene as '$target' (${sceneDoc.nodes.size} root nodes)" }
                            toastState.show("Scene saved to $target", title = "Scene Saved")
                            store.dispatch(StudioContract.Intent.SceneSaved(target))
                        } catch (e: Exception) {
                            studioLog.error(e) { "Failed to save scene: ${e.message}" }
                            toastState.show("Failed to save scene: ${e.message}", title = "Save Failed")
                        }
                    }
                }
            },
            onImportAssetFile = {
                editorBridge.scope.launch {
                    val file = editorBridge.fileChooser.openFile(
                        title = "Import 3D Asset",
                        extensions = listOf("glb", "gltf", "fbx", "obj"),
                    )
                    if (file != null) {
                        studioLog.info { "Imported 3D asset: $file" }
                        toastState.show("Imported 3D asset: $file", title = "Asset Imported")
                    }
                }
            },
        ),
    )
}

private fun buildStudioKeymap(
    world: World,
    editorBridge: StudioEditorBridge,
    onEntityMetadataCopied: (source: Entity, target: Entity) -> Unit,
): com.awakekt.awake.editor.keybinding.EditorKeymap {
    val base = com.awakekt.awake.editor.keybinding.EditorKeymap(
        com.awakekt.awake.editor.scene.session.createSceneEditorKeybindings(world, onEntityMetadataCopied),
    )
    for (contrib in editorBridge.providers.all.filterIsInstance<com.awakekt.awake.editor.core.keybinding.EditorKeybindingContribution>()) {
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
