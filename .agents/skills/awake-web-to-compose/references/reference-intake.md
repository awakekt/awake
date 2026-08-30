# Reference Intake and Fidelity

Read this before porting a URL, screenshots, raw HTML/CSS, or Tailwind/Base UI source.

## Choose the requested fidelity target

Ask only when it is not clear from the request. Do not silently promise pixel parity.

| Target | Meaning | Expected validation |
|---|---|---|
| Pixel parity | Match supplied states and viewport captures as closely as the host allows. | Capture comparison at every supplied viewport/state. |
| Design adaptation | Preserve the site's hierarchy and visual language while using the product's existing theme and components. | Review hierarchy, tokens, and responsive intent; capture representative states. |
| Functional approximation | Recreate the requested flow or layout without claiming its exact visual system. | Focused behaviour/layout verification. |

## Evidence to collect

- A URL with route and state, or local source files; inspect supplied evidence before coding.
- Exact viewport width, height, density if known, theme, locale, and content state for each
  reference capture.
- Desktop and narrow/touch references when the result must be responsive.
- Open/closed, selected/unselected, valid/error, loading/empty, and hover/focus/pressed states
  that materially change the result.
- Fonts and assets the requester supplied or explicitly authorized for use.

Do not derive unobserved breakpoints, animations, interaction rules, or font metrics from one
static capture. State the assumption or leave that state out of the parity claim.

## Assets and source rights

A public URL is visual reference, not a license to copy its assets, source, branding, or fonts.
Reuse only assets the requester supplies or has authorized. Otherwise use project-owned assets,
licensed substitutes, or placeholders while preserving the layout's intended proportions.

Record the source and usage decision for logo, imagery, icon sets, and fonts. Never download an
asset merely because a page displays it.

## Interaction and platform review

For each interactive reference, identify its visible state contract independently of React or DOM
implementation: trigger, content, focus order, keyboard/touch operation, close/dismiss rules,
validation, loading, and reduced-motion behaviour.

Website conventions can be mouse-only or browser-specific. Decide how hover-only affordances,
right-click/context actions, sticky browser positioning, and keyboard shortcuts behave on target
hosts. A touch/keyboard alternative is part of the port's behaviour, not a CSS detail.

## Content and responsiveness

Separate the reference's layout from its sample copy. Check whether actual use needs dynamic
lists, localization, long text, user-provided values, empty states, error states, or image aspect
ratio constraints. Hardcoding a screenshot's content is acceptable only for an explicitly static
mock.

Capture the content shell's constraints and hierarchy before translating individual CSS values.
For each supported viewport, record whether the intended change is reflow, a different component
arrangement, reduced decoration, or merely scaling. Do not use arbitrary breakpoints only because
the source site is responsive.
