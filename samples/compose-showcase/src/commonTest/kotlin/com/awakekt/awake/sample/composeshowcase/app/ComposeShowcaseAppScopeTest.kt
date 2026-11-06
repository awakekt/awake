/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.composeshowcase.app

import com.awakekt.awake.engine.platform.error.AppCoroutineScope
import kotlinx.coroutines.isActive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The showcase registers the scope its stores run under, and disposing the app cancels it. */
class ComposeShowcaseAppScopeTest {
    @Test
    fun theAppRegistersItsScopeAndCancelsItOnDispose() {
        val app = composeShowcase()
        val scope = assertNotNull(app.service(AppCoroutineScope::class), "the app registers an AppCoroutineScope")
        assertTrue(scope.isActive)

        app.dispose()

        assertFalse(scope.isActive, "disposing the app closes the scope, cancelling the stores under it")
    }
}
