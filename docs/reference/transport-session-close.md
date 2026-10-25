# Ktor transport session close

`KtorTransportSession` accepts outbound packets into a bounded queue while the session is open
and capacity is available. `send` does not wait for the peer to receive or process a packet;
its return value is not an acknowledgement of acceptance or delivery.

Normal `TransportSession.close()` on the Ktor implementation closes the outbound queue to new
packets, waits for the writer to drain accepted packets in order, then sends and flushes the
WebSocket close frame. Drain and flush share a one-second deadline. Concurrent or repeated close
calls wait for the same cleanup; cancelling a caller does not skip that cleanup.

A blocked writer or flush is cancelled at the deadline. Peer failure, timeout and outbound queue
overflow can discard packets. Overflow remains an abort with a `TRY_AGAIN_LATER` reason, rather
than a promise to drain an overloaded connection. Incoming queue overflow behavior is unchanged.
Neither a completed close nor a WebSocket flush is an acknowledgement by the peer application.

This ordering describes the Ktor server transport, not every implementation of the transport port.
