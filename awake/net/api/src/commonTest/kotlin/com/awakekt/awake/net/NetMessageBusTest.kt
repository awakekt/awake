/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class NetMessageBusTest {
    private sealed interface OutMsg {
        data class Ask(val id: String) : OutMsg
        data class Toggle(val flag: Boolean) : OutMsg
    }

    private sealed interface InMsg {
        data class Reply(val id: String, val value: Int) : InMsg
        data class Push(val note: String) : InMsg
    }

    @Test
    fun sendDrainAndTypedIncomingFlowDeliverInOrder() = runTest {
        val bus = NetMessageBus<OutMsg, InMsg>()
        assertTrue(bus.send(OutMsg.Ask("1")))
        assertTrue(bus.send(OutMsg.Toggle(true)))

        val drained = mutableListOf<OutMsg>()
        assertEquals(2, bus.drainOutbound { drained += it })
        assertEquals(listOf(OutMsg.Ask("1"), OutMsg.Toggle(true)), drained)
        assertEquals(0, bus.drainOutbound { drained += it })

        val pushes = async { bus.incoming<InMsg.Push>().take(2).toList() }
        testScheduler.runCurrent()

        bus.deliver(InMsg.Reply("ignored", 1))
        bus.deliver(InMsg.Push("first"))
        bus.deliver(InMsg.Push("second"))

        assertEquals(listOf(InMsg.Push("first"), InMsg.Push("second")), pushes.await())
        bus.close()
    }

    @Test
    fun requestMatchesCorrelatedReplyAndTimesOutWhenMissing() = runTest {
        val bus = NetMessageBus<OutMsg, InMsg>()

        val pending = async {
            bus.request<InMsg.Reply>(OutMsg.Ask("req-2"), timeout = 500.milliseconds) { it.id == "req-2" }
        }
        testScheduler.runCurrent()

        val sent = mutableListOf<OutMsg>()
        assertEquals(1, bus.drainOutbound { sent += it })
        assertEquals(listOf<OutMsg>(OutMsg.Ask("req-2")), sent)

        bus.deliver(InMsg.Reply("req-1", 10))
        bus.deliver(InMsg.Reply("req-2", 20))
        assertEquals(InMsg.Reply("req-2", 20), pending.await())

        assertFailsWith<TimeoutCancellationException> {
            bus.request<InMsg.Reply>(OutMsg.Ask("req-3"), timeout = 50.milliseconds)
        }
        bus.close()
    }

    @Test
    fun asClientNetFeatureBridgesLifecycleInboundAndOutbound() = runTest {
        val bus = NetMessageBus<OutMsg, InMsg>()
        val sent = mutableListOf<OutMsg>()
        val features = clientNetFeatures<MutableList<OutMsg>, InMsg> {
            install(
                bus.asClientNetFeature(
                    id = "bus-feature",
                    inboundTypes = listOf(InMsg.Reply::class, InMsg.Push::class),
                    sendMessage = { out, msg -> out += msg },
                ),
            )
        }

        assertFalse(bus.connected.value)
        features.onConnected(sent)
        assertTrue(bus.connected.value)

        val resetSeen = async { bus.resetEvents.first() }
        val pushSeen = async { bus.incoming<InMsg.Push>().first() }
        testScheduler.runCurrent()

        assertTrue(bus.send(OutMsg.Toggle(false)))
        features.flushOutbound(sent)
        assertEquals(listOf<OutMsg>(OutMsg.Toggle(false)), sent)

        assertTrue(features.route(sent, InMsg.Push("synced")))
        assertEquals(InMsg.Push("synced"), pushSeen.await())

        features.onReset(sent)
        resetSeen.await()

        features.onDisconnected(sent)
        assertFalse(bus.connected.value)

        bus.close()
        bus.close()
        assertTrue(bus.isClosed)
        assertFalse(bus.send(OutMsg.Toggle(true)))
        assertFalse(bus.deliver(InMsg.Push("after-close")))
    }

    @Test
    fun closeDropsRetainedOutboundPayloads() {
        val bus = NetMessageBus<OutMsg, InMsg>()
        assertTrue(bus.send(OutMsg.Ask("queued")))
        bus.onConnected()

        bus.close()

        assertFalse(bus.connected.value)
        var drained = 0
        assertEquals(0, bus.drainOutbound { drained++ })
        assertEquals(0, drained)
    }
}
