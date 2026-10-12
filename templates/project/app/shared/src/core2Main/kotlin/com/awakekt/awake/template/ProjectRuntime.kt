package com.awakekt.awake.template

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.project.runtime.loadPlayableProject
import com.awakekt.awake.project.runtime.playProject
import com.awakekt.awake.scene.authoring.SceneAppDsl

/** A loaded project on Core releases before the 0.3 API rename. */
typealias LoadedProject = com.awakekt.awake.project.runtime.PlayableProject

/** Loads the project's entry scene using the selected physics backend. */
internal suspend fun loadProject(
    files: AssetSource,
    physicsWorld: suspend () -> PhysicsWorld,
): LoadedProject = loadPlayableProject(files, physicsWorld = physicsWorld)

/** Runs the loaded project in this application's scene. */
internal fun SceneAppDsl.runProject(project: LoadedProject, touchControls: Boolean) {
    playProject(project, touchControls)
}
