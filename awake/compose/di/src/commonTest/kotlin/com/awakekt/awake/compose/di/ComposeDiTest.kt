/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.di

import com.awakekt.awake.compose.runtime.Applier
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.RememberHolder
import com.awakekt.awake.compose.runtime.Slot
import com.awakekt.awake.compose.runtime.reconcile
import com.awakekt.awake.core.di.container
import com.awakekt.awake.core.di.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComposeDiTest {

    private class TestNode : RememberHolder {
        private val _rememberSlots = mutableListOf<Any?>()
        override val rememberSlots: List<Any?> get() = _rememberSlots
    }

    private class TestApplier : Applier {
        override fun childCount(slot: Slot) = 0
        override fun typeAt(slot: Slot, index: Int): Any? = null
        override fun keyAt(slot: Slot, index: Int): Any? = null
        override fun nodeAt(slot: Slot, index: Int): Any = Unit
        override fun createAt(slot: Slot, index: Int, type: Any, key: Any?): Any = Unit
        override fun moveTo(slot: Slot, from: Int, to: Int) = Unit
        override fun truncateFrom(slot: Slot, index: Int) = Unit
        override fun down(slot: Slot, index: Int) = Unit
        override fun up() = Unit
    }

    interface GreetingService {
        fun greet(): String
    }

    class EnglishGreetingService : GreetingService {
        override fun greet(): String = "Hello"
    }

    private fun compose(root: TestNode = TestNode(), content: context(Composer) () -> Unit) {
        reconcile(TestApplier(), root, content)
    }

    @Test
    fun resolveGetsDependencyFromAmbientContainer() {
        val testContainer = container(
            module {
                singleton<GreetingService> { EnglishGreetingService() }
            },
        )

        var result = ""
        compose {
            ProvideContainer(testContainer) {
                val service = resolve<GreetingService>()
                result = service.greet()
            }
        }

        assertEquals("Hello", result)
    }

    @Test
    fun rememberResolveCachesInstanceAcrossEvaluations() {
        val testContainer = container(
            module {
                factory { EnglishGreetingService() }
            },
        )

        val root = TestNode()
        var firstInstance: EnglishGreetingService? = null
        var secondInstance: EnglishGreetingService? = null

        compose(root) {
            ProvideContainer(testContainer) {
                firstInstance = rememberResolve<EnglishGreetingService>()
            }
        }
        compose(root) {
            ProvideContainer(testContainer) {
                secondInstance = rememberResolve<EnglishGreetingService>()
            }
        }

        kotlin.test.assertSame(firstInstance, secondInstance)
    }

    @Test
    fun currentContainerThrowsWhenNotProvided() {
        assertFailsWith<IllegalStateException> {
            compose {
                currentContainer()
            }
        }
    }
}
