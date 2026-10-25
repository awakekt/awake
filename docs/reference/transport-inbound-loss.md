# Inbound packet-loss telemetry

Import `com.awakekt.awake.net.droppedInbound` to read a `TransportSession`'s optional counter:

```kotlin
val losses: Long? = session.droppedInbound
```

`null` means the implementation does not report this metric. A reporting implementation provides
the optional `InboundDropMetrics` capability. Existing `TransportSession` implementations need
no new method and retain their binary interface. The counter is cumulative for the session;
reading it does not reset it, and it remains readable after close.

The Ktor server reports the inbound channel's undelivered packets. Its `DROP_OLDEST` policy and
configured capacity are unchanged: a full queue evicts its oldest packet and retains the newest.
A burst that fits capacity records no loss. Atomic counting also includes packets handed to a
receiver whose continuation is cancelled before it can consume them. Cancellation that discards
buffered packets through the exposed channel also counts; graceful close retains unread buffered
packets and does not itself count them as dropped.

This is an actual-loss metric, not an overflow-only metric, queue occupancy, a network-loss
estimate, or evidence of peer abuse. A server can compare successive samples and choose its own
logging, rate-limiting or disconnection policy. Core does not kick peers based on this counter.

Counting runs through the channel's synchronous undelivered-element hook and performs only an
atomic increment. It does not run consumer callbacks or add an unbounded queue. The channel
retains responsibility for eviction and receive races.
