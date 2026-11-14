# Launch options

<p class="awake-lede">Declare each option a game reads at launch once, with its type, default, range and description, and read it the same way from a page URL, a command line or an Android intent. A mistyped name or a refused value is reported, never silently dropped.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake.core:config</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

[Launch parameters](launch-parameters.md) gives the raw keys and values a host was launched with.
A `LaunchOptions` schema says which of them the game reads and what each one means. The schema is
plain Kotlin with no reflection, no annotation processing and no dependency on a window or a GPU, so
an entry point can read the server address before the engine starts, and a headless server reads
the same options as a client.

## Declare the options

```kotlin title="commonMain"
enum class Quality { Low, Medium, High }

object Launch : LaunchOptions() {
    val server = string("server", "ws://localhost:7000", "Game server address", aliases = listOf("host"))
    val touch = boolean("touch", description = "Touch layout")
    val shadowDistance = float("shadow-distance", 60f, "Shadow distance in metres", range = 0f..500f)
    val quality = choice("quality", Quality.High, Quality.entries, "Render quality")
    val hideHud = list("hide-hud", description = "HUD pieces to hide, comma-separated")
    val debug = ids("debug", description = "Debug sources to show")
    val slot = int("slot", 0, "Character slot", range = 0..7, development = true)
    val signIn = string("sign-in", "", "Development sign-in", development = true, secret = true)

    init {
        ignorePrefix("utm_") // tracking parameters on a shared link
        ignore("fbclid")
    }
}
```

| Declaration | Reads | Refused |
| --- | --- | --- |
| `boolean` | `true` `1` `yes` `on`, or a bare `--touch` / `?touch`; `false` `0` `no` `off` | anything else |
| `int`, `float` | a decimal number | not a number, outside `range` |
| `string` | the text, trimmed | empty, or what `check` refuses (an allow-list) |
| `choice` | an entry's name, ignoring case, `-` and `_` | any other name |
| `list`, `ids` | comma-separated items; `ids` lower-cases them and keeps each once | empty |
| `option` | any `ValueType`, with your own `check` and `bounds` | what the type or `check` refuses |

A **name matches ignoring case, `-`, `_` and `.`**: `shadow-distance`, `shadowDistance`,
`SHADOW_DISTANCE` and `shadowdistance` are one option, with no alias to declare. `aliases` are for
names that are actually different, such as one an older build used. Declaring two options under one
name fails when the schema is first used.

## Read them

Parse once, in the platform entry point, from the parameters the host already read:

=== "Web"

    ```kotlin title="wasmJsMain"
    val launch = Launch.parse(browserLaunchParameters().toMap())
    ```

=== "Desktop"

    ```kotlin title="desktopMain"
    fun main(args: Array<String>) {
        if ("--help" in args) return println(Launch.describe())
        val launch = Launch.parse(desktopLaunchParameters(args).toMap(), args.asList(), development = isDevBuild)
    }
    ```

=== "Android"

    ```kotlin title="androidMain"
    val launch = Launch.parse(intent.launchParameters().toMap())
    ```

Then log the problems, and read values with `launch[option]`:

```kotlin title="commonMain"
launch.problems.forEach { log.warn(it.message) }

val server = launch[Launch.server]
val hidden = launch[Launch.hideHud]
if (launch.isGiven(Launch.touch)) useTouchLayout(launch[Launch.touch])
```

A value is resolved once, by `parse`. Reading it allocates nothing and does no lookup by name.

## Problems

`parse` never throws. Each thing it did not use as given is a `LaunchProblem` in `launch.problems`,
in the order it met them, and the app decides whether to log or show them:

| Problem | When | Effect |
| --- | --- | --- |
| `Unknown(name)` | no option has this name, and the schema does not `ignore` it | nothing |
| `Invalid(option, given, reason)` | the value is not of the type, is outside the range, or is missing | the option keeps its default |
| `DevelopmentOnly(option)` | a `development = true` option, in a build that passed `development = false` (the default) | the option keeps its default |
| `Stray(argument, hint)` | a desktop argument that is not an option | nothing |

A page URL often carries parameters that are not the game's, such as tracking tags or another
library's settings. `ignore(names)` and `ignorePrefix(prefixes)` keep them out of the report.

On desktop, pass the `main` arguments as the second parameter so that `--debug cells`, written with a
space, is caught. [Launch parameters](launch-parameters.md) reads that as a bare `--debug` and a
positional `cells`. Both are reported, each saying `use --debug=cells`. Leave the arguments out when
the game takes positional arguments of its own.

## Secrets

`secret = true` is for a token or a development sign-in. Its value never appears in a problem's
message, in `describe()` or in `LaunchValues.toString()`; a stray value after a secret option is
shown as `(secret)`. Launch parameters are untrusted input (see
[Launch parameters](launch-parameters.md#read-the-parameters)), so a `check` that allow-lists a
server is worth more than a range on a number.

## Help text

`Launch.describe()` writes one entry per option: its name and type, its range or choices, whether it
is for development builds, its description, its default and its aliases. Print it for `--help` on
desktop, or show it on a debug page. `Launch.options` lists the same declarations for a page that
lays them out itself.

```text
--shadow-distance=<float>  0.0..500.0
    Shadow distance in metres
    default: 60.0
--slot=<int>  0..7  (development)
    Character slot
    default: 0
```

## Reach them from a feature

Register the values as a host service, and a feature reads them once, when it registers:

```kotlin title="commonMain"
val host = FeatureHost.headless(gameplay).provide(launch)

class NetFeature : SceneFeature {
    override val id = "game.net"
    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        plan.frame("net") { host -> NetSystem(host.require<LaunchValues>()[Launch.server]) }
    }
}
```

## Moving from `DebugFlags`

`DebugFlags.fromPage` and `DebugFlags.fromArgs` in `awake:core:diagnostics` are deprecated. Declare
`ids("debug")` and pass `launch[Launch.debug]` to `DebugRegistry`, as
[Debug tools](debug-tools.md#turn-sources-on) shows. The deprecated `fromArgs` still reads
`--debug cells` written with a space. On this path, that spelling is reported with `use --debug=cells`.

## See also

- [Launch parameters](launch-parameters.md) for the raw parameters, the launch environment and host actions.
- [Debug tools](debug-tools.md) for the `debug` option's sources.
