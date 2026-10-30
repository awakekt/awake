# Launch parameters and host actions

<p class="awake-lede">Read what a game was launched with, such as a server name or a debug flag, the same way on every platform, and ask the host to open a link or reload the page.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake.engine:window</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

A launch parameter is read from a different place on each platform: the page URL in a browser, the
command line on desktop, the launch `Intent` on Android, the launch URL on iOS. `LaunchParameters`
puts them in one shape, so game code reads a parameter once and each platform's entry point decides
where it comes from. `HostActions` is a separate service for the things a game asks of its host:
`openUrl` everywhere, and `pageReload` on the web only.

!!! danger "Launch parameters are untrusted input"
    A link, a shortcut or a command line that anyone can share controls them. A page opened as
    `https://game.example/?server=evil.example` names whatever server its author likes, and an
    Android app can be launched with extras by any other app. Validate every value before using
    it: check a server against an allow-list, a number against its range, a mode against the modes
    you have. Never pass one unchecked to a file path, a network address, a shell or `openUrl`.
    A key can also repeat any number of times, so bound how many values you read.

## Read the parameters

Build the parameters in the platform entry point and register them as a service. Game code in
`commonMain` then looks them up without knowing where they came from.

=== "Web"

    ```kotlin title="wasmJsMain"
    val app = app {
        service(LaunchParameters::class, browserLaunchParameters())
        service(HostActions::class, browserHostActions())
    }
    ```

=== "Desktop"

    ```kotlin title="desktopMain"
    fun main(args: Array<String>) {
        val app = app {
            service(LaunchParameters::class, desktopLaunchParameters(args))
            service(HostActions::class, desktopHostActions())
        }
    }
    ```

=== "Android"

    ```kotlin title="androidMain"
    class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val app = app {
                service(LaunchParameters::class, intent.launchParameters())
                service(HostActions::class, androidHostActions(this@MainActivity))
            }
        }
    }
    ```

=== "iOS"

    ```kotlin title="iosMain"
    // launchUrl is the URL the host app received in application(_:open:options:), or null.
    fun makeViewController(launchUrl: String?): UIViewController {
        val app = app {
            service(LaunchParameters::class, iosLaunchParameters(launchUrl))
            service(HostActions::class, iosHostActions())
        }
        // ...
    }
    ```

Then read them anywhere:

```kotlin title="commonMain"
val parameters = lifecycle.requireService<LaunchParameters>()

val server = parameters.first("server")?.takeIf { it in allowedServers } ?: defaultServer
val tags = parameters.values("tag").take(8)
val debug = parameters.has("debug")
```

| Member | Returns |
| --- | --- |
| `has(key)` | True when the key was passed, with a value or as a bare flag. |
| `first(key)` | The first value, or `null` when the key is absent. `""` for a bare flag. |
| `values(key)` | Every value in order, or an empty list. A repeated key keeps all of them. |
| `keys` | The keys in the order they first appeared. |
| `toMap()` | A copy: each key with its list of values. |

A bare flag and an empty value are the same: `?debug` and `?debug=` both give `""`. A browser cannot
tell them apart, so no platform does. `toString()` lists the keys and never the values, so a token
in a link does not reach a log.

## Parsing rules

The rules live in common code, in `LaunchParameters`, and each platform reads its source through
one of them. The same parameters give the same result everywhere; the tests state one set of
parameters in each encoding and compare.

| Source | Rule |
| --- | --- |
| **Web**: page URL query<br>`fromQuery` | Like `URLSearchParams`. One leading `?` is dropped, `&` separates, an empty part is skipped. A part splits at its **first** `=`, so `token=a=b` is `a=b`; no `=` is a bare flag. `+` is a space and `%XX` a UTF-8 byte, in keys and values; a malformed `%` stays as written and invalid UTF-8 becomes U+FFFD. The `#fragment` is never read. |
| **Desktop**: `main` arguments<br>`fromArguments` | `--key=value` splits at the first `=`; `--flag` is a bare flag. Values are taken as given, with no percent-decoding. **`--key value` is not a pair**: `--key` is a flag and `value` is a stray argument, because a flag followed by a positional argument would otherwise swallow it. Anything not starting with `--` is ignored, and a bare `--` ends the options. |
| **Android**: launch `Intent`<br>`Intent.launchParameters()` | First the data URI's query, read as the web rule, then the extras. A `String` or other `CharSequence` extra is one value. A boolean, number or `Char` extra is its `toString()`. A `String[]` or list of text is one value per element, so a repeated key survives. A `null` extra is a bare flag. A `Bundle`, a `Parcelable`, a primitive array or an extra the system cannot unparcel is skipped. |
| **iOS**: launch URL<br>`fromUrl` | The query of the URL, read as the web rule. The part before `?` and the `#fragment` are ignored. |

For every source: a repeated key keeps all its values in order, an empty key is dropped, keys are
case-sensitive, and nothing is trimmed or validated.

### iOS

iOS hands the launch URL only to the app delegate (`application(_:open:options:)`, or `onOpenURL` in
SwiftUI). That is host app code, not part of this library, so the library cannot read it. The host
app passes the URL string in: `iosLaunchParameters(url.absoluteString)`. A launch from the home
screen has no URL, and `iosLaunchParameters()` returns empty parameters.

## Host actions

`HostActions` is separate from `LaunchParameters` because it is a different capability: it does
something, rather than report something.

```kotlin title="commonMain"
val actions = lifecycle.requireService<HostActions>()

if (!actions.openUrl("https://example.com/help")) showLink("https://example.com/help")

// Reload exists only where it has a meaning. Ask first.
actions.pageReload?.let { reload -> button("Reload") { reload.reload() } }
```

| Platform | `openUrl` | `pageReload` |
| --- | --- | --- |
| Web | `window.open` in a new tab with `noopener,noreferrer`. Returns true once called; a popup blocker may still refuse a tab opened outside a user gesture, so call it from an input handler. | Present: reloads the page. |
| Desktop | AWT `Desktop.browse`. Returns false, never throws, with no display (headless, CI), no browse support, or on failure. | `null` |
| Android | `Intent.ACTION_VIEW` with the browsable category. Returns false when no app handles it. | `null` |
| iOS | `UIApplication.open` on the main queue. Returns true once requested; iOS reports the result later. | `null` |

`pageReload` is a capability check, not a method that does nothing elsewhere: it is `null` off the
web. What reload would mean on desktop or mobile (restart the process, re-run bootstrap) is not
decided, so it is not offered there.

`openUrl` only opens absolute `http`, `https` and `mailto` URLs. `file:`, `javascript:`, `intent:`,
custom app schemes and anything with whitespace or control characters are refused with `false`, so
a URL built from a launch parameter or a network message cannot reach another app's handler or the
local disk. That does not make a link from a launch parameter safe to follow: it can still send the
player to a site its author chose. Show where it leads, or check the host against a list.

!!! note "macOS"
    On macOS, GLFW needs the JVM's first thread (`-XstartOnFirstThread`) and AWT wants it too.
    Test `openUrl` in your macOS build before relying on it.

## See also

- [App lifecycle](app-lifecycle.md) for `app { }` and `service(type, value)`.
- [Your first window](../get-started/first-window.md) and [Run in the browser](../get-started/run-in-the-browser.md)
  for the platform entry points.
