/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.project.AwakeProjectManifest
import com.awakekt.awake.project.AwakeProjectValidator
import com.awakekt.awake.project.GatedAssetSource
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneDocument

/**
 * How a project starts when its files gate its entry scene, as a
 * [com.awakekt.awake.project.LayeredAssetSource] gates a protected world before sign-in: it plays
 * [placeholderScene] instead, or an empty scene when that is null, and [runProject] asks for the entry
 * scene as it starts. The switch reports [SceneSwitchState.WaitingForContent] until the app opens the
 * gate, attaching the protected source after sign-in, then loads the entry scene as any switch does.
 *
 * Signing out detaches the protected source; the scene playing goes on, and a later switch to a
 * protected scene waits again. When the entry scene is not gated, the project loads it as usual and
 * the gate does nothing.
 *
 * @property placeholderScene A scene the files can read before the gate opens, such as a sign-in or
 * title scene, relative to the project root; null for an empty scene with nothing in it.
 */
class EntryGate(val placeholderScene: String? = null) {
    init {
        placeholderScene?.let { require(AwakeProjectValidator.isSafeProjectPath(it)) { "'$it' is not a project-relative path" } }
    }

    /** Constants for [EntryGate]. */
    companion object {
        /** The path an empty placeholder plays under; no file is read for it. */
        const val EMPTY_PLACEHOLDER: String = "awake-empty-placeholder.scene.json"
    }
}

/**
 * [loadProject], starting at [gate]'s placeholder when [files] gate the entry scene ([entryScene], or
 * [AwakeProjectManifest.entryScene] when null), and asking for the entry scene once [runProject] starts:
 * [LoadedProject.pendingEntryScene] names it until then. The placeholder loads as an entry scene
 * does, with [onProgress], features, physics and strings as the other [loadProject] overloads describe.
 * [componentRegistry] is the registry to load into, or null for the process-wide one.
 *
 * A host that plays the project without [runProject], such as a server stepping [sceneSystems], reads
 * [LoadedProject.pendingEntryScene] and switches to it with [switchTo] once its gate opens.
 */
@Suppress("LongParameterList") // Each is one thing every loadProject overload takes, plus the gate.
suspend fun loadProject(
    files: AssetSource,
    gate: EntryGate,
    capabilities: List<SceneFeature> = emptyList(),
    hasRenderer: Boolean = true,
    entryScene: String? = null,
    componentRegistry: SceneComponentRegistry? = null,
    onProgress: (Float) -> Unit = {},
    strings: Strings? = null,
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProjectInto(componentRegistry, files, capabilities, hasRenderer, physicsWorld, entryScene, onProgress, strings, gate)

/** Whether this source gates [path]: it is a [GatedAssetSource] that can't read it yet. */
internal suspend fun AssetSource.gates(path: String): Boolean = (this as? GatedAssetSource)?.isGated(AssetPath(path)) == true

/** [gate]'s placeholder, loaded to play while the entry scene waits. */
@Suppress("LongParameterList") // As loadProjectScene's, plus the gate and progress.
internal suspend fun loadPlaceholder(
    gate: EntryGate,
    manifest: AwakeProjectManifest,
    files: AssetSource,
    registry: SceneComponentRegistry?,
    installed: List<SceneFeature>,
    hasRenderer: Boolean,
    physicsWorld: (suspend () -> PhysicsWorld)?,
    onProgress: (Float) -> Unit,
): ProjectScene {
    val placeholder = gate.placeholderScene
    if (placeholder == null) {
        val empty = SceneDocument(name = EntryGate.EMPTY_PLACEHOLDER.removeSuffix(".scene.json"))
        return projectSceneOf(EntryGate.EMPTY_PLACEHOLDER, empty, manifest, files, installed, hasRenderer, physicsWorld)
    }
    files.prefetchSceneContent(placeholder, onProgress)
    return loadProjectScene(placeholder, manifest, files, registry, installed, hasRenderer, physicsWorld)
}
