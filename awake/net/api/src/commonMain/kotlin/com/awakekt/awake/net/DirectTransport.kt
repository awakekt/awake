/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

/**
 * A client [Transport] whose socket takes a packet on the caller's own call stack and hands each
 * packet on from its own event, as a browser's `WebSocket` does: no coroutine, queue or frame stands
 * between the caller and the socket, so a send happens as it is made and a reply is seen as it is
 * handed on.
 *
 * It adds what such a socket lets a client see:
 * - [bufferedBytes], the bytes the socket holds that it hasn't put on the network yet, for a client
 *   that holds back sends while the socket is full (send-side backpressure);
 * - [arrivalLag], how long the packet being handed on waited between reaching the socket and being
 *   handed on, so a client that times a ping's reply can leave out the time it waited for the
 *   client's own thread.
 *
 * Every member is called on the thread the socket hands packets on, which in a browser is the page's
 * one event loop, the thread the frame also runs on.
 */
interface DirectTransport : Transport {
    /**
     * Bytes handed to the socket that it hasn't put on the network yet: a browser's
     * `WebSocket.bufferedAmount`. Never less than the socket holds; 0 once the socket has closed.
     */
    val bufferedBytes: Long

    /**
     * How long, in seconds, the packet [listen] is handing on now waited between reaching the socket
     * and being handed on; 0 outside that call. It is 0 where the socket's own event hands the
     * packet on, and the time the packet waited for this thread where the socket runs on another
     * one, such as a browser socket in a Web Worker. A reply's arrival is then timed as now less
     * this.
     */
    val arrivalLag: Double get() = 0.0

    /**
     * Hands [packet] to the socket now, whatever [channel][DeliveryChannel] a [send] would name. Once
     * the socket has closed the packet is dropped, as [Transport.state] says.
     */
    fun sendNow(packet: ByteArray)

    /**
     * Calls [onPacket] with each packet as the socket hands it on, and [onClosed] once when the
     * socket closes, after every packet that came before the close. Packets that arrived before this
     * call are handed on first, in order. It stands in for [Transport.incoming], which then stays
     * empty; call it once.
     */
    fun listen(onPacket: (ByteArray) -> Unit, onClosed: () -> Unit)

    /**
     * Runs [action] once, about [millis] from now, on the socket's thread: for example to try again
     * a send that backpressure held back.
     */
    fun schedule(millis: Int, action: () -> Unit)
}
