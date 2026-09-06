/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.compose.di.ProvideContainer
import com.awakekt.awake.core.di.resolve
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRingBuffer
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.core.logging.PrintLogSink
import com.awakekt.awake.editor.EditorPlugin
import com.awakekt.awake.editor.scene.gizmo.EditorGizmoController
import com.awakekt.awake.editor.scene.gizmo.SceneGizmo
import com.awakekt.awake.editor.scene.gizmo.SceneGizmoSystem
import com.awakekt.awake.editor.scene.gizmo.SceneOrientationGizmo
import com.awakekt.awake.editor.scene.gizmo.withSceneGizmoCapture
import com.awakekt.awake.editor.scene.toLiveEntity
import com.awakekt.awake.editor.scene.viewport.SceneCameraPreview
import com.awakekt.awake.editor.scene.viewport.SceneEditorCamera
import com.awakekt.awake.editor.scene.viewport.SceneEditorCameraSystem
import com.awakekt.awake.editor.scene.viewport.SceneViewportPreviewSystem
import com.awakekt.awake.editor.scene.viewport.SceneViewportRect
import com.awakekt.awake.editor.scene.viewport.createSceneEditorCameraEntity
import com.awakekt.awake.engine.bootstrap.dsl.appModule
import com.awakekt.awake.engine.platform.core.AppModule
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.authoring.dsl.entity
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.camera.CameraInputSystem
import com.awakekt.awake.scene.controls.camera.CameraSystem
import com.awakekt.awake.scene.rendering.debug.WorldDebugSettings
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.defaultInfrastructureSystems
import com.awakekt.awake.studio.app.platformBackendPreference
import com.awakekt.awake.studio.fixture.StudioFixture
import com.awakekt.awake.studio.state.StudioEditorBridge
import com.awakekt.awake.studio.state.StudioEditorBridgeSystem
import com.awakekt.awake.studio.state.StudioSceneEditorCameraController
import com.awakekt.awake.studio.state.StudioStore
import com.awakekt.awake.studio.systems.StudioFixtureSystem
import com.awakekt.awake.studio.ui.StudioShell
import com.awakekt.awake.studio.ui.StudioTheme
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme

/** `lit_shadow.wgsl`'s Uniforms size -- taken from the shared layout rather than re-summed
 * here, so adding a field to that shader can't leave this call site silently short. */
internal val LIT_SHADOW_UNIFORM_FLOAT_COUNT = LitShadowUniformLayout.total

/**
 * Creates an Awake Studio [AppModule] configured with consumer [plugins] and [backend].
 */
fun studioModule(
    plugins: List<EditorPlugin> = emptyList(),
    backend: AppWindowBackend = platformBackendPreference(),
    includeDefaultFixture: Boolean = true,
    keymap: com.awakekt.awake.editor.keybinding.EditorKeymapBuilder? = null,
    container: com.awakekt.awake.core.di.Container? = null,
): AppModule {
    val di = container ?: com.awakekt.awake.studio.di.createStudioContainer(
        com.awakekt.awake.core.di.module {
            factory<StudioEditorBridge> {
                StudioEditorBridge(
                    studio = resolve(),
                    customPlugins = plugins,
                    keymapBuilder = keymap,
                    fileChooser = resolve(),
                    persistence = resolve(),
                    pluginRepository = resolve(),
                )
            }
        },
    )
    return studioModule(
        store = di.resolve(),
        backend = backend,
        plugins = plugins,
        includeDefaultFixture = includeDefaultFixture,
        keymap = keymap,
        editorBridge = di.resolve(),
        container = di,
    )
}

/** [backend] is only a status-bar label. It is the backend this game asks its window for (see
 * `configureStudioWindow`), which is the closest honest answer available: `Renderer` exposes no
 * identity of its own. */
@Suppress("LongMethod") // The scene DSL is one ordered registration; splitting it obscures lifecycle order.
internal fun studioModule(
    store: StudioStore = StudioStore(),
    backend: AppWindowBackend = platformBackendPreference(),
    plugins: List<EditorPlugin> = emptyList(),
    includeDefaultFixture: Boolean = true,
    keymap: com.awakekt.awake.editor.keybinding.EditorKeymapBuilder? = null,
    editorBridge: StudioEditorBridge = StudioEditorBridge(
        store,
        customPlugins = plugins,
        keymapBuilder = keymap,
    ),
    container: com.awakekt.awake.core.di.Container? = null,
): AppModule {
    val di = container ?: com.awakekt.awake.studio.di.createStudioContainer()
    val fixture = StudioFixture()
    val backendLabel = backend.label()
    // Retained here rather than inside onReady so the console dock has something to read that
    // outlives a scene reload.
    val logBuffer = LogRingBuffer()
    val studioLog = Logger("studio")
    val gizmo = SceneGizmo(editorBridge.history)
    val resources = StudioHostResources(
        viewportRect = SceneViewportRect(),
        cameraPreview = SceneCameraPreview(),
        orientationGizmo = SceneOrientationGizmo { renderer -> renderer.createMaterial(LitShadowUniformLayout) },
        logBuffer = logBuffer,
    )
    val editorCamera = SceneEditorCamera()
    val editorCameraSystem = SceneEditorCameraSystem(editorCamera, StudioSceneEditorCameraController(store, editorBridge.store))

    return appModule {
        scene("studio") {
            assets {
                material("lit-shadow") { renderer.createMaterial(uniformFloatCount = LIT_SHADOW_UNIFORM_FLOAT_COUNT) }
                editorBridge.registerAssets(this)
            }

            systems {
                editorBridge.registerSystems(this)
            }

            // Direct construction: merges UI and gizmo handle drag ownership to prevent double-orbiting.
            frameSystem("editor-bridge") { StudioEditorBridgeSystem(editorBridge) }
            frameSystem("cameraInput") { CameraInputSystem(inputProvider = { gameplayInput(gizmo) }) }

            if (includeDefaultFixture) {
                frameSystem("studio-fixture") {
                    StudioFixtureSystem(this, store, fixture, editorBridge.history, editorCameraSystem::alignToAuthoredCamera)
                }
            }
            frameSystem("scene-editor-camera") { editorCameraSystem }
            frameSystem("camera") {
                CameraSystem(
                    inputProvider = { gameplayInput(gizmo) },
                    viewportBounds = { resources.viewportRect.bounds },
                )
            }
            // Runs after default infrastructure systems so gizmo lines are not cleared by debug visualization passes.
            infrastructureSystems {
                defaultInfrastructureSystems() +
                    SceneGizmoSystem(
                        renderer,
                        EditorGizmoController(editorBridge.session, editorBridge.store, world),
                        gizmo,
                        resources.viewportRect,
                        fixture::boundsOf,
                        inputProvider = { gizmoInput() },
                    ) +
                    SceneViewportPreviewSystem(
                        renderer,
                        resources.cameraPreview,
                        resources.orientationGizmo,
                        selectedEntityId = { editorBridge.store.state.selection.primary?.toLiveEntity(world)?.id },
                        drawCalls = { collectDrawCalls() },
                    )
            }

            onReady {
                // On by default here, unlike a game: studio exists to inspect the engine, and a
                // frame breakdown you have to remember to switch on is one nobody reads until
                // something is already wrong. F2 still toggles it off when the overhead itself
                // is what's under suspicion.
                perfStatsEnabled = true
                // Studio exists to inspect the engine, so it listens by default and at Debug.
                // A game would install nothing and pay nothing: with no sink, a log call does not
                // even build its message.
                Log.install(PrintLogSink())
                Log.install(logBuffer)
                Log.minimumLevel = LogLevel.Debug
                studioLog.info { "Studio ready on $backendLabel" }
                if (includeDefaultFixture) {
                    fixture.preload()
                }
                resources.files.preload()
                // Persistent entity surviving scene reloads to preserve debug visualization toggles.
                world.entity { with(WorldDebugSettings()) }
                // Persistent scene-view editor camera surviving scene reloads.
                editorCamera.entity = createSceneEditorCameraEntity(world)
                if (includeDefaultFixture) {
                    fixture.load(this)
                }
            }

            ui {
                ProvideContainer(di) {
                    provideShadcnTheme(StudioTheme) {
                        StudioShell(
                            store,
                            editorBridge,
                            backendLabel,
                            resources,
                            fixture::copyEntityMetadata,
                        )
                    }
                }
            }

            onDispose { resources.dispose() }
        }
    }
}

/**
 * Input for a system that must stand down while a transform handle is being dragged.
 *
 * `withSceneGizmoCapture` marks the pointer as owned when the gizmo holds it, so the camera does
 * not orbit at the same time as the handle moves -- which is what "double-orbiting" looked like
 * before it existed.
 */
private fun SceneAppLifecycleRuntime.gameplayInput(gizmo: SceneGizmo): GameplayInput =
    GameplayInput(
        requireService(Input::class).currentSnapshot,
        uiOwnership.withSceneGizmoCapture(gizmo),
    )

/**
 * Input for the gizmo itself, deliberately without that capture.
 *
 * The gizmo is what does the capturing, so filtering its own drag out of its own input would leave
 * it unable to see the press it is meant to be tracking.
 */
private fun SceneAppLifecycleRuntime.gizmoInput(): GameplayInput =
    GameplayInput(requireService(Input::class).currentSnapshot, uiOwnership)

// Spelled out rather than derived from `name`: enum-case text ("WEBGPU") is not how any of these
// backends is written.
private fun AppWindowBackend.label(): String = when (this) {
    AppWindowBackend.DEFAULT -> "Default"
    AppWindowBackend.VULKAN -> "Vulkan"
    AppWindowBackend.WEBGPU -> "WebGPU"
    AppWindowBackend.OPENGL -> "OpenGL"
}
