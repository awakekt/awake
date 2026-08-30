/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.runtime

/*
 * There is no observable state type here, and that is deliberate.
 *
 * `mutableStateOf` and `recomposeScope` existed together: state was observable so that writing it
 * could mark a hand-placed restart boundary dirty and let the next pass skip everything else. That
 * pairing is gone. This engine composes the whole tree every frame by design -- see
 * `ComposeHost.frame` -- so nothing needs to be told that a value changed, and a scope that could
 * skip was only ever a trap: state read through a plain field silently never invalidated it, which
 * shipped three times (hover, focus, typing) before the pattern was recognised.
 *
 * State that must outlive a frame is held the way the rest of this engine already holds it: a
 * plain class with plain `var`s, kept across passes by [remember]. `ScrollState`, `TextFieldState`,
 * `LazyListState` and `InteractionSource` are all that shape.
 *
 *     val picker = remember { ScenePickerState() }   // class ScenePickerState { var expanded = false }
 *
 * The divergence from Compose is recorded in `docs/reference/compose-engine/15-compose-parity.md`.
 * Reintroducing observation means reintroducing skipping, which needs restart boundaries the
 * compiler generates rather than ones a caller remembers to place.
 */
