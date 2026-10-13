- **A wired target that finds no one makes a blueprint node do nothing.** Destroy, Play Animation,
  Set Text and Set Visible fell back to the blueprint's own entity whenever their `target` held no
  entity, so a wired Find By Name with a mistyped name destroyed, animated or changed the blueprint's
  own entity or element instead. Only a `target` with nothing wired to it now means the blueprint's own
  entity; a wired one that gives no entity, such as a Find By Name that found no one or a sensor body
  no entity owns, makes the node do nothing, and it warns once rather than every time it runs. Set
  Text and Set Visible no longer fall back to their `name` then either. A node tells the two apart
  with `BlueprintContext.isWired`, and says why it did nothing with `BlueprintContext.warnOnce`.
