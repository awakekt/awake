# Release builds and optimization

<p class="awake-lede">AwakeKt publishes its libraries as plain, unshrunk artifacts. Shrinking and obfuscation happen when your app's release is built, and AwakeKt ships the keep rules that build needs.</p>

## Where optimization happens

AwakeKt does not minify, shrink or obfuscate the libraries it publishes. Class names, method names
and Kotlin metadata in a published jar, AAR or klib are the ones in the source, so stack traces,
IDE navigation and debugging against a dependency work as usual. Each artifact also has a sources
jar, which IDEs read for KDoc, and a placeholder documentation jar that Maven Central requires.
The API reference is built by `./gradlew developerDocs` and published on this site, not as a
javadoc jar.

Your app's release build does the optimizing, on the whole program: your code and every library it
uses together. That is what lets it remove classes nothing reaches and rename the rest.

| Artifact | Published as | Optimized when AwakeKt publishes it? | What your release build does | Keep rules AwakeKt ships |
| --- | --- | --- | --- | --- |
| Desktop JVM (`*-desktop`) | A jar with classes, a sources jar and an empty documentation jar. The Vulkan bindings (`vulkan-kmp`) and shader compiler jars also hold their native libraries under `natives/<platform>/`. | No | ProGuard through Compose Desktop's `buildTypes.release.proguard`, or any other jar shrinker you use. | A module whose native code finds classes by name holds its rules in the jar at `META-INF/proguard/`, named for the module, such as `awake-engine-window.pro`. |
| Android (`*-android`) | A release AAR. The shader compiler's AAR holds `jni/<abi>/libawake_naga.so`, and `vulkan-kmp-android-native` holds the Vulkan bindings' Android library. | No | R8, when the app sets `isMinifyEnabled = true`, plus resource shrinking when it sets `isShrinkResources = true`. | The same rules, as the AAR's `proguard.txt`. R8 applies them with no setup. |
| iOS (`iosArm64`, `iosSimulatorArm64`) | A Kotlin/Native klib. | No | The Kotlin/Native compiler links the klibs into your app's framework. | None. Keep rules are for JNI, which iOS does not have. |
| Web (`wasmJs`) | A Kotlin/Wasm klib. | No | The production executable (`wasmJsBrowserDistribution`) is built by the Kotlin Gradle plugin, which optimizes it with Binaryen. AwakeKt sets no optimization options of its own. | None. There is no JNI in the browser. |
| Platform BOM (`com.awakekt.awake:bom`) | A POM and Gradle module metadata. | Not applicable | Nothing. It only constrains versions. | None. |

The Kotlin Multiplatform root publication (`com.awakekt.awake.<group>:<module>`) holds metadata that
points Gradle at the platform artifacts above; a Gradle build picks the one for each target.

If published size matters to you, the sources jar and the placeholder documentation jar are not part
of what an app ships, and your release build removes what it does not reach. AwakeKt has not
measured per-platform changes to what it publishes; a change to the published artifacts would need
that measurement and a compatibility check first.

## Keep rules

Native code finds some classes by name: JNI binds each `Java_…` function to a class and method
name, and calls such as `FindClass` name a class directly. A release build that renames such a
class fails at launch, or the first time that path runs. These modules keep what their native code
names:

| Module | Rules file | What it keeps |
| --- | --- | --- |
| `com.awakekt.awake.engine:window` | `awake/engine/window/consumer-rules.pro` | The `native` methods of `GlfwWindow` and `GlfwGamepad`. |
| `com.awakekt.awake:vulkan-kmp` | `awake/backend/vulkan/bindings/consumer-rules.pro` | The bindings' types, which the native library builds and reads by name. |
| `com.awakekt.awake.asset:shader-compiler` | `awake/asset/shader-compiler/consumer-rules.pro` | The Naga bindings' `native` methods and `NagaException`. |
| `com.awakekt.awake.backend:jolt` | `awake/backend/jolt/consumer-rules.pro` | The backend's classes and all of jolt-jni's, whose native library calls back into them by name. |

You add nothing for these. Add a rule only for a class that your own code, or a library AwakeKt does
not ship, finds by name, in `proguard-rules.pro` of your Android app and of your desktop app.

Compose Desktop's ProGuard does not read `META-INF/proguard` from your dependencies, as R8 does.
The starter templates collect the rules from your runtime classpath and pass them on; copy the
`libraryKeepRules` task from `templates/project/app/desktopApp/build.gradle.kts` into a desktop app
that shrinks with Compose Desktop.

## What checks this

| Check | What it proves | When it runs |
| --- | --- | --- |
| `./gradlew verifyKeepRules`, part of `awakeVerify` | Every class that an AwakeKt module's native sources name is kept by that module's `consumer-rules.pro`. It cannot see what a third-party native library reaches. | Every pull request |
| `./gradlew verifyPublishedArtifacts` | A published `vulkan-kmp` desktop jar holds its native libraries, and the shader compiler's Android AAR holds `libawake_naga.so` for each ABI. It does not check for optimization. | Release publication |
| [Obfuscated templates](https://github.com/awakekt/awake/blob/main/.github/workflows/obfuscated-templates.yml) workflow, `tools/check_obfuscated_template.sh` | Both starter templates' release builds, shrunk and obfuscated as above, run against the Core published from the same commit. The desktop release is checked to be obfuscated, then plays frames in a window and renders a frame with no window. The Android release launches on an emulator. The web build is produced. A control run without the window's keep rules must fail at its native call, so a missing rule fails the check. | Nightly, and on a pull request that touches the templates or the check |

The Android release of the showcase sample (`samples/engine-showcase/androidApp`) also sets
`isMinifyEnabled = true` with no keep rules of its own, and the nightly device tests launch it.

## Configure a release build

The starter project template is the working example of all three:

| Target | Where to look |
| --- | --- |
| Android | `release` in `templates/project/app/androidApp/build.gradle.kts`: `isMinifyEnabled = true`, `isShrinkResources = true`, and `proguard-android-optimize.txt` plus your `proguard-rules.pro`. |
| Desktop | `buildTypes.release.proguard` in `templates/project/app/desktopApp/build.gradle.kts`: `obfuscate`, ProGuard `7.10.0`, your `proguard-rules.pro` and the collected library rules. |
| Web | `wasmJsBrowserDistribution` in `templates/project/app/webApp`, with no optimization settings of its own. |

Build them with the commands in the template's "Ship it" section:

```bash
./gradlew :app:desktopApp:packageReleaseDistributionForCurrentOS   # desktop installer
./gradlew :app:androidApp:assembleRelease                          # Android APK
./gradlew :app:webApp:wasmJsBrowserDistribution                    # web
```

!!! tip "Test the shrunk build, not just the debug build"
    A missing keep rule shows up only in the release. Run it, as the
    [Obfuscated templates](#what-checks-this) check does. [Vulkan backend](vulkan.md#check-a-build-in-ci)
    lists the two system properties that play a fixed number of frames, or render one frame with no
    window, for a CI job.

## See also

- [Installation](../get-started/installation.md#android-release-builds)
- [Maven coordinates and artifacts](https://github.com/awakekt/awake/blob/main/docs/reference/maven-coordinates.md)
- [Vulkan backend](vulkan.md#check-a-build-in-ci)
- [Releases and compatibility](../reference/releases.md)
