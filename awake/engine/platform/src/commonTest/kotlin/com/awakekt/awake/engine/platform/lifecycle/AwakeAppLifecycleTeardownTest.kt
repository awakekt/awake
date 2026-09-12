/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.lifecycle

import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class AwakeAppLifecycleTeardownTest {
    private class CloseProbe(private val name: String, private val events: MutableList<String>) : AutoCloseable {
        override fun close() {
            events += name
        }
    }

    @Test
    fun closesServicesAfterCallbacksAndOnlyOnce() {
        val events = mutableListOf<String>()
        val builder = AppSpecBuilder()
        builder.service(CloseProbe::class, CloseProbe("service", events))
        builder.dispose { events += "callback" }
        val lifecycle = builder.build().createLifecycle()

        lifecycle.dispose()
        lifecycle.dispose()

        assertEquals(listOf("callback", "service"), events)
    }
}
