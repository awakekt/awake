/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContainerTest {

    private interface EngineService
    private class DefaultEngineService(val id: Int = 1) : EngineService

    private interface Worker
    private class DefaultWorker : Worker

    private class TrackedResource(
        val name: String,
        val events: MutableList<String>,
        private val failOnClose: Boolean = false,
    ) : AutoCloseable {
        var closedCount = 0
            private set

        override fun close() {
            closedCount++
            events += "close:$name"
            if (failOnClose) {
                throw IllegalStateException("close failed for $name")
            }
        }
    }

    private class DependentResource(
        val name: String,
        val dependency: TrackedResource,
        val events: MutableList<String>,
    ) : AutoCloseable {
        override fun close() {
            events += "close:$name(depClosed=${dependency.closedCount})"
        }
    }

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
    fun childOverrideCanDecorateParentBindingWithSameKey() {
        class DecoratingService(val base: DefaultEngineService) : EngineService

        val parent = container {
            singleton<EngineService> { DefaultEngineService(7) }
        }
        val child = parent.createChild {
            singleton<EngineService> { DecoratingService(parent.get<EngineService>() as DefaultEngineService) }
        }

        val resolved = child.get<EngineService>() as DecoratingService
        assertEquals(7, resolved.base.id)
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
    fun cyclicDependencyThrowsExceptionWithCycleChain() {
        val c = container {
            singleton { NodeA(get<NodeB>()) }
            singleton { NodeB(get<NodeA>()) }
        }

        val error = assertFailsWith<CyclicDependencyException> {
            c.get<NodeA>()
        }
        assertTrue(error.message.orEmpty().contains("NodeA -> NodeB -> NodeA"), "Unexpected message: ${error.message}")
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

    @Test
    fun reusedModuleIsolatesSingletonsPerContainer() {
        val shared = module {
            singleton<EngineService> { DefaultEngineService(1) }
        }

        val first = container(shared)
        val second = container(shared)

        val a = first.get<EngineService>()
        val b = second.get<EngineService>()
        assertNotEquals(a, b)
        first.close()
        assertSame(b, second.get<EngineService>())
    }

    @Test
    fun closeDisposesSingletonsInReverseCreationOrderAndIsIdempotent() {
        val events = mutableListOf<String>()
        val c = container {
            singleton("dep") { TrackedResource("dep", events) }
            singleton("consumer") { DependentResource("consumer", get("dep"), events) }
        }

        c.get<DependentResource>("consumer")
        assertFalse(c.isClosed)

        c.close()
        assertTrue(c.isClosed)
        // Dependent closes first while dependency is still open (depClosed=0)
        assertEquals(listOf("close:consumer(depClosed=0)", "close:dep"), events)

        // Second close is a safe no-op
        c.close()
        assertEquals(2, events.size)

        // Post-close resolution and child creation fail fast
        assertFailsWith<ContainerClosedException> { c.get<TrackedResource>("dep") }
        assertFailsWith<ContainerClosedException> { c.getOrNull<TrackedResource>("dep") }
        assertFailsWith<ContainerClosedException> { c.createChild() }
        assertFailsWith<ContainerClosedException> { c.verify() }
    }

    @Test
    fun closeDeduplicatesAliasedAutoCloseableSingletons() {
        val events = mutableListOf<String>()
        val c = container {
            singleton<TrackedResource> { TrackedResource("shared", events) }
            singleton<AutoCloseable>("alias") { get<TrackedResource>() }
        }

        val impl = c.get<TrackedResource>()
        val alias = c.get<AutoCloseable>("alias")
        assertSame(impl, alias)

        c.close()
        assertEquals(1, impl.closedCount)
        assertEquals(listOf("close:shared"), events)
    }

    @Test
    fun instanceBindingRespectsCloseOnDisposeOwnershipFlag() {
        val events = mutableListOf<String>()
        val borrowed = TrackedResource("borrowed", events)
        val owned = TrackedResource("owned", events)

        container {
            instance(borrowed, qualifier = "borrowed")
            instance(owned, qualifier = "owned", closeOnDispose = true)
        }.use {
            // Even without resolving `owned`, the container took ownership of it.
        }

        assertEquals(0, borrowed.closedCount)
        assertEquals(1, owned.closedCount)
        assertEquals(listOf("close:owned"), events)
    }

    @Test
    fun parentCloseClosesOpenChildrenFirstAndDetachesClosedChildren() {
        val events = mutableListOf<String>()
        val parent = container {
            singleton("parent") { TrackedResource("parent", events) }
        }
        parent.get<TrackedResource>("parent")

        val shortLived = parent.createChild {
            singleton("short") { TrackedResource("short", events) }
        }
        shortLived.get<TrackedResource>("short")
        shortLived.close()
        assertEquals(listOf("close:short"), events)
        assertFalse(parent.isClosed)

        val child1 = parent.createChild {
            singleton("child1") { TrackedResource("child1", events) }
        }
        val child2 = parent.createChild {
            singleton("child2") { TrackedResource("child2", events) }
        }
        child1.get<TrackedResource>("child1")
        child2.get<TrackedResource>("child2")

        parent.close()
        assertTrue(child1.isClosed)
        assertTrue(child2.isClosed)
        assertEquals(
            listOf("close:short", "close:child2", "close:child1", "close:parent"),
            events,
        )
    }

    @Test
    fun closeStillClosesRemainingResourcesWhenOneThrows() {
        val events = mutableListOf<String>()
        val c = container {
            singleton("first") { TrackedResource("first", events) }
            singleton("failing") { TrackedResource("failing", events, failOnClose = true) }
            singleton("last") { TrackedResource("last", events) }
        }
        c.get<TrackedResource>("first")
        c.get<TrackedResource>("failing")
        c.get<TrackedResource>("last")

        val error = assertFailsWith<IllegalStateException> {
            c.close()
        }
        assertTrue(c.isClosed)
        assertEquals("close failed for failing", error.message)
        assertEquals(listOf("close:last", "close:failing", "close:first"), events)
    }

    @Test
    fun verifySucceedsOnValidGraphAndClosesTemporaryFactoryResources() {
        val events = mutableListOf<String>()
        val parent = container {
            singleton("parentRes") { TrackedResource("parentRes", events) }
        }
        val child = parent.createChild {
            singleton("childRes") { DependentResource("childRes", get("parentRes"), events) }
            factory("tempFactory") { TrackedResource("tempFactory", events) }
            factory("parentAlias") { get<TrackedResource>("parentRes") }
        }

        child.verify()
        // Temporary factory resource is closed immediately; parent singleton is untouched.
        assertEquals(listOf("close:tempFactory"), events)

        parent.close()
        assertEquals(
            listOf("close:tempFactory", "close:childRes(depClosed=0)", "close:parentRes"),
            events,
        )
    }

    @Test
    fun verifyFailsFastOnMissingBindingOrCycle() {
        val missing = container {
            singleton<EngineService> { DefaultEngineService(get<Int>()) }
        }
        assertFailsWith<MissingBindingException> {
            missing.verify()
        }

        val cyclic = container {
            singleton { NodeA(get<NodeB>()) }
            singleton { NodeB(get<NodeA>()) }
        }
        assertFailsWith<CyclicDependencyException> {
            cyclic.verify()
        }
    }
}
