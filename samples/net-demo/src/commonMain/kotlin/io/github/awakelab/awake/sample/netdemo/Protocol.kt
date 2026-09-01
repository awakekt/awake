/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.netdemo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Handshake frames: JSON, readable in a browser frame inspector, and never on the hot path.
 * Per-tick input and snapshot frames are binary instead -- see [TickCodec].
 *
 * Every packet starts with a one-byte opcode, so the two formats share one socket without a
 * separate channel. JSON frames carry [TickCodec.Opcode.JSON] and their payload follows.
 *
 * Clients send intents, never state -- that asymmetry is the anti-cheat foundation, not a
 * stylistic choice.
 */
@Serializable
sealed interface ClientFrame {
    @Serializable
    @SerialName("join")
    data class Join(val displayName: String) : ClientFrame
}

@Serializable
sealed interface ServerFrame {
    @Serializable
    @SerialName("welcome")
    data class Welcome(val netId: Long, val tickRate: Int) : ServerFrame

    @Serializable
    @SerialName("rejected")
    data class Rejected(val reason: String) : ServerFrame
}

internal val NetJson = Json { ignoreUnknownKeys = true }

internal fun ClientFrame.encode(): ByteArray = jsonPacket(NetJson.encodeToString(this))

internal fun ServerFrame.encode(): ByteArray = jsonPacket(NetJson.encodeToString(this))

/** Returns null on anything malformed; the caller decides whether that is a violation. */
internal fun decodeClientFrame(packet: ByteArray): ClientFrame? =
    jsonPayload(packet)?.let { runCatching { NetJson.decodeFromString<ClientFrame>(it) }.getOrNull() }

internal fun decodeServerFrame(packet: ByteArray): ServerFrame? =
    jsonPayload(packet)?.let { runCatching { NetJson.decodeFromString<ServerFrame>(it) }.getOrNull() }

internal fun opcodeOf(packet: ByteArray): Byte = if (packet.isEmpty()) 0 else packet[0]

private fun jsonPacket(payload: String): ByteArray {
    val body = payload.encodeToByteArray()
    val packet = ByteArray(body.size + 1)
    packet[0] = TickCodec.Opcode.JSON
    body.copyInto(packet, destinationOffset = 1)
    return packet
}

private fun jsonPayload(packet: ByteArray): String? {
    if (packet.isEmpty() || packet[0] != TickCodec.Opcode.JSON) return null
    return packet.decodeToString(startIndex = 1)
}
