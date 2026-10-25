# `awake:project:cli`

`awake`, the command line for an Awake project's files. It validates a project, reads and edits its
scenes with no Studio and no GPU, through the same scene codec and validation a played project uses,
and renders a scene headless to a PNG. A script, a CI job or an agent can change a scene, know it
still loads, and see it. It needs Java 25, which the WebGPU backend's libraries are built for.

```bash
./gradlew :awake:project:cli:installDist
awake/project/cli/build/install/awake/bin/awake validate path/to/project
```

`./gradlew :awake:project:cli:run --args="validate path/to/project"` runs it without installing.

| Command | What it does |
|---|---|
| `awake validate [project]` | Checks the manifest, the engine version it needs, the entry scene, the asset lock's pins (each asset's size and SHA-256), and every scene: decode errors, validation errors, components nothing installed provides (a warning) and tags the manifest's `tags` list leaves out (a warning) |
| `awake validate [project] --code <classpath>` | Also opens the project with its own built code, as an editor does: see [Validating with the project's code](#validating-with-the-projects-code) |
| `awake scene list` | The project's scenes, with their names and node counts |
| `awake scene show <scene>` | A scene's nodes and components |
| `awake scene set <scene> <node> <c.field=value>` | Sets a component's field, or `transform.…` or `name` on the node |
| `awake scene add-node <scene> <name> [--parent <node>]` | Adds a node, last under its parent or at the top level |
| `awake scene remove-node <scene> <node>` | Removes a node and its children |
| `awake scene add-component <scene> <node> <type> [fields-json]` | Adds a component, with the fields given set on it |
| `awake scene remove-component <scene> <node> <type>` | Removes the node's component of that type |
| `awake render <scene> [--output <png>]` | Plays the scene headless and saves what its primary camera sees |
| `awake bundle <index> --output <dir>` | Packs the files a loose project index lists into content bundles under `<dir>/bundles`, named by SHA-256, and writes the bundled index to `<dir>/project.index.json`: see [Content bundles](../../../website/docs/guides/content-bundles.md) |
| `awake mcp` | Serves these commands to an AI agent over the Model Context Protocol, on stdin and stdout |

`<scene>` is a path from the project root, or a name under `scenes/`. `<node>` is a path of node
names, such as `Player/Camera`, with `#2` for an unnamed node's index. Without `--project`, the project
is the nearest folder up holding `awake.project.json`. Every command takes `--json` for output a
program reads, and an edit takes `--dry-run` to report what it would change without writing it.

An edit is checked before anything is written. The edited scene decodes back through the codec, which
checks every value's type; a field the component doesn't have is refused rather than dropped; and an
edit that adds a validation error is refused. The edit is then made to the file as written, so a
hand-written scene keeps its layout, its old component names and the fields it leaves at their
defaults, and a one-line scene, as Studio saves it, stays on one line. Where the file spells a field
another way than the codec does, such as a colour's `x` for `r`, the object holding it is written as
the codec spells it, so nothing is left that reads differently from what was set.

## Rendering

`awake render` loads the scene as a played project loads its entry scene, with Core's capabilities
and `ProjectRenderPlan`, plays `--frames` frames (1 by default) and saves the primary camera's view as
a PNG (`<scene>.png` by default), `--width` by `--height` pixels (1280 by 720 by default). The
picture is the 3D scene; a scene canvas drawn over it isn't included yet.

`--view` shows one of the renderer's debug views instead of the lit frame: `clay` (every surface one
neutral grey, lit), `normals`, `depth`, `albedo`, `shadows`, `joint-weights`, or `wireframe` over the
lit frame. `--camera <name>` takes the picture from the camera on that node instead of the scene's
primary camera.

| Backend | Where it runs | Needs |
|---|---|---|
| `vulkan` (default on Linux and macOS) | No window or display | A Vulkan driver, or Mesa's lavapipe without a GPU, and the `awake-vulkan` library in the bindings jar, which Awake builds for Linux and macOS |
| `webgpu` (default on Windows) | A hidden window | A display (`xvfb-run` on a headless Linux), and `JAVA_OPTS=-XstartOnFirstThread` on macOS |

`./gradlew :awake:project:cli:renderTest` renders a project on both backends and checks the frame;
`-Pawake.render.backends=webgpu` runs one, and `-Pawake.prebuiltNatives` skips building the Vulkan
bindings where they can't be built.

## Serving an AI agent

`awake mcp` serves the commands above to any agent that speaks the
[Model Context Protocol](https://modelcontextprotocol.io) over stdio: Claude Code, Codex CLI, Gemini
CLI, Cursor and others. The agent starts it and works on the project's files with no Studio running,
so it suits a cloud agent or a CI job as well as a desktop. Its tools are `validate`, `list_scenes`,
`show_scene`, `set_field`, `add_node`, `remove_node`, `add_component`, `remove_component` and `render`,
which returns the picture as an image the agent sees. Edits take `dry_run`, and go through the same
checks and minimal patch as the commands. Where the tools overlap with Studio's MCP server, they take
the same names and the same `component.field` form.

```bash
claude mcp add awake -- /path/to/awake/bin/awake mcp --project /path/to/project
```

Codex CLI, in `~/.codex/config.toml`:

```toml
[mcp_servers.awake]
command = "/path/to/awake/bin/awake"
args = ["mcp", "--project", "/path/to/project"]
```

Other agents take the same command in their `mcpServers` settings. While it serves, anything a
library prints goes to stderr, so stdout carries only MCP's messages.

`awake` exits 0 when a command succeeds, 1 when it finds errors or refuses an edit, and 2 when the
command line is wrong.

A game's own components are kept as data: `awake` links Core's scene components, not the game's code,
so it can't check their fields. `validate` names them as warnings, unless it is given the game's code.

## Validating with the project's code

`awake validate --code <classpath>` is the CI step for "this project still opens in the editor", for a
project with capabilities of its own. `<classpath>` holds the project's built code, class folders and
jars split by the platform's path separator (`:`, or `;` on Windows); entries a build never wrote are
skipped. With it, `validate` also:

- loads each `plugins[].capabilityClass` from that code, in a class loader of its own whose parent holds
  Core, and checks it is a `SceneCapability` (a Kotlin `object`, or a class with a public constructor
  that takes nothing) with the plugin's `id`;
- opens every scene with those capabilities through `checkProjectOpens` in `awake:project:runtime`:
  decoded with the project's own registry, its prefabs expanded, every component known (one no
  capability registers is an error at its node), instantiated, loaded with its content and a physics
  world by `LoadedProject.switchTo`, and its systems run for one fixed step and one frame;
- saves each scene and prefab, reopens it and compares, and saves it again, which must write the same
  text;
- moves back to the entry scene and closes the project: every physics world it made must be destroyed,
  and no capability's component may have reached the process-wide scene serializers;
- warns when the project's code stays loaded once the check has closed it, which means something outside
  the project holds one of its classes, as a process-wide registry or a thread can.

```bash
./gradlew :game:jar
awake validate path/to/project --code game/build/libs/game.jar
```

Each problem is reported with its file, its node path and a code (`capability_class`, `project_load`,
`unknown_component`, `scene_load`, `scene_systems`, `round_trip`, `global_serializer`, `leak`), and
any error exits 1. Without `--code`, `validate` runs none of the project's code.
