# Protected content

<p class="awake-lede">Read a published project's index and files with a short-lived read token that your game gets for its signed-in player, refreshed before it expires and retried once when the server refuses it.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake:project</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

`IndexedAssetSource` reads a published project's files as its index lists them, checks each one
against the index's size and SHA-256, and keeps it in a `ContentCache`. It sends requests through a
fetch the host supplies, so Core carries no HTTP library. When the content server wants a read token
on every request, wrap that fetch in a `TokenAuthorizedFetch`: your game supplies the token, and the
engine attaches it, refreshes it and retries with it.

## Wire a token provider

**1. Adapt your HTTP client.** A `ContentTransport` sends one request with the headers it is given,
and answers the status and body. Answer every status, 401 and 403 included, instead of throwing:
that is how a refused token is told apart from a missing file.

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
reads the index with the token too, then every file.

```kotlin title="Kotlin"
--8<-- "awake/project/src/commonTest/kotlin/com/awakekt/awake/project/ProtectedContentDocsSampleTest.kt:open"
```

With an index you already have, pass the fetch function instead:
`IndexedAssetSource(index, contentFetch::fetch, cache)`.

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
| Browser `fetch()` | `AuthorizationHeader`; cross-origin, the content server must allow `Authorization` in its CORS preflight |
| A loader that takes only a URL | `QueryParameter()` |

!!! warning "A token in the URL can be recorded"
    Servers, proxies, CDNs and browser history may log full URLs, query included. Use
    `QueryParameter` only where the host can't send a header, and keep the token short-lived.

## Properties

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `transport` | `ContentTransport` | required | Sends each request; resolves relative URLs against wherever the index came from. |
| `placement` | `ReadTokenPlacement` | `AuthorizationHeader` | `Authorization: Bearer <token>`, or `QueryParameter(name)`, by default `access_token`. |
| `refreshAhead` | `Duration` | 60 seconds | How long before expiry the next token is asked for. A token shorter-lived than twice this is replaced at half its lifetime. |
| `timeSource` | `TimeSource` | `TimeSource.Monotonic` | Measures a token's lifetime, unaffected by changes to the device clock. |
| `tokenProvider` | `suspend () -> ReadToken` | required | Answers a current token. |

`ReadToken(value, expiresIn)` takes the token and how long it stays valid from now: an OAuth
`expires_in`, or a JWT's `exp` less the current time.

## How it works

- **Refresh before expiry.** The token is kept until it is within `refreshAhead` of expiring, then
  the next request asks the provider for a new one before it is sent.
- **One retry on refusal.** A 401 or 403 gets a fresh token and the request is sent once more. A
  second refusal throws `ContentFetchException` with that status and a message saying the fresh
  token was refused too. Any status other than 2xx throws at once, with no retry.
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

!!! tip "Keep retries and resumes in the transport"
    `TokenAuthorizedFetch` retries only a refused token. Network retries, timeouts and resumed
    downloads belong to your HTTP client, behind the transport.

## See also

- [Load a scene document](../get-started/load-a-scene-document.md)
- [Run in the browser](../get-started/run-in-the-browser.md)
- [Modules](../reference/modules.md)
