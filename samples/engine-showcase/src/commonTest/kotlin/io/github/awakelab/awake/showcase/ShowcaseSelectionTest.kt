/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The switcher's whole contract: a click asks, the driver takes the request once, and taking it
 * twice would activate a scene that is already running — which closes the world mid-frame for no
 * reason anyone watching could explain.
 */
class ShowcaseSelectionTest {

    @Test
    fun aRequestIsHandedOverExactlyOnce() {
        val selection = ShowcaseSelection("point-lights")

        selection.request("streamed-nav")

        assertEquals("streamed-nav", selection.consumeRequest())
        assertNull(selection.consumeRequest(), "The same request must not activate twice.")
        assertEquals("streamed-nav", selection.current)
    }

    @Test
    fun clickingTheRunningShowcaseChangesNothing() {
        val selection = ShowcaseSelection("point-lights")

        selection.request("point-lights")

        assertNull(selection.consumeRequest())
    }

    /** Clicking through a list faster than frames arrive must land on the last one, not queue. */
    @Test
    fun onlyTheLatestRequestSurvives() {
        val selection = ShowcaseSelection("point-lights")

        selection.request("nav-chase")
        selection.request("particles")

        assertEquals("particles", selection.consumeRequest())
        assertNull(selection.consumeRequest())
    }

    @Test
    fun nothingIsPendingBeforeAnythingIsClicked() {
        assertNull(ShowcaseSelection("point-lights").consumeRequest())
        assertEquals("point-lights", ShowcaseSelection("point-lights").current)
    }
}
