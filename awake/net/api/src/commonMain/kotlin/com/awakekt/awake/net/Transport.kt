/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmInline

/**
 * The transport port, deliberately datagram-shaped: one `send` is one packet, and packets are
 * opaque bytes. WebSocket is implemented *as* a datagram transport rather than a stream so
 * that swapping in UDP or WebTransport later (docs/plans/network.md, phases 3-4) changes only
 * the implementation behind this interface.
 *
 * Packets are opaque here on purpose: message types, opcodes and protocol shape are game
 * policy and stay in the consuming project (see docs/reference/framework-game-boundary.md).
 * This module moves bytes and says nothing about what they mean.
 */
interface Transport {
    val state: StateFlow<ConnectionState>

    suspend fun send(channel: DeliveryChannel, packet: ByteArray)

    /** Inbound packets, in arrival order. Closed when the connection ends. */
    fun incoming(): ReceiveChannel<ByteArray>

    suspend fun close()
}

/**
 * Delivery guarantee requested by the caller. Which of the two is free depends on the
 * transport: over WebSocket [Reliable] costs nothing and [Unreliable] silently degrades to
 * reliable; over UDP it is the reverse. Callers must therefore treat [Unreliable] as
 * genuinely lossy even while WebSocket is the only implementation, or the loss handling never
 * gets written.
 */
enum class DeliveryChannel { Reliable, Unreliable }

sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    data object Connecting : ConnectionState

    data class Connected(val sessionId: SessionId) : ConnectionState

    data class Failed(val reason: String) : ConnectionState
}

@JvmInline
value class SessionId(val value: Long)

/** Server-side network identity of an entity. Never the local ECS entity index. */
@JvmInline
value class NetId(val value: Long)

/** Accepts connections and surfaces each one as a [TransportSession]. */
interface TransportServer {
    val port: Int

    fun sessions(): ReceiveChannel<TransportSession>

    suspend fun stop()
}

interface TransportSession {
    val id: SessionId

    suspend fun send(channel: DeliveryChannel, packet: ByteArray)

    fun incoming(): ReceiveChannel<ByteArray>

    suspend fun close()
}
