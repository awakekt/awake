/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.codec

/**
 * Pluggable serializer and deserializer bridging binary packet payloads with typed message structures.
 *
 * The game server host is completely agnostic of protocol details, opcodes, and serialization format (e.g. JSON, CBOR,
 * Protobuf, or custom binary).
 *
 * @param M The base message type.
 */
interface MessageCodec<M> {
    /**
     * Decodes a message from [bytes].
     *
     * @return Decoded message instance, or `null` if the packet is malformed, unrecognized, or incomplete.
     */
    fun decode(bytes: ByteArray): M?

    /**
     * Encodes [message] into its wire byte representation.
     */
    fun encode(message: M): ByteArray
}
