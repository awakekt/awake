/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio

import io.github.awakelab.awake.asset.shaderpack.LitShadowUniformLayout
import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.geometry.generate.generate
import io.github.awakelab.awake.core.input.Input
import io.github.awakelab.awake.core.logging.Log
import io.github.awakelab.awake.core.logging.LogLevel
import io.github.awakelab.awake.core.logging.LogRingBuffer
import io.github.awakelab.awake.core.logging.Logger
import io.github.awakelab.awake.core.logging.PrintLogSink
import io.github.awakelab.awake.editor.scene.gizmo.EditorGizmoController
import io.github.awakelab.awake.editor.scene.gizmo.SceneGizmo
import io.github.awakelab.awake.editor.scene.gizmo.SceneGizmoSystem
import io.github.awakelab.awake.editor.scene.gizmo.SceneOrientationGizmo
import io.github.awakelab.awake.editor.scene.gizmo.withSceneGizmoCapture
import io.github.awakelab.awake.editor.scene.toLiveEntity
import io.github.awakelab.awake.editor.scene.viewport.SceneCameraPreview
import io.github.awakelab.awake.editor.scene.viewport.SceneEditorCamera
import io.github.awakelab.awake.editor.scene.viewport.SceneEditorCameraSystem
import io.github.awakelab.awake.editor.scene.viewport.SceneViewportPreviewSystem
import io.github.awakelab.awake.editor.scene.viewport.SceneViewportRect
import io.github.awakelab.awake.editor.scene.viewport.createSceneEditorCameraEntity
import io.github.awakelab.awake.engine.bootstrap.dsl.appModule
import io.github.awakelab.awake.engine.platform.core.AppModule
import io.github.awakelab.awake.engine.platform.dsl.AppWindowBackend
import io.github.awakelab.awake.render.renderer.createMaterial
import io.github.awakelab.awake.scene.authoring.dsl.entity
import io.github.awakelab.awake.scene.authoring.scene
import io.github.awakelab.awake.scene.controls.GameplayInput
import io.github.awakelab.awake.scene.controls.camera.CameraInputSystem
import io.github.awakelab.awake.scene.controls.camera.CameraSystem
import io.github.awakelab.awake.scene.core.transform.SpinSystem
import io.github.awakelab.awake.scene.rendering.debug.WorldDebugSettings
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.scene.runtime.defaultInfrastructureSystems
import io.github.awakelab.awake.studio.app.platformBackendPreference
import io.github.awakelab.awake.studio.fixture.StudioFixture
import io.github.awakelab.awake.studio.fixture.StudioFixtureBounds
import io.github.awakelab.awake.studio.state.StudioEditorBridge
import io.github.awakelab.awake.studio.state.StudioEditorBridgeSystem
import io.github.awakelab.awake.studio.state.StudioSceneEditorCameraController
import io.github.awakelab.awake.studio.state.StudioStore
import io.github.awakelab.awake.studio.systems.PlayModeSystem
import io.github.awakelab.awake.studio.systems.SpinClockSystem
import io.github.awakelab.awake.studio.systems.StudioFixtureSystem
import io.github.awakelab.awake.studio.ui.StudioShell
import io.github.awakelab.awake.studio.ui.StudioTheme
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme

/** `lit_shadow.wgsl`'s Uniforms size -- taken from the shared layout rather than re-summed
 * here, so adding a field to that shader can't leave this call site silently short. */
internal val LIT_SHADOW_UNIFORM_FLOAT_COUNT = LitShadowUniformLayout.total

/** [backend] is only a status-bar label. It is the backend this game asks its window for (see
 * `configureStudioWindow`), which is the closest honest answer available: `Renderer` exposes no
 * identity of its own. */
@Suppress("LongMethod") // The scene DSL is one ordered registration; splitting it obscures lifecycle order.
internal fun studioModule(
    store: StudioStore = StudioStore(),
    backend: AppWindowBackend = platformBackendPreference(),
    editorBridge: StudioEditorBridge = StudioEditorBridge(store),
): AppModule {
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
                mesh("cube") { renderer.createMesh(generate { cube(size = 1f, colored = true) }.alsoRecordBounds("cube")) }
                mesh("ground") { renderer.createMesh(generate { plane(size = 10f, colored = false) }.alsoRecordBounds("ground")) }
                material("lit-shadow") { renderer.createMaterial(uniformFloatCount = LIT_SHADOW_UNIFORM_FLOAT_COUNT) }
            }

            // Direct construction: merges UI and gizmo handle drag ownership to prevent double-orbiting.
            frameSystem("editor-bridge") { StudioEditorBridgeSystem(editorBridge) }
            frameSystem("cameraInput") { CameraInputSystem(inputProvider = { gameplayInput(gizmo) }) }
            // Advance rotating cube SpinControl only during Play mode.
            frameSystem("spin-clock") { PlayModeSystem(SpinClockSystem(), editorBridge.store) }
            frameSystem("spin") { PlayModeSystem(SpinSystem(), editorBridge.store) }
            frameSystem("studio-fixture") {
                StudioFixtureSystem(this, store, fixture, editorBridge.history, editorCameraSystem::alignToAuthoredCamera)
            }
            frameSystem("scene-editor-camera") { editorCameraSystem }
            frameSystem("camera") { CameraSystem(inputProvider = { gameplayInput(gizmo) }) }
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
                    ) +
                    StudioEditorBridgeSystem(editorBridge)
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
                fixture.preload()
                resources.files.preload()
                // Persistent entity surviving scene reloads to preserve debug visualization toggles.
                world.entity { with(WorldDebugSettings()) }
                // Persistent scene-view editor camera surviving scene reloads.
                editorCamera.entity = createSceneEditorCameraEntity(world)
                fixture.load(this)
            }

            content {
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

/** Records [meshId]'s local bounds for picking, and returns the geometry unchanged. */
private fun MeshGeometry.alsoRecordBounds(meshId: String): MeshGeometry = also {
    StudioFixtureBounds.register(meshId, it)
}

// Spelled out rather than derived from `name`: enum-case text ("WEBGPU") is not how any of these
// backends is written.
private fun AppWindowBackend.label(): String = when (this) {
    AppWindowBackend.DEFAULT -> "Default"
    AppWindowBackend.VULKAN -> "Vulkan"
    AppWindowBackend.WEBGPU -> "WebGPU"
    AppWindowBackend.OPENGL -> "OpenGL"
}
