# Pluggable Compose Editor Architecture for Awake Engine

**Date:** 2026-08-28  
**Status:** `Active`  
**Parent / Issue:** #<editor-modularization>  
**Target Modules:** `:awake:editor:model`, `:domain`, `:presenter`, `:ui`  

---

## 1. Real-World Mental Model (The Modular Workbench)

> [!TIP]
> **The Real-World Analogy**:  
> *Think of an artist's professional modular workbench (like Blender, Unreal, or VS Code).*
> The core workbench provides the sturdy table, the adjustable lamp, the camera tripod, and the magnetic rail (Viewport, Orbit Camera, 3D Gizmos, Selection, Undo/Redo).
>
> An environment artist snaps in a **Terrain & Foliage Brush** tray; a character rigger snaps in a **Bone & Animation Rigging** panel; a technical artist snaps in a **Live Shader Material Editor**.
>
> Awake Core is the universal workbench; any downstream game pack or tool simply snaps in custom **Shaders, ECS Systems, and Compose UI Panels** without modifying the core engine frame.

- **User Goal**: Make `awake:editor` 100% pluggable and domain-agnostic so any game pack or tooling studio can integrate custom shaders, simulation systems, and Compose UI panels seamlessly.
- **Root Cause / Current State**: `awake:editor:scene` currently hardcodes generic ECS panels without a modular slot-based docking, custom system runner, or live shader inspector seam.

---

## 2. The 3 Core Extension Pillars: Shaders, Systems, and Custom UI

```
                                  EditorSession
                                       │
     ┌─────────────────────────────────┼─────────────────────────────────┐
     ▼                                 ▼                                 ▼
1. RUNTIME SHADERS               2. ECS SYSTEMS                    3. CUSTOM COMPOSE UI
 • Live WGSL preview              • Custom authoring systems        • Dockable left/right panels
 • Real-time uniform tuning       • Simulation play/pause/step      • Custom viewport HUD overlays
 • Shader error diagnostics       • Gizmo pick & transform          • Slot-based toolbars
```

---

## 3. How to Use Each Extension Pillar (Call-Site Code Examples)

### A. Live Runtime Shaders in Editor
Inspect, compile, and tweak WGSL / ASL shaders in the live 3D viewport with immediate parameter reloading:

```kotlin
// Register a custom procedural material shader into the editor's asset registry
val waterShader = registerEditorShader(
    id = "procedural_water",
    source = WgslSource(
        """
        struct Uniforms { waveSpeed: f32, waveHeight: f32, waterColor: vec4<f32> };
        @group(0) @binding(0) var<uniform> u: Uniforms;
        // Vertex & fragment functions compiled in-memory via Naga...
        """.trimIndent()
    )
)

// The editor automatically reflects uniform parameters into the Inspector Panel:
// Sliders for 'waveSpeed', 'waveHeight', and a ColorPicker for 'waterColor'.
```

### B. Custom ECS Systems in Editor
Plug custom simulation, physics, or visual effect systems into the editor's update cycle (supporting Play, Pause, and Step modes):

```kotlin
val editorSession = createAwakeEditorSession {
    // 1. Register domain ECS systems that run in authoring mode:
    registerSystem(ParticleEmitterSystem())      // Previews live particle effects
    registerSystem(SkeletonAnimationSystem())    // Previews skinned mesh animation poses
    registerSystem(PhysicsDebugRaycastSystem())  // Shows collision wireframes

    // 2. Control simulation lifecycle during editing:
    simulationMode = SimulationMode.Paused       // Freeze frame for precision placement
}
```

### C. Custom Compose Multiplatform UI Panels
Build custom toolbars, dockable tabs, and viewport HUD overlays using design tokens (`ShadcnTheme.colors.*`):

```kotlin
// Define a custom tool panel plugin
class FoliageBrushPlugin : EditorPanelPlugin {
    override val id: String = "foliage_brush"
    override val title: String = "Foliage Painter"
    override val icon: ImageVector = Icons.Brush
    override val dockPosition: DockPosition = DockPosition.LeftDock

    @Composable
    override fun Content(editorState: EditorWorkspaceState) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text("Brush Radius", style = ShadcnTheme.typography.label)
            Slider(value = brushRadius, onValueChange = { brushRadius = it })
            Button(onClick = { editorState.dispatch(FoliageIntent.ClearAll) }) {
                Text("Clear Foliage")
            }
        }
    }
}
```

---

## 4. The 4-Stage Architectural Progression

```
┌─────────────────────────┐     ┌─────────────────────────┐     ┌─────────────────────────┐     ┌─────────────────────────┐
│ 1. :awake:editor:model  │ ──► │ 2. :awake:editor:domain │ ──► │ 3. :editor:presenter    │ ──► │ 4. :awake:editor:ui     │
│  (Selection, DockState) │     │ (Undo/Redo, Gizmo Math) │     │   (EditorStore / VM)    │     │ (Compose Dock & Shadcn) │
└─────────────────────────┘     └─────────────────────────┘     └─────────────────────────┘     └─────────────────────────┘
```

### Stage 1: Data & State Models (`:awake:editor:model`)
- [ ] `EditorSelection`: Multi-entity, asset, or spatial mesh sub-selection.
- [ ] `DockLayoutState`: Configurable panel splits (`LeftPanel`, `RightPanel`, `BottomDrawer`, `ViewportOverlay`).
- [ ] `EditorPlugin`: Extensible descriptor interface with tabs, icons, and inspector contracts.

### Stage 2: Domain Logic & Undo/Redo (`:awake:editor:domain`)
- [ ] `EditorCommandHistory`: Robust undo/redo transaction stack (`TransformEntityCommand`, `ModifyMaterialCommand`).
- [ ] `GizmoRaycastSystem`: Raycasting and 3D translation/rotation/scale drag math on plain JVM.
- [ ] `EditorCameraSystem`: Orbit, pan, fly-through, and focal-point framing.

### Stage 3: Presenter & State Flow (`:awake:editor:presenter`)
- [ ] `EditorViewModel` / `EditorStore`: Single unified `StateFlow<EditorWorkspaceState>`.
- [ ] Testable on plain JVM in 10ms without Vulkan or graphics initialization.

### Stage 4: Compose UI & Docking Shell (`:awake:editor:ui`)
- [ ] `AwakeEditorWorkspace`: Flexible split-pane layout using modern Compose Multiplatform foundation.
- [ ] `SceneViewportPanel`: Embeds hardware swapchain with gizmo overlay and camera controls.
- [ ] `InspectorHostPanel`: Dynamically renders registered plugin inspectors based on active selection.

---

## 5. Verification & Testing

```bash
# 1. Run headless JVM tests for Editor Undo/Redo & Selection
./gradlew :awake:editor:domain:desktopTest

# 2. Test Presenter StateFlow transactions
./gradlew :awake:editor:presenter:desktopTest

# 3. Visual Roborazzi screenshot verification of Docking Panels
./gradlew :awake:editor:ui:recordRoborazziDesktop
```

---
*Generated via Vibe Planning Standard — Automatically indexed by `heal_docs.py`*
