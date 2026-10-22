# Awake Networking (`:awake:net:*`)

`:awake:net` provides Awake's multiplatform transport contracts, zero-allocation binary packet primitives, compile-time `@Serializable` packet codecs, and authoritative Ktor server hosting.

## Modules

```text
net:api          Transport, DeliveryChannel, SessionId, NetId, PacketWriter, PacketReader,
                 PacketFormat, PacketOpcodeRegistry, NetViolation, ViolationSink
net:server-ktor  Ktor server plugins: GameTransport (WebSocket framing & backpressure),
                 GameServerHost (fixed-rate tick loop, MessageRouter, GameFeature, SaveScheduler)
```

`:awake:net` is a pure container directory (`:awake:net:api` and `:awake:net:server-ktor`), matching `:awake:physics:api`. It depends on no ECS, scene, or rendering modules.

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
