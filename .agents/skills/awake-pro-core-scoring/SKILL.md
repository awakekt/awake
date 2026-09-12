---
name: awake-pro-core-scoring
description: >
  Evaluates and scores proposed Awake Engine features, editor panels, runtime modules, and asset tools
  to decide whether they belong in Awake Core (Free & Open-Source) or Awake Pro (Commercial Studio Tier).
  Produces structured insights, industry runtime architecture benchmarks, and a scoring matrix report.
license: Apache-2.0
metadata:
  author: awake
  last-updated: '2026-10-15'
  keywords: Awake, Awake Pro, Awake Core, monetization, feature scoring, freemium matrix, commercial tier
---

# Awake Core vs. Awake Pro Feature Scoring Skill

Use this skill whenever evaluating a new engine feature, editor panel, asset pipeline tool, or runtime subsystem to determine its tier placement (**Awake Core / Free** vs. **Awake Pro / Commercial**).

---

## The Golden Principle

> **"Runtime Engine Libraries are Free; Studio Productivity & Enterprise Workflow is Pro."**

- **Awake Core (Free & Open-Source)**:
  Every runtime library required to compile, run, and ship a complete game on Desktop, iOS, Android, and WASM (`:awake:scene`, `:awake:ui:shadcn`, `:awake:physics`, `:awake:navigation`, `:awake:ai`, `:awake:audio`, `:awake:scene:dialogue`).

- **Awake Pro (Commercial & Studio Tier)**:
  Visual node graph editors in Studio, asset optimization pipelines, real-time team collaboration, cloud build systems, and enterprise SLAs.

---

## Why This Model Is a Strategic Win for Awake Engine

1. **Unstoppable Ecosystem & Developer Growth (Free Core)**:
   - **Zero Barrier to Adoption**: Solo developers, students, and open-source contributors can clone, compile, and ship complete games without hitting paywalls or trial limits.
   - **Establishes KMP Engine Leadership**: Positions Awake as the primary open-source Kotlin Multiplatform 3D game engine across Desktop, Android, iOS, and WASM.
   - **Community Word-of-Mouth**: Shipped games built on Awake Core serve as live showcases, driving engine stars, contributors, and industry credibility.

2. **High-Margin Commercial Monetization (Paid Pro)**:
   - **Studios Pay for Time Savings, Not Runtime Permitting**: Commercial studios and professional teams gladly pay for visual authoring tools that eliminate hundreds of engineering hours (e.g. Visual Dialogue Node Editors, Shader Graphs, Real-Time Collaborative Co-Editing, KTX2 Texture Auto-Compressors).
   - **Fair & Transparent Value Exchange**: Studios never feel "hostage" to runtime per-install or revenue-share royalties (unlike Unity's runtime fee backlash); they pay for tangible studio productivity.

3. **Predictable Governance for AI Agents & Developers**:
   - Gives AI agents and human contributors an objective, 4-metric scoring formula to evaluate feature placement without endless debates or accidental paywalling of runtime APIs.

---

## 4-Metric Feature Scoring Algorithm

Evaluate each proposed capability across 4 dimensions (1 to 5 points each):

| Metric | Description | Score 1 | Score 5 |
|---|---|---|---|
| **1. Runtime Criticality ($R$)** | Is this required for a game binary to compile and execute? | Optional editor tool | Essential runtime engine library |
| **2. Productivity Boost ($P$)** | Does this save studios 100s of hours of visual authoring? | Raw code DSL | Visual Node Graph Editor / Auto-Generator |
| **3. Team & Enterprise Scale ($E$)** | Is this used by commercial teams and studios? | Solo developer local workflow | Multi-user team, Cloud Build, Enterprise SLA |
| **4. Infrastructure Cost ($I$)** | Does this require cloud servers or proprietary SDKs? | Pure local Kotlin KMP code | Remote cloud rendering / streaming server |

### Tier Decision Formula

$$\text{Core Index} = (2 \times R) - (P + E)$$

- **Core Index $\ge 3$**: $\rightarrow$ **Awake Core (Free & Open-Source)**
- **Core Index $< 3$**: $\rightarrow$ **Awake Pro (Commercial Tier)**

---

## Standardized Scoring Report Template

When asked to score a feature, generate the following structured report:

### 1. Executive Summary & Verdict
- **Feature Name**: `[Feature Name]`
- **Target Module**: `[e.g. :awake:scene:dialogue or :awake:editor:dialogue]`
- **Final Verdict**: **`Awake Core (Free)`** OR **`Awake Pro (Commercial)`**

### 2. Feature Scorecard Matrix

| Metric | Score (1-5) | Rationale |
|---|---|---|
| Runtime Criticality ($R$) | `/5` | `[Explanation]` |
| Productivity Boost ($P$) | `/5` | `[Explanation]` |
| Team & Enterprise Scale ($E$) | `/5` | `[Explanation]` |
| Infrastructure Cost ($I$) | `/5` | `[Explanation]` |
| **Calculated Core Index** | **`[Value]`** | **Tier Decision** |

### 3. Industry Architecture Benchmark Comparison
- **Open-source ecosystem baseline**: `[How standalone open-source engines handle this feature]`
- **Commercial tier baseline**: `[How commercial studio tiers package this feature]`

### 4. Implementation Split Strategy
- **Free Component**: `[What goes into open-source Kotlin library]`
- **Pro Component**: `[What goes into Awake Pro Studio / Commercial Tooling]`
