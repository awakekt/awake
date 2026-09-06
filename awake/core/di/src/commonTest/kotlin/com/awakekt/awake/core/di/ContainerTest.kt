/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ContainerTest {

    private interface EngineService
    private class DefaultEngineService(val id: Int = 1) : EngineService

    private interface Worker
    private class DefaultWorker : Worker

    @Test
    fun singletonResolvesSameInstanceLazily() {
        var createCount = 0
        val c = container {
            singleton<EngineService> {
                createCount++
                DefaultEngineService()
            }
        }

        assertEquals(0, createCount, "Singleton should not be created eagerly")
        val first = c.get<EngineService>()
        assertEquals(1, createCount)
        val second = c.get<EngineService>()
        assertEquals(1, createCount)
        assertSame(first, second)
    }

    @Test
    fun factoryResolvesNewInstanceEveryTime() {
        var createCount = 0
        val c = container {
            factory<Worker> {
                createCount++
                DefaultWorker()
            }
        }

        val first = c.get<Worker>()
        val second = c.get<Worker>()
        assertEquals(2, createCount)
        assertNotEquals(first, second)
    }

    @Test
    fun instanceBindingReturnsProvidedObject() {
        val existing = DefaultEngineService(42)
        val c = container {
            instance<EngineService>(existing)
        }

        assertSame(existing, c.get<EngineService>())
    }

    @Test
    fun qualifierDistinguishesBindings() {
        val c = container {
            singleton<EngineService>("primary") { DefaultEngineService(1) }
            singleton<EngineService>("secondary") { DefaultEngineService(2) }
        }

        val primary = c.get<EngineService>("primary") as DefaultEngineService
        val secondary = c.get<EngineService>("secondary") as DefaultEngineService

        assertEquals(1, primary.id)
        assertEquals(2, secondary.id)
        assertNull(c.getOrNull<EngineService>())
    }

    @Test
    fun injectPropertyDelegateResolvesLazily() {
        val c = container {
            singleton<EngineService> { DefaultEngineService(99) }
        }

        class Component {
            val service: EngineService by c.inject()
        }

        val component = Component()
        val service = component.service as DefaultEngineService
        assertEquals(99, service.id)
    }

    @Test
    fun childContainerInheritsAndOverridesBindings() {
        val parent = container {
            singleton<EngineService> { DefaultEngineService(10) }
            singleton<Worker> { DefaultWorker() }
        }

        val child = parent.createChild {
            singleton<EngineService> { DefaultEngineService(20) }
        }

        val parentService = parent.get<EngineService>() as DefaultEngineService
        val childService = child.get<EngineService>() as DefaultEngineService

        assertEquals(10, parentService.id)
        assertEquals(20, childService.id)

        // Inherited worker from parent
        assertNotNull(child.get<Worker>())
        assertSame(parent.get<Worker>(), child.get<Worker>())
    }

    @Test
    fun missingBindingThrowsException() {
        val c = container()
        assertFailsWith<MissingBindingException> {
            c.get<EngineService>()
        }
    }

    private class NodeA(val b: NodeB)
    private class NodeB(val a: NodeA)

    @Test
    fun cyclicDependencyThrowsException() {
        val c = container {
            singleton { NodeA(get<NodeB>()) }
            singleton { NodeB(get<NodeA>()) }
        }

        assertFailsWith<CyclicDependencyException> {
            c.get<NodeA>()
        }
    }

    @Test
    fun moduleCompositionMergesBindings() {
        val moduleA = module {
            singleton<EngineService> { DefaultEngineService(100) }
        }
        val moduleB = module {
            factory<Worker> { DefaultWorker() }
        }

        val c = container(moduleA + moduleB)
        assertNotNull(c.get<EngineService>())
        assertNotNull(c.get<Worker>())
    }
}
