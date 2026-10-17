# Awake Core Input

Platform-neutral input for [Awake](../../../README.md): the keys, pointer buttons, touches and
controller buttons a host writes into `Input`, the `InputSnapshot` each frame reads, and named
actions over them. No dependency on the ECS, rendering or a window; a host maps its platform's
events to these types once.

A host that polls a controller sets every button at once with `setGamepadButtons` and lets go of
them with `releaseGamepadButtons`. On Android, `KeyEvent.syncAwakeGamepadInput` and
`MotionEvent.syncAwakeGamepadInput` read a controller's buttons, hat d-pad and triggers.

## Actions

`InputActions` turns a frame's input into named actions, so a system asks for `"jump"` and never for
a key:

- `ButtonAction` is on or off: held, pressed for a frame, or toggled. It is triggered by `keys`,
  pointer `buttons`, and `chords`.
- `AxisAction` is a unit direction from four sets of keys.
- `hold`, `press` and `push` let an on-screen control drive an action as a key would.

## Chords

A `KeyChord` is a key pressed while modifiers are held (Shift+1, Ctrl+Alt+K); a `GamepadChord` is a
controller button pressed while others, usually a shoulder or trigger, are held. Every binding on the
same key or button resolves by the same rules, across every action:

- A chord fires when its key goes down with its modifiers already held, or going down in the same
  frame. Pressing the key first and the modifier after does not make the chord.
- Of the bindings whose modifiers are held, only the most specific fire: Shift+1 does not also fire
  1, and Ctrl+S does not push an axis bound to S.
- The winner keeps the key until it is released.

`ExtraModifiers.Block`, a chord's default, stops it matching while a modifier it does not name is
held; `Allow` lets it match. A plain key in `keys`, and an axis key, allow extra modifiers. Left and
right modifiers are one `Key`, so a chord cannot tell them apart.

## Bindings as data

`InputActions.bindings()` returns an `InputBindings`, every action's `ActionBinding` by name, which
is `@Serializable`. `rebind` swaps one action's binding at run time and `rebindAll` loads a saved
set, skipping names the actions no longer have. `actionsBoundTo(chord)` finds what a new chord would
clash with. `ButtonBinding.describe()` lists each way to trigger an action as a
`BindingDescription`, its modifiers and its control. This module holds no label text:
`InputLabels` in `awake:core:localization` names a `BindingDescription`, a chord or an action's
bindings in the player's language and platform style.

The guide is [Input](../../../website/docs/guides/input.md).
