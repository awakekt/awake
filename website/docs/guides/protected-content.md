# Protected content

<p class="awake-lede">Read a published project's index and files with a short-lived read token that your game gets for its signed-in player, refreshed before it expires, retried once when the server refuses it, and paced when the server asks for fewer requests.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake:project</code></span>
<span class="awake-badge">module: <code>com.awakekt.awake.project:runtime</code></span>
<span class="awake-badge">transports: <code>com.awakekt.awake.project:transport-ktor</code>, <code>com.awakekt.awake.project:transport-browser</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

`IndexedAssetSource` reads a published project's files as its index lists them, checks each one
against the index's size and SHA-256, and keeps it in a `ContentCache`. It sends requests through a
fetch the host supplies, so `com.awakekt.awake:project` carries no HTTP library. When the content
server wants a read token on every request, use a `TokenAuthorizedFetch`: your game supplies the
token, and the engine attaches it, refreshes it, retries with it, and waits out a 429 or a 503 with `Retry-After`.

## Wire a token provider

**1. Pick a transport.** A `ContentTransport` sends one `GET` with the headers it is given and
answers the status, body and response headers. Two ready-made ones cover most games, each in its own
optional module, so a game takes only the HTTP stack it uses:

| Host | Module | Transport |
| --- | --- | --- |
| Desktop, Android, iOS: a game with a Ktor `HttpClient` | `com.awakekt.awake.project:transport-ktor` | `KtorContentTransport(client, baseUrl)` |
| Web (wasmJs), with no HTTP library | `com.awakekt.awake.project:transport-browser` | `BrowserContentTransport(baseUrl)`, over `fetch()` |
| Anything else | none | your own `ContentTransport`, below |

With Ktor, pass the client your game already has. Its engine (OkHttp or Android, Darwin, CIO or
Java, Js) and its plugins, such as `HttpTimeout`, are yours; the transport names no engine.

```kotlin title="Kotlin (Ktor)"
--8<-- "awake/project/transport-ktor/src/commonTest/kotlin/com/awakekt/awake/project/transport/ktor/KtorTransportDocsSampleTest.kt:ktor"
```

In the browser, the transport calls `fetch()` itself:

```kotlin title="Kotlin (wasmJs)"
--8<-- "awake/project/transport-browser/src/wasmJsTest/kotlin/com/awakekt/awake/project/transport/browser/BrowserContentTransportTest.kt:browser"
```

`baseUrl` is the folder the index was published to, ending in `/`; the index's relative URLs resolve
against it the way a browser resolves a link. Without it, the Ktor transport sends each URL as it
comes (for a client with its own `defaultRequest` URL) and the browser resolves it against the page.

To adapt another client, answer every status, 401, 403, 429 and 503 included, instead of throwing: that
is how a refused token is told apart from a missing file. Pass the response headers too, so the `Retry-After` of a 429 or
503 is honoured.

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/ProtectedContentDocsSampleTest.kt:transport"
```

**2. Supply the token.** The provider is a suspend function that answers a `ReadToken`: the token
and how long it stays valid. Get it however your game does, typically from your own server once
the player has signed in. The engine calls it on the first request and again before the token
expires, never twice at once.

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/ProtectedContentDocsSampleTest.kt:provider"
```

**3. Open the project through it.** Passing the `TokenAuthorizedFetch` to `IndexedAssetSource.open`
reads the index with the token too, then every file and [content bundle](content-bundles.md).

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/ProtectedContentDocsSampleTest.kt:open"
```

**4. Keep files between runs.** `platformContentCache(namespace)` answers the platform's default
`ContentCache`, so the next launch reads every file it already has instead of downloading it again;
only the index is fetched each time. Pass a name for your app or project that stays the same across
releases:

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/ProtectedContentDocsSampleTest.kt:platform-cache"
```

On Android, pass the app's cache folder too, since `com.awakekt.awake:project` never reaches a
`Context`; the one-argument call answers null there, which means no cache:

```kotlin title="Kotlin (Android)"
--8<-- "awake/project/src/androidHostTest/kotlin/com/awakekt/awake/project/PlatformContentCacheAndroidTest.kt:android-cache"
```

| Platform | Where the files go | Cache |
| --- | --- | --- |
| Web (wasmJs) | The origin's IndexedDB | `IndexedDbContentCache(namespace)` |
| Linux | `$XDG_CACHE_HOME/<namespace>/awake-content`, else `~/.cache/<namespace>/awake-content` | `FileSystemContentCache` |
| macOS | `~/Library/Caches/<namespace>/awake-content` | `FileSystemContentCache` |
| Windows | `%LOCALAPPDATA%\<namespace>\awake-content` | `FileSystemContentCache` |
| iOS | `Library/Caches/<namespace>/awake-content` in the app's container | `FileSystemContentCache` |
| Android | `<cacheDir>/<namespace>/awake-content`, with `platformContentCache(namespace, context.cacheDir)` | `FileSystemContentCache` |

- **The namespace is one folder name.** 1 to 128 ASCII letters, digits, `.`, `_` or `-`, starting
  with a letter or digit, such as `harbor-town` or `com.example.harbortown`. Anything else, a `/`,
  `..` or a Windows device name such as `CON`, throws `IllegalArgumentException` before any storage
  is touched. On desktop every app shares the user's cache folder, so make the name your app's own.
- **The cache never fails a load.** A read the storage refuses is a miss and the file comes from the
  network; a file it can't keep, on a full disk or past the browser's quota, is dropped, costing the
  next run a download. When the folder can't be created at all, `platformContentCache` answers null,
  and the project loads with no cache.
- **Old releases are pruned.** `open` deletes cached files the new index doesn't list
  (`retainCachedOnly = true`, the default), so the cache holds one release's files.
- **The system may empty it.** Every one of these is a cache folder: the user, or the system when
  space runs short, may delete it, and the next run downloads again.

With an index you already have, pass the fetch function instead:
`IndexedAssetSource(index, contentFetch::fetch, cache)`. Bundles go through the same fetch: a whole
bundle with `fetch`, and one member alone with `fetchRange` (`open(..., rangeReads = true)`), which
adds a `Range` header beside the token.

## A host that can't send headers

Where a request can carry only a URL, put the token in the query instead. The transport then
ignores `request.headers`, and `request.url` arrives with `?access_token=<token>` added (after any
query the URL already has, and before any fragment).

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/ProtectedContentDocsSampleTest.kt:query"
```

| Host | Placement |
| --- | --- |
| Desktop, Android, iOS: any HTTP client that sets request headers | `AuthorizationHeader`, the default |
| Browser `fetch()` | `AuthorizationHeader`; cross-origin, see [the browser and CORS](#the-browser-and-cors) |
| A loader that takes only a URL | `QueryParameter()` |

!!! warning "A token in the URL can be recorded"
    Servers, proxies, CDNs and browser history may log full URLs, query included. Use
    `QueryParameter` only where the host can't send a header, and keep the token short-lived.

## The browser and CORS

When the content server is on another origin than the page, its CORS answers decide what reaches
the game. A browser reports a request it refused for CORS exactly as it does a network failure, so
`BrowserContentTransport`'s error message lists what the server must allow.

| Response header | Value | Why |
| --- | --- | --- |
| `Access-Control-Allow-Origin` | the game's origin | Without it, every request fails before a status arrives. |
| `Access-Control-Allow-Headers` | `Authorization, Range` | `Authorization` makes the browser send a preflight `OPTIONS` first; `Range` is for a bundle member read alone. |
| `Access-Control-Expose-Headers` | `Retry-After` | Otherwise the browser hides it: a 429 backs off exponentially instead, and a 503 fails at once. |
| `Access-Control-Max-Age` | e.g. `600` | Lets the browser reuse a preflight answer instead of sending one per file. |

No cookies are sent cross-origin (`credentials: same-origin`), so the read token is the only
credential. With `QueryParameter` there is no preflight, at the cost of the token in the URL.

## Rate limits

A server that answers **429 Too Many Requests**, or **503 Service Unavailable with a `Retry-After`**,
is waited out and the request sent again, as `RateLimitBackoff` says (RFC 9110 lets a 503 say, with
`Retry-After`, how long the service stays unavailable):

- **`Retry-After` first.** The wait is exactly what the header says: a number of seconds, or an HTTP
  date (`Wed, 21 Oct 2026 07:28:00 GMT`, or either obsolete form). A date already past sends again
  at once.
- **Otherwise exponential, with jitter.** A 429 with no `Retry-After`, or one that doesn't parse,
  waits up to `initialDelay` before the first retry, each later one up to twice the one before, never
  more than `maxDelay`. Each wait is drawn from between half and all of that, so players refused
  together don't all come back together.
- **Bounded.** A request is sent at most `maxAttempts` times while it is answered 429 or 503 with
  `Retry-After`, and one fetch waits at most `maxTotalWait` in all. A wait that would go past that
  isn't started: the fetch fails at once with a `ContentFetchException` whose `status` is 429 or 503
  and whose message says why it stopped.
- **A 503 only with `Retry-After`.** A 503 without the header, or with one that doesn't parse, says
  nothing about when to come back and is not retried: the fetch fails at once with a
  `ContentFetchException` whose `status` is 503. There is no exponential fallback for a 503.
- **Only 429 and 503, only reads.** No other status is retried. Every content request is a `GET`,
  so sending it again changes nothing on the server.
- **Never blocking.** A wait suspends, so it is safe on the frame thread, and cancelling the fetch
  cancels it. After a wait, the token is checked again and refreshed if the wait brought it close to
  expiring.

Tune it, or turn it off with `RateLimitBackoff.None`:

```kotlin title="Kotlin"
--8<-- "awake/project/transport-ktor/src/commonTest/kotlin/com/awakekt/awake/project/transport/ktor/KtorTransportDocsSampleTest.kt:backoff"
```

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `maxAttempts` | `Int` | 4 | Sends of one request while it is answered 429 or 503 with `Retry-After`, the first included. 1 never retries. |
| `maxTotalWait` | `Duration` | 30 seconds | The longest one fetch waits in all. |
| `initialDelay` | `Duration` | 1 second | The longest first wait when the server sends no `Retry-After`. |
| `maxDelay` | `Duration` | 8 seconds | The longest any one wait grows to when the server sends no `Retry-After`. |
| `clock` | `Clock` | `Clock.System` | The wall clock an HTTP-date `Retry-After` is measured against. |
| `random` | `Random` | `Random.Default` | The jitter's source. |
| `delay` | `suspend (Duration) -> Unit` | `kotlinx.coroutines.delay` | Takes a wait; a test passes its own to record waits instead. |

!!! tip "Don't retry a 429 twice"
    If your Ktor client installs `HttpRequestRetry`, keep 429 out of what it retries, or each wait
    is taken twice. Network failures and timeouts stay the client's to retry.

## Properties

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `transport` | `ContentTransport` | required | Sends each request; resolves relative URLs against wherever the index came from. |
| `placement` | `ReadTokenPlacement` | `AuthorizationHeader` | `Authorization: Bearer <token>`, or `QueryParameter(name)`, by default `access_token`. |
| `refreshAhead` | `Duration` | 60 seconds | How long before expiry the next token is asked for. A token shorter-lived than twice this is replaced at half its lifetime. |
| `timeSource` | `TimeSource` | `TimeSource.Monotonic` | Measures a token's lifetime, unaffected by changes to the device clock. |
| `rateLimitBackoff` | `RateLimitBackoff` | `RateLimitBackoff()` | How a 429, or a 503 with `Retry-After`, is waited out; see [Rate limits](#rate-limits). |
| `tokenProvider` | `suspend () -> ReadToken` | required | Answers a current token. |

`ReadToken(value, expiresIn)` takes the token and how long it stays valid from now: an OAuth
`expires_in`, or a JWT's `exp` less the current time.

## How it works

- **Refresh before expiry.** The token is kept until it is within `refreshAhead` of expiring, then
  the next request asks the provider for a new one before it is sent.
- **One retry on refusal.** A 401 or 403 gets a fresh token and the request is sent once more. A
  second refusal throws `ContentFetchException` with that status and a message saying the fresh
  token was refused too.
- **A 429 waits, and so does a 503 with `Retry-After`.** See [Rate limits](#rate-limits). Any other
  status but 2xx, a 503 without `Retry-After` included, throws at once, with no retry.
- **One refresh for many requests.** `prefetch` reads up to six files at once. When they need a new
  token at the same moment, or are all refused, they wait on a single call to the provider and share
  its token. If that call fails, the requests waiting on it fail with it, and the next request asks
  again.
- **The cache never sees the token.** `ContentCache` stores checked bytes under their SHA-256, so a
  new token, or a new release that lists the same files, reads them from the cache. Only the index
  is fetched again.
- **The token stays out of messages.** `ContentFetchException` names the URL as you passed it,
  before the token was added. A transport failure whose message quotes the token is reported with
  it replaced by `<redacted>`, and without that cause attached. `ReadToken` and `ContentRequest`
  print without it, so logging one is safe.

!!! tip "Keep network retries and resumes in the transport"
    `TokenAuthorizedFetch` retries only a refused token, a 429 and a 503 with `Retry-After`. Network retries, timeouts and
    resumed downloads belong to your HTTP client, behind the transport.

## Play a protected project after sign-in

A routed app declares its routes before it starts, but a protected project can be read only once the
player has signed in. Give its route the project as a loader with `projectRoute`, from
`com.awakekt.awake.project:runtime`: the router runs the loader the first time the route is entered,
off the frame thread, and shows the route's loading scene until the project arrives.

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/commonTest/kotlin/com/awakekt/awake/project/runtime/ProtectedProjectRouteDocsSampleTest.kt:route"
```

When sign-in succeeds, complete what the loader waits for and enter the route:

```kotlin title="Kotlin"
--8<-- "awake/project/runtime/src/commonTest/kotlin/com/awakekt/awake/project/runtime/ProtectedProjectRouteDocsSampleTest.kt:signed-in"
```

Entering the route before the player has signed in is fine too: the loading scene plays until the
loader returns. To start reading the project before the player enters the route, call
`router.preload("harbor-town")` as sign-in succeeds; entering it once it has loaded plays it at once.

| When | What happens |
| --- | --- |
| The route is entered before the project has loaded | `activeSceneId` names the route, its loading scene plays, and `routeStatus` is `Loading`. The project's scene takes its place at the start of the first frame after the loader returns. |
| The route is entered after it has loaded | The project plays at once, with no loading frame. |
| The loader throws, for instance when the token is refused | The route stays on its loading scene with `routeStatus` `Failed(cause)`, and the failure is logged. Entering the route again with `switchTo` runs the loader again. |
| The player leaves while it loads | The load goes on, and the project waits, unplayed, for the next entry; the router never switches to the route on its own. Stopping the app cancels the load and closes a project that loaded but never played. |
| The player leaves the route after it played | Its scene stops and the project is closed, physics world included, because a project plays once. Entering again runs the loader again; the files it read come from the `ContentCache`. |

A route whose scene is not a project uses `deferredRoute(id) { ... }`, from `awake:scene:authoring`,
which returns a `sceneApp { }` instead. It keeps what it loaded for later entries unless it is given
`keepLoaded = false`. Routes declared with `route` load before the app starts, as they always have.

## See also

- [Content bundles](content-bundles.md)
- [Load a scene document](../get-started/load-a-scene-document.md)
- [Run in the browser](../get-started/run-in-the-browser.md)
- [Modules](../reference/modules.md)
