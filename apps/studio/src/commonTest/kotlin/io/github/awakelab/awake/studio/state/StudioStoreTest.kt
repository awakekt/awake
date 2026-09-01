/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.state

import io.github.awakelab.awake.scene.controls.camera.CameraMode
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

}
