/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.fixture

import io.github.awakelab.awake.core.math.Aabb
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.render.renderer.CullMode
import io.github.awakelab.awake.scene.rendering.mesh.MeshRenderer
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.scene.runtime.SceneDocument
import io.github.awakelab.awake.scene.runtime.SceneLoader
import io.github.awakelab.awake.scene.runtime.SceneMeshRenderer
import io.github.awakelab.awake.scene.runtime.fromWorld

/** The one authored scene that exercises the editor host itself. Engine demonstrations live in
 * `samples:engine-showcase`; this fixture deliberately has no catalogue or selection state. */
internal class StudioFixture {
    private lateinit var document: SceneDocument
    private val boundsByEntity = mutableMapOf<Entity, Aabb>()

    /**
     * The authored mesh and material each renderable was built from.
     *
     * A live [MeshRenderer] holds GPU handles, which cannot be serialized back to a document, so
     * `SceneLoader.fromWorld` refuses to export one without a resolver rather than silently
     * dropping the geometry. This is that resolver's data, and instantiation is the only moment it
     * exists -- the request carries both the entity and the authored IDs.
     */
    private val authoredRenderers = mutableMapOf<Entity, SceneMeshRenderer>()

    fun boundsOf(entity: Entity): Aabb? = boundsByEntity[entity]

    /**
     * Copies the document-level render metadata to a fresh entity handle.
     *
     * Entity lifecycle commands never reuse a handle: undo and redo create a new generation. The
     * source record is intentionally retained so repeated undo/redo cycles can restore metadata
     * from the original command snapshot.
     */
    fun copyEntityMetadata(source: Entity, target: Entity) {
        authoredRenderers[source]?.let { authoredRenderers[target] = it }
        boundsByEntity[source]?.let { boundsByEntity[target] = it }
    }

    /**
     * Throws rather than returns null on a miss, which is what `fromWorld` asks of a resolver.
     *
     * A renderable with no authored record cannot be written to a document at all -- its mesh and
     * material exist only as GPU handles. Failing loudly is the engine's own choice here, and the
     * alternative is a save that quietly drops visible geometry.
     */
    fun authoredRendererOf(entity: Entity, renderer: MeshRenderer): SceneMeshRenderer {
        val authored = checkNotNull(authoredRenderers[entity]) {
            "Cannot save $entity: it has a MeshRenderer this fixture never instantiated."
        }
        return authored.copy(cullMode = renderer.cullMode.toSceneCullMode())
    }

    suspend fun preload() {
        document = SceneLoader.loadFromResource(SCENE_PATH)
    }

    fun load(runtime: SceneAppLifecycleRuntime) {
        load(runtime, document)
    }

    /**
     * Rebuilds the world from [source] rather than from the file on disk.
     *
     * What Stop uses to put back the scene as it was before Play ran: the snapshot is a document
     * like any other, so restoring it is the same operation as loading one.
     */
    fun load(runtime: SceneAppLifecycleRuntime, source: SceneDocument) {
        runtime.sceneManager.close()
        val instance = runtime.sceneManager.switchTo(source)
        val library = runtime.requireAssetLibrary()
        boundsByEntity.clear()
        authoredRenderers.clear()
        instance.renderableRequests.forEach { request ->
            runtime.world.add(request.entity, library.resolve(runtime, request))
            authoredRenderers[request.entity] = request.meshRenderer
            StudioFixtureBounds[request.meshRenderer.mesh]?.let { boundsByEntity[request.entity] = it }
        }
    }

    /** The live world as an authored document, ready to encode. */
    fun exportFrom(runtime: SceneAppLifecycleRuntime): SceneDocument =
        SceneLoader.fromWorld(runtime.world, name = document.name, meshRenderer = ::authoredRendererOf)

    internal companion object {
        private const val SCENE_PATH = "assets/examples/rotating-cube.scene.json"
    }
}

private fun CullMode.toSceneCullMode(): SceneMeshRenderer.CullMode = when (this) {
    CullMode.None -> SceneMeshRenderer.CullMode.None
    CullMode.Back -> SceneMeshRenderer.CullMode.Back
    CullMode.Front -> SceneMeshRenderer.CullMode.Front
}
