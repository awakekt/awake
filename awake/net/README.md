# Awake Networking (`:awake:net:*`)

`:awake:net` provides Awake's multiplatform transport contracts, zero-allocation binary packet primitives, compile-time `@Serializable` packet codecs, and authoritative Ktor server hosting.

## Modules

```text
net:api          Transport, DirectTransport, AbortableTransport, DirectOutbox, DeliveryChannel,
                 TransportKeepAlive, StallWatch, TransportStallWatch, PolledStallWatch,
                 TransportStallDetector, SplitPacketInbox, InboundQueue, ReadEnd, InputBudget,
                 TickLag, PingLimit, PingStats, PingMeter,
                 SessionId, NetId, PacketWriter, PacketReader, PacketFormat, PacketOpcodeRegistry,
                 MessageRouter, ClientNetFeature, ClientNetFeatures, NetViolation, ViolationSink,
                 ViolationWindow
net:server-ktor  Ktor server plugins: GameTransport (WebSocket framing, ping keepalive & backpressure),
                 GameServerHost (fixed-rate catch-up TickSchedule, TickClock/ManualTickClock, TickHealth,
                 MessageRouter, GameFeature, SaveScheduler)
net:client-browser  connectBrowserSocket (wasmJs): a DirectTransport over the browser's own
                 WebSocket, run in a dedicated Web Worker (socket-worker.js) or on the page,
                 with shared Web Lock & unthrottled worker timer scheduling
net:client-ktor  connectWebSocket, WebSockets.Config.boundOutgoingFrames: bounded-outbox
                 AbortableTransport over a Ktor HttpClient (close-safe pre-kick delivery &
                 non-throwing post-close send drop)
net:prediction   PredictionHistory, reconcile -> Correction (None, Blend, Shift, Snap, Wait),
                 ReconcileRules, PredictedEntity, SnapshotTimeline, AdaptiveSnapshotTimeline:
                 client-side prediction, reconciliation, and adaptive/pause-safe playback timing
```

`:awake:net` is a pure container directory (`:awake:net:api`, `:awake:net:server-ktor`, `:awake:net:client-ktor`, `:awake:net:client-browser` and `:awake:net:prediction`), matching `:awake:physics:api`. It depends on no ECS, scene, or rendering modules.

## Ktor client (`:awake:net:client-ktor`) & `DirectOutbox` (`:awake:net:api`)

`connectWebSocket(url)` (or `connectWebSocket(url, client, closeClient)`) in `:awake:net:client-ktor` opens a binary WebSocket `AbortableTransport` over Ktor without forcing a Ktor dependency onto `:awake:net:api`. `WebSockets.Config.boundOutgoingFrames()` bounds Ktor's outgoing frame queue (`OUTGOING_FRAMES = 1` by default) so socket congestion surfaces directly to callers instead of piling up silently; inbound frames are drained on the `HttpClient` scope so pre-close server frames (such as kick reasons) always reach `incoming()` before close, and post-close sends drop cleanly without throwing.

For `DirectTransport` sockets (such as `:awake:net:client-browser`), `DirectOutbox<M>` in `:awake:net:api` passes discrete packets (`sendNow`) straight through in FIFO order while folding replaceable state updates (`submit`) into a single waiting slot (`merge`, `folded`, `waiting`) whenever `bufferedBytes > limit` (`512 B` default), retrying every `retryMillis` (`8 ms` default).

## Browser client (`:awake:net:client-browser`)

On wasmJs, `connectBrowserSocket(url)` opens the browser's own `WebSocket` (binary messages, one per
packet, so the Ktor server transport needs no change) and returns a `BrowserSocketTransport`, a
`DirectTransport` from `:awake:net:api`. By default the socket runs in a dedicated Web Worker, so a
frame that holds the page's thread delays neither when a reply is stamped nor what `bufferedAmount`
reads. The worker's script ships in the klib as `awake-net/socket-worker.js`; apply the
`com.awakekt.awake.plugin.socket-worker` Gradle plugin to the web app so its page serves it. Without
it, or in a browser without `Worker`, the socket falls back to the page and says so once. The
[web networking guide](../../website/docs/guides/web-networking.md) says what runs where.

## Client prediction (`:awake:net:prediction`)

A client moves its own entity at once and corrects it from the server's snapshots. `PredictionHistory`
keeps the pose each input left the entity at; `reconcile` compares a snapshot with the pose kept for
the input it acknowledged, not the pose drawn now, and answers with a `Correction`: blend a small
drift, shift by a real one, snap only for a far drift, a lost history or a game's own rule
(`ReconcileRules`), or ease to a stop once the server has heard no input for as long as it guesses
on. It depends on `:awake:core:math` alone, with no transport, ECS or scene. The
[client prediction guide](../../website/docs/guides/client-prediction.md) shows a client loop.
The opt-in [`awake:scene:net`](../scene/net/README.md) module binds it to root ECS transforms.

## Idle connection & background-pause resilience

Idle sessions and multi-minute background-tab or window-focus pauses require coordination across transport timers, inbound queues, playback clocks, and server timeouts:

- **Shared Web Lock & worker timer scheduling (`:awake:net:client-browser`)**: `connectBrowserSocket` holds a shared browser Web Lock (`navigator.locks`, enabled by default when available with graceful no-op fallback) for the socket's connected lifetime so Chromium-based browsers do not freeze background tabs after five minutes and stall WebSocket `PONG` replies. When running in `socket-worker.js`, `DirectTransport.schedule` executes timers on the worker thread rather than the throttled page thread.
- **Unthrottled keepalive & two-lane inbox (`:awake:net:api`)**: `TransportKeepAlive` (`DirectTransport.startKeepAlive`) drives periodic heartbeats via `DirectTransport.schedule` (optionally `onlyWhenIdle = true` with `notifyOutbound()`) to keep NAT mappings and reverse proxies warm without per-tick closure allocations. `SplitPacketInbox<M, S>` separates bounded discrete reliable messages (`offerMessage` / `drainMessages`) from a `DROP_OLDEST` world-snapshot lane (`offerSnapshot` / `latestSnapshot`) so high-rate snapshots arriving while the render loop is paused never evict or head-of-line block discrete events.
- **Stall detection (`:awake:net:api`)**: `TransportStallDetector` is a pure, clock-driven rule with two stall cases. **Upstream**: `bufferedBytes` is above 0 and hasn't fallen for over `upstreamAfterSeconds` (default 1 s). **Downstream**: while `expectInbound` is set, nothing has been `received` for over `downstreamAfterSeconds` (default 2 s). A reading gap longer than a threshold restarts a wait that isn't a stall yet, and a stall already under way carries on. `DirectTransport.watchStalls()` drives it every 250 ms through `DirectTransport.schedule` on the socket's thread, so it needs neither outbound traffic nor a render frame. The client calls `received()` from its `listen` callback. `state: StateFlow<TransportStallState>` is the signal to observe: for example, reconnect once `state.value.seconds` passes the client's own limit. `onEnded` reports each stall's direction and length for diagnostics.
  For any `Transport`, `Transport.watchStalls(scope)` returns the `StallWatch` that fits it, so a client doesn't branch on the transport's kind. A `DirectTransport` gets `TransportStallWatch` on its own thread. Any other transport, such as the Ktor WebSocket client on the JVM and Android, gets `PolledStallWatch`: a coroutine on `scope` that checks downstream only, because such a socket can't say how many bytes it holds unsent.
- **Pause-aware & adaptive snapshot timelines (`:awake:net:prediction`)**: `SnapshotTimeline` smooths `clientTime - serverTime` clock offsets (`gain`) behind `bufferSeconds` during normal play, and immediately rebases both `offset` and `playbackTime` once drift exceeds `maxBehindSeconds` so clamped frame deltas on resume do not leave remote entity interpolation minutes behind server time. `AdaptiveSnapshotTimeline` tracks a rolling minimum arrival offset, learns the median snapshot interval, measures 95th-percentile arrival lateness, adapts playback delay within `minDelaySeconds..maxDelaySeconds`, holds playback at an extrapolation cap across stalls (`hold`), and rebases immediately across long client pauses.
- **Decaying violation counters & server WebSocket pings (`:awake:net:api`, `:awake:net:server-ktor`)**: `ViolationWindow` (`decayTicks`, `record`, `countAt`) decays isolated post-pause burst counts over clean simulation ticks while sustained floods still cross enforcement thresholds. On the server, `GameTransportConfig` configures WebSocket `pingPeriod` and `timeout` (`Duration.ZERO` or `Duration.INFINITE` to disable), coexists with pre-installed Ktor `WebSockets`, and closes unresponsive peers cleanly with `SessionCloseReason.PeerClosed`.
- **Inbound packet/byte bounds & server-time input/ping budgets (`:awake:net:api`)**: `InboundQueue` enforces dual packet-count and byte-count caps between a connection's async reader (`ReceiveChannel.readEach` / `TransportSession.readEach` -> `ReadEnd`) and its consumer, permanently latching `overflowed` on flood or `DROP_OLDEST` inbox loss (`ReadEnd.InboxFull`). `InputBudget` and `TickLag` rate-limit client inputs and per-tick discrete requests in monotonic server time (`perSecond`, `burst`, `stallSeconds` sequence-verified gap credit, and `longestNanos` stall scaling) so neither network bunching nor late server ticks kick steady clients. `PingLimit` caps sustained ping replies (`perSecond`, `burst`) while answering post-stall bursts in full.
- **Session RTT histograms & client ping metering (`:awake:net:api`)**: `PingStats` accumulates per-session RTT samples into zero-allocation fine/coarse buckets (`5 ms` below `1 s`, `250 ms` up to `10 s` by default) for instant `percentile(fraction)` queries and RFC 3550 `1/16` EWMA `jitterMs()` reporting across threads. `PingMeter` pairs outbound client ping nonces (`send(nowSeconds)`) with inbound pongs (`receive(nonce, nowSeconds)`) in a fixed ring, smoothing RTT (`smoothedMs`) while escalating `readingMs(nowSeconds)` immediately when any remembered ping waits past `staleSeconds`.

---

## Wire Format Channels & Architectural Scoreboard

Multiplayer games and authoritative servers have distinct data planes. Do not force a single wire format onto every channel:

| Channel / Plane | Recommended Format | Why |
| :--- | :--- | :--- |
| **1. Handshake / Lobby / REST Admin** | `kotlinx-serialization-json` | Readable in browser devtools, standard HTTP/RFC 9457 error responses, off the tick loop. |
| **2. Evolving / Persisted / Cross-Service Data** *(DB blobs, replays, external tooling)* | `kotlinx-serialization-protobuf` (`ProtoBuf`) | Field tags (`@ProtoNumber`) allow seamless forward/backward schema evolution across rolling upgrades or persisted state. |
| **3. Live Client $\leftrightarrow$ Server Game Packets** *(Chat, Guild, Party, Trade, Quests, Skills)* | **`PacketFormat` + `PacketOpcodeRegistry`** | Compile-time `@Serializable` codegen over `PacketWriter`/`PacketReader`: **0 lines of manual `write*`/`read*` code**, tagless wire layout, and declarative **pre-allocation DoS bounds** (`@PacketMaxBytes`, `@PacketCollectionSize`, `@PacketIntRange`). |
| **4. 20–60 Hz Tick Hot Path** *(`Input`, `Snapshot` entity deltas)* | **Direct `PacketWriter` / `PacketReader`** | Sub-byte bitflags, conditional delta fields, and zero per-tick heap allocations on the same underlying buffer. |

### Architectural Comparison Scoreboard

| Criteria | Protocol Buffers (`ProtoBuf`) | `kotlinx.io` (`Buffer` / `ByteReadPacket`) | Hand-Rolled `PacketWriter` / `PacketReader` | **Awake `PacketFormat` + `PacketOpcodeRegistry`** |
| :--- | :--- | :--- | :--- | :--- |
| **Bandwidth Efficiency** | **7 / 10** *(Field tag headers; no float quantization)* | **8 / 10** *(No tags, no built-in VarInt/ZigZag)* | **10 / 10** *(Bitflags + tagless VarInt/ZigZag + mm quantization)* | **9.5 / 10** *(Tagless VarInt/ZigZag + `@PacketMillis`; coexists with bit-packed tick packets)* |
| **CPU & GC Speed** | **6 / 10** *(Tag dispatch + decode allocations)* | **8 / 10** *(Segment pooling, but throws `EOFException`)* | **10 / 10** *(Zero-allocation reusable `ByteArray` cursor)* | **9 / 10** *(Compile-time generated serializer directly over reusable `PacketWriter`/`PacketReader`)* |
| **Untrusted Input / DoS Security** | **6 / 10** *(No per-field string/list caps or numeric ranges during decode)* | **5 / 10** *(Manual bounds; `EOFException` stack-trace cost on bad packets)* | **9.5 / 10** *(Pre-allocation caps + branchless `reader.ok`, but manual per field)* | **10 / 10** *(Declarative `@PacketMaxBytes`, `@PacketCollectionSize`, `@PacketIntRange` checked **before** allocation + branchless `reader.ok`)* |
| **Development Speed** | **9.5 / 10** *(Declarative `@Serializable` / `.proto`)* | **5 / 10** *(Manual stream reads/writes)* | **4 / 10** *(Hundreds of lines of manual `when` branches)* | **9.5 / 10** *(Declare `@Serializable data class` + 1-line `register<T>(OPCODE)`)* |
| **Schema Evolution** | **10 / 10** *(Skips unknown field tags)* | **3 / 10** *(Positional byte order)* | **3 / 10** *(Positional byte order; uses protocol version)* | **4 / 10** *(Positional wire order; pair with `ProtoBuf` when persistence/evolution is needed)* |

---

## Declaring & Registering Packets with `PacketFormat`

Annotate packet classes with `@Serializable` and bounds/quantization annotations from `com.awakekt.awake.net`:

```kotlin
@Serializable
sealed interface GamePacket {
    @Serializable
    data class UseSkill(
        @PacketMaxBytes(64, nonEmpty = true) val skill: String,
        @PacketZeroNullNetId val target: NetId? = null,
    ) : GamePacket

    @Serializable
    data class NaviPoint(
        val from: NetId,
        @PacketMaxBytes(32, nonEmpty = true) val name: String,
        @PacketMillis val x: Float,
        @PacketMillis val z: Float,
    ) : GamePacket
}

val registry = PacketOpcodeRegistry<GamePacket>().apply {
    register<GamePacket.UseSkill>(opcode = 1)
    register<GamePacket.NaviPoint>(opcode = 2) { it.from.value > 0L }
}
```

- **Pre-allocation DoS safety**: `@PacketMaxBytes` and `@PacketCollectionSize` verify wire lengths against `reader.remaining` and configured maximums *before* allocating arrays or collections. Anything malformed flips `reader.ok = false` and returns `null` without throwing exceptions.
- **Hot-path coexistence**: `PacketOpcodeRegistry.encode(writer, msg)` returns `false` for unregistered hot-path messages (like bit-packed `Input` or `Snapshot`), allowing custom bit-level encoding on the exact same `PacketWriter` and `PacketReader`.

---

## Modular Client Feature Routing (`MessageRouter` & `ClientNetFeature`)

Multiplatform clients (`wasmJs`, Desktop, Android, iOS) split inbound packet handlers, outbound request pumps, and session lifecycle hooks by feature using `clientNetFeature` and `clientNetFeatures` from `:awake:net:api` instead of accumulating a monolithic `when (packet)` dispatcher:

```kotlin
val navigationFeature = clientNetFeature<ClientNetContext, GamePacket>("navigation") {
    on<GamePacket.NaviPoint> { ctx, packet -> ctx.applyNaviPoint(packet) }
    flushOutbound { ctx -> ctx.drainQueuedWaypoints() }
    onConnected { ctx -> ctx.resetWaypoints() }
    onDisconnected { ctx -> ctx.resetWaypoints() }
}

val features = clientNetFeatures<ClientNetContext, GamePacket> {
    install(navigationFeature)
}
```

- **`O(1)` zero-allocation dispatch**: `MessageRouter<C, M>` and `ClientNetFeatures<C, M>` index handlers by `KClass<out M>` in a single `HashMap` and iterate lifecycle/flush hooks over flat arrays.
- **Fail-fast registration**: Duplicate feature IDs or colliding message handler registrations throw `IllegalArgumentException` at setup time.
