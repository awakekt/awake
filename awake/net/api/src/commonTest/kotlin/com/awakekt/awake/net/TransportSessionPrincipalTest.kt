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

class TransportSessionPrincipalTest {
    /** Written as an implementation from before [TransportSession.principal] existed. */
    private class LegacySession(override val id: SessionId) : TransportSession {
        override suspend fun send(channel: DeliveryChannel, packet: ByteArray) = Unit
        override fun incoming(): ReceiveChannel<ByteArray> = Channel()
        override suspend fun close() = Unit
    }

    private class AttachedSession(
        override val id: SessionId,
        override val principal: String?,
    ) : TransportSession {
        override suspend fun send(channel: DeliveryChannel, packet: ByteArray) = Unit
        override fun incoming(): ReceiveChannel<ByteArray> = Channel()
        override suspend fun close() = Unit
    }

    @Test
    fun anImplementationThatDoesNotOverrideItHasNoPrincipal() {
        val session: TransportSession = LegacySession(SessionId(1))
        assertNull(session.principal)
        assertNull(session.droppedInbound)
    }

    @Test
    fun aSessionCanExposeOptionalLossTelemetryThroughThePort() {
        val session: TransportSession = object : TransportSession, InboundDropMetrics {
            override val id = SessionId(3)
            override val droppedInbound = 7L
            override suspend fun send(channel: DeliveryChannel, packet: ByteArray) = Unit
            override fun incoming(): ReceiveChannel<ByteArray> = Channel()
            override suspend fun close() = Unit
        }
        assertEquals(7L, session.droppedInbound)
        assertEquals(7L, session.droppedInbound)
    }

    @Test
    fun aPrincipalAttachedAtAcceptTimeIsReadableThroughTheInterface() {
        val session: TransportSession = AttachedSession(SessionId(2), principal = "peer-7")
        assertEquals("peer-7", session.principal)
    }
}
