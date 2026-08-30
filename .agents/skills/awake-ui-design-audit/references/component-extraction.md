# Component Extraction and Ownership

Reuse is a way to preserve consistency and make coordinated change safe. It is not a reason to
turn every repeated rectangle into a global component.

## Decide at the smallest useful level

1. **Existing shared recipe.** If the element is an ordinary control or an already-supported
   generic recipe, consume it as-is with its documented variants. Do not clone it locally just to
   make a screen port convenient.
2. **Product-local recipe.** Extract a named product component when two or more occurrences have
   the same anatomy, behaviour/state, or values that must change together. Product brand,
   campaign-specific hierarchy, and product-only composition stay beside that product's theme and
   screens (for example, `AcmeFeatureCard`), not in the engine-wide Shadcn layer.
3. **Deliberate one-off composition.** Keep a unique content arrangement inline when it is truly
   singular and is built from existing layout primitives and controls. Name the decision in the
   audit when it resembles a component but lacks a second use or stable contract.

Two substantially equivalent occurrences are a signal to assess extraction. Extract on that
second use when it would prevent duplicated state handling or coordinated visual edits. Do not
extract a coincidental pair with different intent, changing anatomy, or no stable public input.

## Promote only with evidence

Promote a product-local recipe to the shared design system only when all of these are true:

- its behaviour and anatomy are generic rather than brand-specific;
- it has a stable, typed API that is useful independently of the originating page;
- there is evidence of cross-product need or an established shared design-system pattern; and
- its semantics, states, keyboard/focus behaviour, and theme roles have an identified owner.

If generic behaviour is missing underneath the recipe, record a Foundation/behaviour-layer gap.
Do not bury it in a branded product component and do not add backend- or browser-specific
workarounds to a screen.

## Websites without a design system

Many references were assembled page by page and have no reusable system. Preserve the requested
visual result while creating the minimum viable product layer: semantic colour and type roles, a
spacing/surface scale, and only the product recipes demonstrated by repetition. This is a warning
and a debt record, not an automatic stop condition.

- A narrow, static, one-page copy may retain deliberate one-offs after recording them.
- A multi-screen port, or one already showing drift among repeated controls, needs product tokens
  and local recipes before further page expansion.
- Never force Shadcn visual identity onto a branded reference. Use shared recipes for compatible
  ordinary controls; use a product recipe when the reference's identity genuinely diverges.
