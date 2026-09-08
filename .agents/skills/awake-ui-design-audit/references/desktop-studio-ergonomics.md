# Desktop Studio Ergonomics & Anti-Slop Guide

This reference provides specific criteria for Awake Studio desktop tools, docks, panels, and inspector plugins. While Awake's design system uses `awake:ui:shadcn` to guarantee theme and token consistency, desktop IDEs require distinct information density and ergonomics that differ from consumer web pages.

---

## 1. Anti-Slop Rules for Studio Panels

### A. Eliminate Card-Spam (Flatten Nested Chrome)
- **Problem**: Stacking multiple `ShadcnCard`s inside a dock panel or dialog that already has padding, borders, and a background. This creates visual clutter, redundant border lines, and excessive nested padding.
- **Rule**: In dock panels (Bottom Panel, Inspector, Outliner), use subtle section dividers (`ShadcnSeparator()`) or compact flat rows instead of nesting cards inside cards. Reserve `ShadcnCard` for distinct standalone items (e.g. extension cards in a grid).

### B. Eliminate Telemetry Jitter (Monospace Dynamic Numbers)
- **Problem**: Dynamic numbers (FPS, frame timings in ms, memory in MB, entity counts, transform XYZ) rendered in proportional fonts change width as digits tick every frame (e.g., `1` is narrower than `8`), causing entire layouts to jump horizontally.
- **Rule**: All real-time telemetry, coordinates, and timings must use monospace formatting or fixed-width column cells (`Tw.Font.mono`).

### C. Eliminate Raw Ad-Hoc Colors
- **Problem**: Writing inline `Color(0.2f, 0.6f, 0.9f)` for charts, badges, or tags breaks dark/light theme switching and design coherence.
- **Rule**: All UI surfaces, borders, and text must use `shadcnTheme.palette` semantic tokens. For specialized telemetry/waterfall visualizations, use a tokenized telemetry color object that adapts gracefully to dark/light modes.

### D. Desktop Studio Vertical Rhythm
- **Problem**: Web-style large paddings (24dp+) waste limited screen space and push critical 3D viewport canvas or editor tools off-screen.
- **Rule**:
  - Buttons and inputs in tools and docks should use compact heights (28dp–32dp, `ShadcnButtonSizeVariant.Sm`).
  - Gaps between compact items should be 4dp–8dp (`Tw.Spacing.s1` or `s2`).
  - Keep headers, toolbars, and status bars tight (36dp–48dp).

---

## 2. Hard Pre-Flight Checklist for Studio UI Panels

Before committing any editor tool, dock tab, or plugin inspector:
- [ ] **No Card-Spam**: No cards nested inside containers that already have the same fill/border.
- [ ] **Zero Hardcoded Colors**: No `Color(r, g, b)` literals outside of shader/material editing.
- [ ] **Monospace Telemetry**: Dynamic numerical readouts do not jitter on frame ticks.
- [ ] **Compact Control Heights**: Standardized to 28dp–32dp for dock/inspector tools.
- [ ] **Dual-Theme Legibility**: Text and chart bars maintain high contrast in both dark and light modes.
