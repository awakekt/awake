package com.awakekt.awake.template

import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.bootstrap.dsl.select
import com.awakekt.awake.engine.platform.dsl.AppWindowBackend
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.authoring.scene

/** The window title. */
const val GAME_TITLE = "Awake Project"

/**
 * The Awake project this app plays: everything under `project/` in the shared module's resources.
 * Replace that folder with a project from Awake Studio to play it instead of the sample.
 */
val BundledProject = AssetSource { path -> runCatching { readResourceBytes("project/${path.value}") } }

/** Reads the bundled project and its entry scene. */
suspend fun loadGame(): LoadedProject = loadProject(BundledProject, physicsWorld = ::createJoltPhysicsWorld)

/** Creates a fresh application lifecycle that plays [project]; [touchControls] shows its on-screen controls. */
fun createGame(
    project: LoadedProject,
    windowBackend: AppWindowBackend = AppWindowBackend.VULKAN,
    touchControls: Boolean = false,
): AwakeAppLifecycle = app {
    window {
        title = GAME_TITLE
        size(1280, 720)
        backend.select(windowBackend)
    }
    scene("game") { runProject(project, touchControls) }
}
