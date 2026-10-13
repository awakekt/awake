- **Blueprints drive game UI.** Three nodes join the ones `BlueprintSystem` registers, so a menu or
  a HUD reacts to its buttons without code: On Button Pressed (`event.button.pressed`) fires once for
  each press of the Button whose `Name` is its `name`, or of the blueprint's own Button, and gives the
  pressed `element`; Set Text (`ui.set-text`) and Set Visible (`ui.set-visible`) change a
  `canvas_element`'s `text` and `visible`, found by a wired `target`, by `name`, or as the
  blueprint's own. `CanvasElement.presses` counts a Button's taps and takes none, so a blueprint takes
  no tap from the Button's `action` or from a system calling `consumePress()`.
  `BlueprintInterpreter.fire` takes an `EventFilter`, which runs only the event nodes whose own inputs
  it accepts.
