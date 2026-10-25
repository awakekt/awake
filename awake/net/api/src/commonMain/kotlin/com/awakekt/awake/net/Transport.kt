/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
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
    /** Observable flow emitting current connection life-cycle states. */
    val state: StateFlow<ConnectionState>

    /**
     * Sends a packet through the specified delivery channel.
     *
     * @param channel Delivery reliability guarantee requested for this packet.
     * @param packet Raw byte payload to transmit.
     */
    suspend fun send(channel: DeliveryChannel, packet: ByteArray)

    /** Inbound packets, in arrival order. Closed when the connection ends. */
    fun incoming(): ReceiveChannel<ByteArray>

    /** Closes the transport connection and releases underlying network resources. */
    suspend fun close()
}

/**
 * Delivery guarantee requested by the caller. Which of the two is free depends on the
 * transport: over WebSocket [Reliable] costs nothing and [Unreliable] silently degrades to
 * reliable; over UDP it is the reverse. Callers must therefore treat [Unreliable] as
 * genuinely lossy even while WebSocket is the only implementation, or the loss handling never
 * gets written.
 */
enum class DeliveryChannel {
    /** Guarantees in-order, loss-free packet delivery. */
    Reliable,

    /** Transmits packet with lowest latency without delivery or order guarantees. */
    Unreliable,
}

/** Represents the life-cycle state of a transport network connection. */
sealed interface ConnectionState {
    /** Connection is inactive and closed. */
    data object Disconnected : ConnectionState

    /** Connection handshake or socket setup is currently in progress. */
    data object Connecting : ConnectionState

    /**
     * Connection is established and active for data exchange.
     *
     * @property sessionId Assigned transport session identifier.
     */
    data class Connected(val sessionId: SessionId) : ConnectionState

    /**
     * Connection attempt or active connection failed.
     *
     * @property reason Diagnostic message describing the failure cause.
     */
    data class Failed(val reason: String) : ConnectionState
}

/**
 * Unique identifier of an active transport connection session.
 *
 * @property value Raw 64-bit integer identifier.
 */
@Serializable
@JvmInline
value class SessionId(val value: Long)

/**
 * Server-side network identity of an entity. Never the local ECS entity index.
 *
 * @property value Raw 64-bit entity network identifier.
 */
@Serializable
@JvmInline
value class NetId(val value: Long)

/** Accepts connections and surfaces each one as a [TransportSession]. */
interface TransportServer {
    /** Local network port number bound by the server. */
    val port: Int

    /**
     * Returns a receive channel yielding incoming client transport sessions as they connect.
     *
     * @return Channel of accepted client transport sessions.
     */
    fun sessions(): ReceiveChannel<TransportSession>

    /** Stops listening for incoming connections and terminates active sessions. */
    suspend fun stop()
}

/** Active bidirectional connection session on the server. */
interface TransportSession {
    /** Unique identifier of this transport session. */
    val id: SessionId

    /**
     * Opaque identity of the peer, verified and attached by whatever accepted the connection
     * (for example a check on the upgrade request that ran before the session existed), or
     * `null` when nothing established one. Fixed for the session's lifetime.
     *
     * The transport only carries the value: it never verifies, parses or interprets it, and what
     * it names (a user, a device, a service) is the consuming project's policy. Trust it only as
     * far as you trust the component that set it; a packet received *on* the session can never
     * change it. Defaults to `null`, so existing implementations keep compiling unchanged.
     */
    val principal: String? get() = null

    /**
     * Sends a packet to this session through the specified delivery channel.
     *
     * @param channel Delivery reliability guarantee requested for this packet.
     * @param packet Raw byte payload to transmit.
     */
    suspend fun send(channel: DeliveryChannel, packet: ByteArray)

    /**
     * Inbound packets from this session, in arrival order. Closed when the session ends.
     *
     * @return Channel yielding received packet byte arrays in arrival order.
     */
    fun incoming(): ReceiveChannel<ByteArray>

    /** Closes this session and terminates the client connection. */
    suspend fun close()
}

/** Optional telemetry for a session whose receive queue can discard packets. */
interface InboundDropMetrics {
    /**
     * Cumulative packets discarded by the inbound queue, including overflow and cancelled
     * receive handoffs. This is actual loss, not queue occupancy or proof of an abusive peer.
     * Reading does not reset it; the count remains available after close.
     */
    val droppedInbound: Long
}

/**
 * Actual inbound queue discards, or null when this transport does not report them.
 * An optional capability keeps existing [TransportSession] implementations binary-compatible.
 */
val TransportSession.droppedInbound: Long?
    get() = (this as? InboundDropMetrics)?.droppedInbound
