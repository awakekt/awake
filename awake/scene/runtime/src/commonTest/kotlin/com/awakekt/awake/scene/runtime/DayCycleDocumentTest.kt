/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.scene.rendering.light.DayCycle
import com.awakekt.awake.scene.rendering.light.DayCycleSystem
import com.awakekt.awake.scene.rendering.light.SceneDayCycle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A `day_cycle` on the sun loads, plays and saves back as authored, never with the time it played to. */
class DayCycleDocumentTest {

    @Test
    fun aDayCycleRoundTripsAndSavesItsAuthoredTimeAfterPlaying() {
        val document = SceneLoader.decode(SCENE)
        assertTrue(SceneValidator.validate(document).isEmpty())
        val authored = document.nodes.single().components.filterIsInstance<SceneDayCycle>().single()
        assertEquals(SceneLoader.decode(SceneLoader.encode(document)), document, "decode and encode round-trip")

        val world = SceneLoader.instantiate(document).world
        val system = DayCycleSystem()
        repeat(FRAMES) { system.update(world, 1f / 60f) }
        val playing = world.query(DayCycle::class).single().let { world.get<DayCycle>(it)!! }
        assertNotEquals(authored.time, playing.time, "the day must have advanced")

        val saved = SceneLoader.decode(SceneLoader.encode(SceneLoader.fromWorld(world, name = "harbor")))
        val exported = saved.nodes.single().components.filterIsInstance<SceneDayCycle>().single()
        assertEquals(authored, exported)
        assertEquals(0.3f, exported.time)
    }

    private companion object {
        const val FRAMES = 60

        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "sun", "components": [
    { "component": "light", "type": "Directional" },
    { "component": "day_cycle", "dayLengthSeconds": 30.0, "time": 0.3, "sunriseAzimuthDegrees": 45.0,
      "stops": [ { "time": 0.25, "lightIntensity": 0.2, "horizonColor": "#203040" },
                 { "time": 0.5, "lightIntensity": 1.0, "ambient": 0.4 } ] }
  ] }
] }
"""

        init {
            DefaultSceneComponentResolvers.install()
        }
    }
}
