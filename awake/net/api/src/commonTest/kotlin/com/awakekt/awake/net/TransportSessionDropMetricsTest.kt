/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransportSessionDropMetricsTest {
    private class LegacySession : TransportSession {
        override val id = SessionId(1)
        override suspend fun send(channel: DeliveryChannel, packet: ByteArray) = Unit
        override fun incoming(): ReceiveChannel<ByteArray> = Channel()
        override suspend fun close() = Unit
    }

    @Test
    fun aLegacySessionReportsUnsupportedTelemetry() {
        val session: TransportSession = LegacySession()
        assertNull(session.droppedInbound)
    }

    @Test
    fun aSessionCanExposeOptionalLossTelemetryThroughThePort() {
        val measured = object : TransportSession, InboundDropMetrics {
            override val id = SessionId(3)
            override val droppedInbound = 7L
            override suspend fun send(channel: DeliveryChannel, packet: ByteArray) = Unit
            override fun incoming(): ReceiveChannel<ByteArray> = Channel()
            override suspend fun close() = Unit
        }
        val supportedCount: Long = measured.droppedInbound
        val session: TransportSession = measured
        val optionalCount: Long? = session.droppedInbound
        assertEquals(supportedCount, optionalCount)
        assertEquals(7L, session.droppedInbound)
    }
}
