# Audio

<p class="awake-lede">Sound in the scene: sources that play clips, quieter and panned by distance from a listener, plus clips decoded from WAV files or synthesised in code.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:scene:audio</code></span>
<span class="awake-badge">module: <code>awake:core:audio</code></span>
<span class="awake-badge">Playback: Desktop</span>
</div>

`awake:core:audio` has clips, WAV decoding, a sound synthesiser and the `AudioPlayer` contract.
`awake:scene:audio` has the ECS side: `AudioSource`, `AudioListener` and `AudioSystem`. The scene
DSL functions live in `awake:scene:authoring`.

## Make a clip

An `AudioClip` is uncompressed PCM in memory. Decode one from WAV bytes, however you load them:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/AudioDocsSampleTest.kt:wav"
```

!!! warning "Check `bitsPerSample` on decoded clips"
    `WavDecoder` currently reads `bitsPerSample` two bytes past where the WAV format stores it. With
    a 16-bit file whose format chunk is 16 bytes, the channels, sample rate and PCM bytes come out
    right but `bitsPerSample`, and so `durationSeconds`, do not. Until that is fixed, copy the
    clip with the value you know: `clip.copy(bitsPerSample = 16)`.

Or synthesise one:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/AudioDocsSampleTest.kt:clip"
```

`SoundSynthesizer.build` also takes `waveform` (`SINE`, `SQUARE`, `TRIANGLE`, `SAWTOOTH`, `NOISE`),
an ADSR `envelope`, `lowPassFilter` and `volume`.

## Add a sound source

A looping sound three metres to the side of a camera that hears it.

=== "Scene document"

    The scene runtime has no scene document component for audio yet. AwakeKt Studio saves an
    `audio_source` component, but only Studio registers it. Use the scene DSL.

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/AudioDocsSampleTest.kt:scene-dsl"
    ```

    `sound(clip, name, volume, maxDistance)` spawns a whole emitter entity in one call.

=== "Studio"

    1. In the **Hierarchy**, click **Add entity** and choose **Audio: Audio Source**.
    2. In the **Inspector**, pick the **Audio Asset**, then set **Volume**, **Spatial 3D**,
       **Max Distance**, **Auto Play** and **Loop**.

    Studio's audio source starts with **Auto Play** off.

## Play it

`AudioSystem` starts, positions and stops sources through an `AudioPlayer`:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/AudioDocsSampleTest.kt:system"
```

Register it as a per-frame system in an app, for example `frameSystem("audio") { AudioSystem(player) }`.
The player also plays music outside the ECS: `playMusic(clip)` and `stopMusic()`, with
`masterVolume`, `sfxVolume` and `musicVolume` to mix them.

## Properties

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `clip` | `AudioClip` | required | The sound to play. |
| `volume` | number | `1` | Volume, from 0 to 1. |
| `isSpatial3D` | boolean | `true` | Fade with distance and pan by direction from the listener. Off plays at `volume`, centred. |
| `maxDistance` | number | `50` | Beyond this distance a spatial sound is silent. |
| `autoPlay` | boolean | `true` | Start on the first update. |
| `loop` | boolean | `false` | Repeat until stopped. |

At run time `AudioSource.isPlaying` says whether it is playing, and `stop()` stops it.

## How it works

Each update, `AudioSystem` finds the first entity with an `AudioListener` and a `Transform`, or uses
the origin if there is none. A spatial source's volume falls off with its distance from the listener
and reaches zero at `maxDistance`; its pan follows the listener's right-hand direction. A source whose
sound has finished drops its handle, and `isPlaying` turns false.

| Platform | `AudioPlayer` |
| --- | --- |
| Desktop | `JvmAudioPlayer`, through Java Sound. |
| Android | `AndroidAudioPlayer`, through `AudioTrack`. |
| Web | `WebAudioPlayer`, through Web Audio API. |
| iOS | Supply your own `AudioPlayer`, or use `NoOpAudioPlayer`. |

!!! warning "A spatial source out of range never auto-plays"
    `autoPlay` gets one attempt, on the first update. If the listener is beyond `maxDistance`
    then, the player returns no sound and the source does not try again. Set `hasAutoPlayed` back to
    `false` to retry, or place the source within range.

## See also

- [Physics](physics.md) contact events, for impact sounds.
- [Large worlds](large-worlds.md), for streaming emitters with their cells.
