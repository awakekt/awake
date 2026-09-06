/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.state

import com.awakekt.awake.scene.controls.camera.CameraMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudioStoreTest {

    @Test
    fun setCameraModeUpdatesStateWithoutQueuingAnEffect() {
        val store = StudioStore()
        assertEquals(CameraMode.ThirdPerson, store.state.value.camera.mode)

        store.dispatch(StudioContract.Intent.SetCameraMode(CameraMode.FirstPerson))

        assertEquals(CameraMode.FirstPerson, store.state.value.camera.mode)
        assertTrue(store.drainEffects().isEmpty())
    }

    @Test
    fun setProjectionFlipsProjection() {
        val store = StudioStore()
        assertEquals(StudioContract.Projection.Perspective, store.state.value.camera.projection)

        store.dispatch(StudioContract.Intent.SetProjection(StudioContract.Projection.Orthographic))
        assertEquals(StudioContract.Projection.Orthographic, store.state.value.camera.projection)

        store.dispatch(StudioContract.Intent.SetProjection(StudioContract.Projection.Perspective))
        assertEquals(StudioContract.Projection.Perspective, store.state.value.camera.projection)
    }

    @Test
    fun startPlayAndStopPlayProduceMatchingEffects() {
        val store = StudioStore()
        store.startPlay()
        val effects1 = store.drainEffects()
        assertEquals(listOf(StudioContract.Effect.StartPlay), effects1)
        assertTrue(store.drainEffects().isEmpty())

        store.stopPlay()
        val effects2 = store.drainEffects()
        assertEquals(listOf(StudioContract.Effect.StopPlay), effects2)
        assertTrue(store.drainEffects().isEmpty())
    }

    @Test
    fun reloadFixtureProducesReloadEffect() {
        val store = StudioStore()
        store.reloadFixture()
        assertEquals(listOf(StudioContract.Effect.ReloadFixture), store.drainEffects())
    }

    @Test
    fun saveSceneAndAlignViewProduceEffects() {
        val store = StudioStore()
        store.dispatch(StudioContract.Intent.SaveScene)
        store.dispatch(StudioContract.Intent.AlignViewToCamera)

        assertEquals(
            listOf(StudioContract.Effect.SaveScene, StudioContract.Effect.AlignViewToCamera),
            store.drainEffects(),
        )
        assertTrue(store.drainEffects().isEmpty())
    }

    @Test
    fun stateMutationsUpdateSynchronously() {
        val store = StudioStore()
        store.dispatch(StudioContract.Intent.SceneSaved("/path/to/scene.json"))
        assertEquals("/path/to/scene.json", store.state.value.lastSavedTo)

        store.dispatch(StudioContract.Intent.SelectDockTab("custom-dock"))
        assertEquals("custom-dock", store.state.value.dockTab)

        store.dispatch(StudioContract.Intent.SelectFile("/assets/model.glb"))
        assertEquals("/assets/model.glb", store.state.value.selectedFile)
    }
}
