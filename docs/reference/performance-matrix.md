# Engine Performance Matrix & Scorecard

High-level dashboard of Awake's performance baselines, micro-benchmark suites, algorithmic complexity guarantees, and coverage gap roadmap.

---

## 1. Current Benchmark Matrix

### A. ECS & Scene Graph Runtime (`:awake:ecs:benchmark`)

Reran 2026-08-21 on Kotlin 2.4.10 and Temurin JDK 17.0.19 (1 fork, 5 warmup
iterations, 5 measurement iterations, 1s/iteration on Desktop JVM).
Detailed analysis and historic profiling notes live in [docs/ecs-benchmark-scorecard.md](../ecs-benchmark-scorecard.md).

| Benchmark Category | Scale / Depth | **Awake** (ops/sec) | Fleks 2.14 | Artemis-odb 2.3.0 | Ashley 1.7.3 | Leader |
|---|---:|---:|---:|---:|---:|---|
| **Entity Create/Destroy** | 10k | **8,634.117** | 7,755.325 | 2,566.932 | 155.432 | **Awake** |
| **Entity Create/Destroy** | 100k | **908.979** | 801.458 | 481.423 | 2.188 | **Awake** |
| **Component Add/Remove** | 10k | **2,633.746** | 3,026.158 | 2,527.302 | 392.222 | Fleks |
| **Component Add/Remove** | 100k | **188.597** | 196.045 | 121.580 | 30.677 | Fleks |
| **Family / Query Churn** | 10k | **2,649.934** | 2,854.911 | 821.615 | 167.416 | Fleks |
| **Family / Query Churn** | 100k | **110.505** | 161.719 | 241.011 | 2.989 | Artemis-odb |
| **Transform Hierarchy** | Depth 10 | **3,122,865.827** | 747,124.930 | 885,073.547 | 791,041.812 | **Awake** |
| **Transform Hierarchy** | Depth 50 | **734,005.393** | 146,549.743 | 179,985.282 | 151,345.590 | **Awake** |
| **Transform + Mesh Query** | 10k | **425,929.064** | 79,379.361 | 58,891.964 | 29,397.653 | **Awake** |
| **Transform + Mesh Query** | 100k | **44,982.856** | 6,890.007 | 2,719.384 | 298.193 | **Awake** |

Focused cached-type-ID mutation decision (3 forks, 5 warmups, 5 measurements):

| Awake workload | Scale | Before | After | Change |
|---|---:|---:|---:|---:|
| Component add/remove | 10k | 2,351.023 | **2,747.332** | +16.9% |
| Component add/remove | 100k | 180.436 | **220.363** | +22.1% |
| Maintained-family churn | 10k | 2,358.154 | **2,475.613** | +5.0% |
| Maintained-family churn | 100k | 105.174 | **111.574** | +6.1% |

At 100k, normalized family-churn allocation dropped about 90%. See the
[cached type-ID churn report](../tasks/archive/2026-08-21-ecs-cached-type-id-churn.md).

The follow-up no-family/`Family1`/`Family2` decomposition found CPU/index maintenance rather than
GC as the remaining cost. A fused `Family2` sparse-update candidate measured -1.9% with overlapping
confidence intervals and was reverted. The next gated lane is
[adaptive bulk structural mutation](../tasks/2026-08-21-ecs-adaptive-bulk-mutation-plan.md), which
aims to avoid repeated work across large batches instead of adding another micro-cache.

Focused Kotlin 2.4.10 tag-family decision run (3 forks, 5 warmups, 5 measurements,
1 second each, Temurin JDK 17.0.19):

| Benchmark | Scale | Before | After | Change |
|---|---:|---:|---:|---:|
| Tag-family iteration | 10k | 208,232.122 | **492,879.156** | +136.7% |
| Tag-family iteration | 100k | 20,206.434 | **48,879.048** | +141.9% |
| Tag-family churn | 10k | 3,158.275 | **3,209.308** | +1.6% |
| Tag-family churn | 100k | 284.483 | **304.939** | +7.2% |

See the [family tag-column report](../tasks/archive/2026-08-21-ecs-family-tag-columns.md) for
confidence errors, normal-component controls, GC results, and the Flecs/Fleks/EnTT/Bevy/Unity
architecture matrix.

Pure-archetype decision control (same Kotlin/JDK and 3x5x5 methodology):

| Workload | Scale | Current Awake | Hybrid control | Pure archetype | Leader |
|---|---:|---:|---:|---:|---|
| Stable render-family iteration | 100k | **50,421.543** | 26,936.261 | 30,989.607 | Awake |
| Dynamic-tag churn | 100k | 1,695.029 | **4,006.350** | 530.804 | Hybrid raw control |
| 256-signature required/excluded query | 100k | **440,046.129** | 27,879.333 | 205,277.897 | Awake |

The pure control performs real table migration; the hybrid raw control keeps dynamic tags
outside its stable table. Production remains sparse-set based. See the
[storage-shape decision](../tasks/archive/2026-08-18-ecs-hybrid-archetype-sparse-set.md).

---

### B. UI Layout & Frame Timing (`:awake:ui:benchmark`)

Ran via `./gradlew :awake:ui:benchmark:mainBenchmark`.
Measures retained Compose layout pass execution time across deep row/column trees without widget styling noise.

| Benchmark Target | Nesting Depth | Metric | Target SLA |
|---|---:|---|---|
| `LayoutNestingBenchmark` | Depth 2 | Average Frame Time | $< 25\,\mu\text{s}$ |
| `LayoutNestingBenchmark` | Depth 4 | Average Frame Time | $< 75\,\mu\text{s}$ |
| `LayoutNestingBenchmark` | Depth 6 | Average Frame Time | $< 200\,\mu\text{s}$ |
| `LayoutNestingBenchmark` | Depth 8 | Average Frame Time | $< 500\,\mu\text{s}$ |

---

## 2. Subsystem Coverage & Gap Analysis

| Subsystem | Module | Benchmark Harness | Status | Roadmap & Gap Description |
|---|---|---|---|---|
| **ECS Storage** | `:awake:ecs` | `:awake:ecs:benchmark` | 🟡 Active | Immediate storage covered; adaptive bulk-mutation crossover and same-semantics competitor controls planned. |
| **Scene Graph** | `:awake:scene` | `:awake:ecs:benchmark` | ✅ Covered | Hierarchy matrix evaluation and transform propagation. |
| **UI Layout** | `:awake:compose:foundation` | `:awake:ui:benchmark` | ✅ Covered | Deep nesting measure-policy curves. |
| **Geometry** | `:awake:core:geometry` | — | 🟡 Missing | Needs `MeshSimplifier` decimation throughput benchmark (50k–500k triangles). |
| **Animation** | `:awake:core:animation` | — | 🟡 Missing | Needs skeletal SLERP pose blending benchmark across 50–200 joint hierarchies. |
| **Asset I/O** | `:awake:asset:gltf` | — | 🟡 Missing | Needs `GltfParser` binary throughput benchmark ($\text{MB/s}$). |
| **Physics** | `:awake:backend:jolt` | — | 🟡 Missing | Needs `PhysicsWorld.step()` tick cost with 1,000 active dynamic bodies. |
| **Rendering** | `:awake:backend:vulkan` | — | 🟡 Missing | Needs command buffer recording and descriptor set binding throughput. |
| **Text Engine** | `:awake:core:text` | — | 🟡 Missing | Needs font glyph lookup and multiline text measurement throughput. |

---

## 3. Execution & Verification Strategy

Awake uses a 3-tier strategy to maintain high performance without slowing down daily development:

```
┌────────────────────────────────────────────────────────────────────────┐
│ Tier 1: Pre-Push Hook (Fast, <100ms, Deterministic)                   │
│ - Exact trial count tests (e.g. WeightTrialReuseTest, TrialMeasureTest)│
│ - Algorithmic O(N) complexity checks without clock noise               │
│ - Performance Matrix documentation verification in pre-push            │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 2: On-Demand Local Profiling (During Optimization Tasks)         │
│ - Run by awake-engine-core-engineer during ECS/math refactors          │
│ - ./gradlew :awake:ecs:benchmark:mainBenchmark                         │
│ - ./gradlew :awake:ui:benchmark:mainBenchmark                          │
├────────────────────────────────────────────────────────────────────────┤
│ Tier 3: Nightly & Release CI Gate                                      │
│ - Full benchmark suite executed on isolated CI runners                 │
│ - Automated regression alerts if throughput drops >5%                  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Engineer Responsibilities

- **When modifying hot paths** in `:awake:ecs`, `:awake:core:math`, or `:awake:compose:foundation`:
  1. Run the respective benchmark suite before and after your change.
  2. Record the new numbers and ensure no regression occurred.
  3. Update this matrix and [docs/ecs-benchmark-scorecard.md](../ecs-benchmark-scorecard.md) in the same pull request.
