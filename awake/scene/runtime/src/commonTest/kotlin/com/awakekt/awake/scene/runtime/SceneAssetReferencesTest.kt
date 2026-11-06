/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.audio.SceneAudioSource
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.scene.document.ScenePrefabLink
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.scene2d.SceneParticleEmitter2D
import com.awakekt.awake.scene.scene2d.SceneSprite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A tree's assets are read from its components, children included, without a world. */
class SceneAssetReferencesTest {
    @Test
    fun aTreeNamesWhatItsComponentsDrawAndPlay() {
        val references = HARBOR.assetReferences()

        assertEquals(setOf(SceneRenderableKey("cube", "lit"), SceneRenderableKey("models/crate.glb", "lit")), references.renderables)
        assertEquals(setOf("sprites/flag.png", "sprites/spark.png"), references.textures)
        assertEquals(setOf("audio/bell.wav"), references.audioClips)
        assertEquals(emptySet(), references.prefabLinks)
    }

    /** The negative control: an unassigned clip, or a 2D emitter with no texture, names nothing. */
    @Test
    fun unassignedSlotsNameNothing() {
        val node = SceneNode(components = listOf(SceneAudioSource(), SceneParticleEmitter2D()))

        assertTrue(node.assetReferences().isEmpty)
    }

    /** A link whose prefab is not expanded has no assets to list, so it says which file to expand. */
    @Test
    fun anUnexpandedPrefabLinkIsNamed() {
        val bare = SceneNode(name = "Boat", components = listOf(ScenePrefabLink("prefabs/boat.prefab.json")))
        val expanded = bare.copy(children = listOf(HARBOR))

        assertEquals(setOf("prefabs/boat.prefab.json"), bare.assetReferences().prefabLinks)
        assertEquals(emptySet(), expanded.assetReferences().prefabLinks)
        assertEquals(HARBOR.assetReferences(), expanded.assetReferences())
    }

    @Test
    fun aDocumentAndAPrefabNameWhatTheirNodesDo() {
        val document = SceneDocument(nodes = listOf(HARBOR, SceneNode(components = listOf(SceneSprite(texture = "sprites/gull.png")))))

        assertEquals(HARBOR.assetReferences().textures + "sprites/gull.png", document.assetReferences().textures)
        assertEquals(HARBOR.assetReferences(), ScenePrefab(guid = "harbor", root = HARBOR).assetReferences())
    }

    private companion object {
        val HARBOR = SceneNode(
            name = "Harbor Town",
            components = listOf(SceneMeshRenderer(mesh = "cube", material = "lit"), SceneAudioSource(clip = "audio/bell.wav")),
            children = listOf(
                SceneNode(name = "Crate", components = listOf(SceneMeshRenderer(mesh = "models/crate.glb", material = "lit"))),
                SceneNode(
                    name = "Flag",
                    components = listOf(SceneSprite(texture = "sprites/flag.png"), SceneParticleEmitter2D(texture = "sprites/spark.png")),
                ),
            ),
        )
    }
}
