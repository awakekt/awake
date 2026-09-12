/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.bootstrap.di

import com.awakekt.awake.core.di.container
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.dsl.service
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppServiceLookupAdaptersTest {
    private data class SessionConfig(val name: String)

    @Test
    fun resolvesRegisteredServicesAtTheCompositionBoundary() {
        val services = container {
            instance(SessionConfig("demo"))
        }.asAppServiceLookup()

        assertEquals(SessionConfig("demo"), services.requireService<SessionConfig>())
        assertNull(services.service<String>())
    }
}
