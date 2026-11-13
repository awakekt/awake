# Awake Compose Game (`:awake:compose:game`)

Game UI built purely on Compose core (`compose:runtime`, `compose:ui`, `compose:foundation`).

## Admission rule

A piece goes in only if all three hold:

1. It is game UI built purely on Compose core.
2. It depends on no `awake:scene` module.
3. At least two different games would use it unchanged.

## Contents

- `TouchStick`, `TouchStickState`: a fixed, floating or dynamic thumb stick.
- `TouchAim`, `TouchAimState`: drag to aim.
- `TouchLook`: the base and knob fills (`ImageFill` or a colour).

The dead zone, response and aim maths is `TouchStickMath` in `awake:core:input`, shared with the canvas controls.

To move a player or fire an action with them, attach `TouchStickInput` / `TouchAimInput` (`awake:project:runtime`) to an entity with the same state; they feed the actions a canvas joystick or aim button feeds. This module depends on no scene module, so the binding lives there.
