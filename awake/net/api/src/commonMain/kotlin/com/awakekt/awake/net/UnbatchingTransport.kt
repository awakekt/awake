/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

/**
 * Client side of [BatchFrame]: a [Transport] whose [incoming] yields the packets inside each batch
 * [delegate] receives, in order, the same stream a client without batching sees, so consumer
 * message handlers do not change.
 *
 * Wrap a transport only when the connection asked for batching
 * ([BatchFrame.NEGOTIATION_PARAMETER]); the server then sends nothing but batches. A frame that is
 * not a well-formed batch is dropped whole and counted in [malformed]; the connection stays open,
 * as it does for a game packet the codec cannot decode. A `DirectTransport` client unpacks in its
 * own `listen` callback with [BatchFrame.decode] instead.
 *
 * @param delegate The connected transport.
 * @param scope Runs the unpacking loop; it ends when [delegate]'s incoming channel closes.
 */
class UnbatchingTransport(
    private val delegate: Transport,
    scope: CoroutineScope,
) : Transport by delegate {
    private val unpacked = Channel<ByteArray>(Channel.BUFFERED)

    /** Frames dropped for not being a well-formed batch. */
    var malformed: Long = 0L
        private set

    init {
        scope.launch {
            for (frame in delegate.incoming()) {
                val parts = BatchFrame.decode(frame)
                if (parts == null) malformed++ else parts.forEach { unpacked.send(it) }
            }
        }.invokeOnCompletion { cause -> unpacked.close(cause?.takeUnless { it is CancellationException }) }
    }

    override fun incoming(): ReceiveChannel<ByteArray> = unpacked
}
