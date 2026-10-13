- **A `World` holds more than 64 component types.** Registering a 65th type no longer throws
  `IllegalArgumentException`: each entity's component signature grows by one `Long` per further 64
  types, and a world that stays within 64 keeps its one-`Long` signature.
