# Networking in the browser

<p class="awake-lede">A game's connection to its server on the web: the browser's own WebSocket, run in a dedicated Web Worker so a busy frame can't delay it, with the arrival lag and buffered bytes a client needs for ping timing and backpressure.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:net:client-browser</code></span>
<span class="awake-badge">module: <code>awake:net:api</code></span>
<span class="awake-badge">Platform: Web (wasmJs)</span>
</div>

`awake:net:client-browser` opens the browser's own `WebSocket`, with no HTTP or socket library. It
sends binary messages, one per packet, so it speaks to the Ktor server transport
(`awake:net:server-ktor`) unchanged. What it hands back is a `DirectTransport` from `awake:net:api`:
a `Transport` whose sends go to the socket on the caller's call stack, and which says how many bytes
the socket still holds and how long each packet waited to be handed on.

## Connect

```kotlin title="Kotlin (wasmJs)"
--8<-- "awake/net/client-browser/src/wasmJsTest/kotlin/com/awakekt/awake/net/browser/BrowserSocketDocsSample.kt:connect"
```

`connectBrowserSocket` returns once the socket has opened. It fails with `BrowserSocketFailed`, which
carries the close code, when the socket closes before it opens; cancelling it closes the socket.

Then read packets with `listen`, which hands each one on from the socket's own event, or with
`incoming()`, the `Transport` channel. Use one or the other.

## Serve the worker script

The socket's worker runs `awake-net/socket-worker.js`, which the page must serve beside `index.html`.
Apply Core's Gradle plugin to your web module and it puts the script there, for the development
server and the production build:

```kotlin title="build.gradle.kts"
plugins {
    id("com.awakekt.awake.plugin.socket-worker") version "<your Core version>"
}
```

It takes the script from the `client-browser-wasm-js` klib your app already depends on; keep the
plugin on the same version as Core. A module that doesn't depend on the client gets nothing. If you
can't apply the plugin, copy the file from the klib by hand:

```bash
unzip client-browser-wasm-js-<version>.klib 'awake-net/socket-worker.js' -d src/wasmJsMain/resources
```

To serve it from somewhere else, pass its URL, relative to the page, as `workerScript`.

## What runs where

| Work | Socket in a worker (default) | Socket on the page |
| --- | --- | --- |
| The `WebSocket`: open, send, receive, close | The worker's thread | The page's thread |
| Stamping when a message arrived | The worker, as the message event runs | Nothing: the packet is handed on from its own event |
| Reading `bufferedAmount` | The worker, after each send and every 50 ms while it isn't 0 | The page, when `bufferedBytes` is read |
| Decoding packets, your game's code, the frame | The page's thread | The page's thread |
| Copying a packet between Kotlin and JS | Once each way, through wasm linear memory; the `ArrayBuffer` then moves to or from the worker without another copy | Once each way, through wasm linear memory |

On the page, a socket's events are tasks on the page's one thread, so they run only between frames:
while a frame takes 100 ms, a reply that arrived at its start is seen 100 ms late, and the browser
lowers `bufferedAmount` late too. In a worker those events run on the worker's own thread at once,
whatever the frame is doing. Your code still runs on the page's thread, so a game keeps decoding there
and can still drain its packets once per frame.

## Time a ping

A packet from a worker comes with when it reached the socket. While `listen` hands it on,
`arrivalLag` is how long it then waited for the page's thread, in seconds; take it off now to time the
reply's arrival:

```kotlin title="Kotlin"
--8<-- "awake/net/client-browser/src/commonTest/kotlin/com/awakekt/awake/net/browser/BrowserSocketDocsSampleTest.kt:ping"
```

`arrivalLag` is 0 outside `listen`'s callback, and always 0 for a socket on the page, where nothing
measures the wait. The worker stamps messages on `performance.timeOrigin + performance.now()`, and the
page reads its clock the same way, because a dedicated worker's `performance.now()` counts from the
worker's own start, not the page's.

## Hold back sends on a full socket

`bufferedBytes` is never less than the socket holds: in a worker, the worker's last report plus the
bytes posted to it that it hasn't taken yet. Send-side backpressure can rely on it:

```kotlin title="Kotlin"
--8<-- "awake/net/client-browser/src/commonTest/kotlin/com/awakekt/awake/net/browser/BrowserSocketDocsSampleTest.kt:backpressure"
```

`schedule` runs the retry on the page's thread, with `setTimeout`.

## Fallback and opt-out

`mode` picks where the socket runs:

| `BrowserSocketMode` | Where the socket runs |
| --- | --- |
| `Auto` (default) | In a worker; on the page when the browser has no `Worker` or the script doesn't run, after logging why once |
| `Worker` | In a worker, or fails with `SocketWorkerFailed` |
| `Page` | On the page's thread |

```kotlin title="Kotlin (wasmJs)"
--8<-- "awake/net/client-browser/src/wasmJsTest/kotlin/com/awakekt/awake/net/browser/BrowserSocketDocsSample.kt:modes"
```

`runsInWorker` says which one a connection got.

## Properties

| Property | Type | What it does |
| --- | --- | --- |
| `bufferedBytes` | `Long` | Bytes the socket holds that it hasn't sent; 0 once closed. |
| `arrivalLag` | `Double` | Seconds the packet `listen` is handing on waited for the page's thread; 0 outside that call. |
| `runsInWorker` | `Boolean` | Whether the socket runs in a dedicated worker. |
| `closeCode` | `Int?` | The close event's code once closed; null while open. |
| `closeReason` | `String` | The close event's reason once closed; empty otherwise. |
| `state` | `StateFlow<ConnectionState>` | `Connected` while open, `Disconnected` once closed. |

## How it works

The page and the worker exchange messages. The page posts `{t: 'open', url}` once, then each packet
as a bare `ArrayBuffer` it transfers, then `{t: 'close'}`. The worker posts `{t: 'open'}` when the
socket opens, `{t: 'm', data, at}` for each binary message with its buffer transferred, `{t: 'b',
buffered, taken}` for `bufferedAmount` and the bytes it has taken, and `{t: 'x', code, reason, error}`
when the socket closes. Every packet that came before a close is handed on before it. A text message
is not a packet and is ignored.

A connection doesn't reconnect: when it closes, connect again. Answering the server's pings is your
protocol's business, so it stays in your code on the page, timed with `arrivalLag`.

!!! warning "Serve the script as JavaScript"
    A page that answers a missing file with an HTML page and status 200 still fails the worker, since
    the browser won't run HTML as a worker script. `Auto` then falls back to the page and logs
    "The socket worker awake-net/socket-worker.js could not run". Open the script's URL from the
    running page to check it: it must be JavaScript.

## See also

- [Client prediction](client-prediction.md): predict the player's own entity and correct it from the snapshots
- [Run in the browser](../get-started/run-in-the-browser.md)
- [Modules](../reference/modules.md)
