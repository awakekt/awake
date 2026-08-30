# Layout Archetypes

Choose the smallest structure that matches the requested flow. These are product-level skeletons:
apply the extracted product theme and recipes rather than copying their visual treatment.

## Marketing landing page

`top navigation → hero → proof/benefits → feature sections → call to action → footer`

Highlight one action in the hero. On compact layouts, stack hero media after the primary copy and
action. Keep navigation actions reachable through a compact trigger rather than merely hiding them.

## App shell and dashboard

`navigation rail or header → page title/actions → summary metrics → primary work area → supporting panels`

Use a clear primary work area; cards and tables support it rather than competing with it. Compact
mode normally moves navigation behind a trigger and stacks panels in task priority order.

## Authentication

`brand/context → one focused form → submit action → alternate account path → recovery/help`

Use this archetype for sign-in, registration, password recovery, verification, and invitation
acceptance. Keep field errors adjacent to their fields and preserve entered values across error or
loading states. Registration is a variation, not a separate structural system.

## List, search, and detail

`filters/search → result list or collection → selected detail → contextual actions`

On wide layouts, a list/detail split may work; compact mode generally navigates from list to a
dedicated detail state. Design empty, loading, error, and no-results states with the same care as
the populated list.

## Settings and account

`section navigation → grouped settings → save/status feedback → destructive/account actions`

Group by user intent, not by control type. Compact mode may turn side navigation into a section
picker; destructive actions stay visually and semantically distinct.

## Onboarding and wizard

`purpose/progress → one task step → back/next or finish → optional skip/help`

One visible decision per step is the default. Preserve progress and validation state when moving
between steps. Do not use a visual stepper as the only indication of current position.

## Content, article, and documentation

`content shell → title/metadata → readable body → in-page navigation or related content`

Constrain line length and keep headings, code/examples, and media readable before adding a
secondary rail. Compact mode moves supporting navigation below or behind the primary reading flow.

## Shared states, not separate page archetypes

Every applicable archetype needs deliberate loading, empty, error, offline/permission, and
long-content states. Treat those as variants of the same task flow so they retain navigation,
actions, and accessibility rather than becoming disconnected screens.

## Use only when relevant

Commerce checkout, calendars, kanban boards, media galleries, maps, and game HUDs are
domain-specific. Add an archetype only after a repeated product need demonstrates that it is more
than a one-off layout.
