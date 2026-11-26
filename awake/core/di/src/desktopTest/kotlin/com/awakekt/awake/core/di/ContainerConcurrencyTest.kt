/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ContainerConcurrencyTest {

    private class CounterService(val createdOrder: Int)
    private class CycleA(val b: CycleB)
    private class CycleB(val a: CycleA)

    @Test
    fun concurrentSingletonResolutionInstantiatesExactlyOnce() {
        val created = AtomicInteger(0)
        val c = container {
            singleton<CounterService> {
                val order = created.incrementAndGet()
                Thread.sleep(10)
                CounterService(order)
            }
        }

        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val ready = CountDownLatch(threads)
            val start = CountDownLatch(1)
            val futures = List(threads) {
                pool.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        c.get<CounterService>()
                    },
                )
            }
            ready.await()
            start.countDown()

            val results = futures.map { it.get() }
            assertEquals(1, created.get())
            val first = results.first()
            results.forEach { assertSame(first, it) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun concurrentCyclicResolutionThrowsWithoutDeadlock() {
        val c = container {
            singleton { CycleA(get<CycleB>()) }
            singleton { CycleB(get<CycleA>()) }
        }

        val threads = 6
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val ready = CountDownLatch(threads)
            val start = CountDownLatch(1)
            val futures = List(threads) { idx ->
                pool.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        assertFailsWith<CyclicDependencyException> {
                            if (idx % 2 == 0) c.get<CycleA>() else c.get<CycleB>()
                        }
                    },
                )
            }
            ready.await()
            start.countDown()

            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }
}
