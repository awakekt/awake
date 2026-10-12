package com.awakekt.awake.template

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.project.runtime.loadProject as loadCoreProject
import com.awakekt.awake.project.runtime.runProject as runCoreProject
import com.awakekt.awake.scene.authoring.SceneAppDsl

/** A loaded project on Core 0.3 and later. */
typealias LoadedProject = com.awakekt.awake.project.runtime.LoadedProject

/** Loads the project's entry scene using the selected physics backend. */
internal suspend fun loadProject(
    files: AssetSource,
    physicsWorld: suspend () -> PhysicsWorld,
): LoadedProject = loadCoreProject(files, physicsWorld = physicsWorld)

/** Runs the loaded project in this application's scene. */
internal fun SceneAppDsl.runProject(project: LoadedProject, touchControls: Boolean) {
    runCoreProject(project, touchControls)
}
