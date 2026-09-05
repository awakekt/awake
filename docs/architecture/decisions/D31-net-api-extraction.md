# D31: Net — What the Transport Module Owns, and What Stays in the Game

## Decision

`:awake:net:api` exists and carries exactly two things: the **transport port** and the
**packet buffers**. Everything about what bytes *mean* stays in the consuming project.

| In `:awake:net:api` | Stays in the consumer |
|---|---|
| `Transport`, `TransportServer`, `TransportSession` | Ktor/WebSocket implementation (today: `samples:net-demo`) |
| `DeliveryChannel`, `ConnectionState`, `SessionId`, `NetId` | Opcodes, frame types, handshake shape |
| `PacketWriter`, `PacketReader` (varint, zig-zag, bounds) | Quantization choices, `SnapshotBuffer`, `TickCodec` |
| — | Accounts, lobby, matchmaking, protocol versioning |

Dependencies: `kotlinx-coroutines-core` only. **Ktor must never appear here** — that is the
point of the split, not a style preference.

## Why this line and not another

The framework boundary (rule 3, [framework-game-boundary.md](../reference/framework-game-boundary.md))
puts protocol messages in the consuming game. A wire format encodes game decisions: what an
entity is, which fields replicate, what precision a position needs. `TickCodec` quantizes to
centimetres because *this demo* is a metres-scale walking game; a space sim would pick
differently. So the codec is consumer code and the buffers underneath it are not.

The test for whether something belongs here: **could a second, unrelated game use it
unchanged?** A varint writer, yes. A snapshot of `(netId, x, y)`, no.

## Promotion gate, and how it was met

The plan ([network.md](../plans/network.md)) says promote after a second consumer. That has
**not** happened — `samples:net-demo` is still the only one. The extraction was directed
anyway, on the reasoning that the port's shape is already settled by Phase 0 and Phase 1: it
survived a JSON protocol, a binary one, and a decode path that must not allocate, without
changing. Recording the exception per boundary rule 5:

- **Consumers:** one (`samples:net-demo`). A wasmJs browser client is next and will be the
  second.
- **Missing API:** none — nothing forced this; it was scheduled work.
- **Contract owner:** `:awake:net:api`.
- **Excluded policy:** protocol, auth, sessions, matchmaking, quantization.
- **Validation:** compiles on all five targets; `PacketBuffersTest` covers the buffers
  directly rather than only through the sample.

If the browser client reshapes the port, that is the signal this was one phase early.

## Why hand-rolled packet buffers rather than kotlinx-io

Ktor 3 has no packet type of its own; it delegates to `kotlinx-io`'s `Source`/`Sink`/`Buffer`.
So the real question is whether `PacketWriter`/`PacketReader` should wrap `kotlinx-io.Buffer`.

Three findings decided it:

1. **kotlinx-io has no varint or zig-zag.** Its integer primitives are fixed-width. The
   compact-encoding layer is hand-written either way, so the saving is only the byte cursor
   underneath — roughly sixty lines.
2. **Underflow semantics are the wrong shape.** `require()` throws `EOFException`; the
   non-throwing counterpart is `request()`, which means a guard before *every* read to match
   what `PacketReader.ok` gives for free. Rejecting a malformed packet must allocate nothing —
   that is measured, not asserted (100k hostile packets, 0 bytes), and an exception per packet
   is exactly what a flood attack is shopping for.
3. **A segmented rope buys nothing here.** A datagram is one small contiguous `ByteArray`
   (≤1400 bytes) that the transport has already materialized. `Buffer`'s segmentation pays off
   for streaming and large payloads, neither of which describes a tick packet.

Against that: `kotlinx-io` is better tested than anything written here, and it is already on
the classpath transitively via Ktor — though not for `:awake:net:api`, which cannot depend on
Ktor, so it would be an explicit new dependency on an experimental API.

**Revisit if** the transport ever needs framing over a stream (a TCP fallback rather than
WebSocket), or if packets stop being single contiguous arrays. Either one flips finding 3, and
finding 1 is the only thing left.

## Consequences

- `:awake:ecs`, `:awake:scene:*` and every render module keep zero dependency on `:awake:net:*`.
- Swapping the WebSocket transport for UDP or WebTransport is one new module implementing
  `Transport`, with no change above the port.
- The sample keeps its own protocol, which is the arrangement the boundary asks for and also
  the honest one: nobody else's game wants these opcodes.
