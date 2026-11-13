/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

/**
 * The names of Core's systems, in one place: each is the name [SceneSystemSet.planned] lists and the
 * name a host passes to disable it, as `sceneSystemsFor(..., disabled = setOf(CoreSystems.CAMERA))`
 * does. They are stable across releases: a name is never changed or reused, and a system that goes
 * away has its constant deprecated first. [all] lists them. A game's own systems are named by its
 * capabilities, which list them in [SceneCapability.systemNames].
 */
object CoreSystems {
    /** `playerInput`: The keymap, or the canvas touch controls, read into the frame's input actions. */
    const val PLAYER_INPUT: String = "playerInput"

    /** `canvas-actions`: Begins the actions that controls added at runtime read. */
    const val CANVAS_ACTIONS: String = "canvas-actions"

    /** `movement`: Moves a `movement_control` straight through the world when it has no character controller. */
    const val MOVEMENT: String = "movement"

    /** `camera`: Follows and orbits a `camera_rig`. */
    const val CAMERA: String = "camera"

    /** `occluder-fade`: Fades what a camera rig's occluder fade finds between its eye and target. */
    const val OCCLUDER_FADE: String = "occluder-fade"

    /** `canvas-occlusion`: Answers the canvas followers that ask for occlusion. */
    const val CANVAS_OCCLUSION: String = "canvas-occlusion"

    /** `paged-terrain`: Streams a `paged_terrain`'s cells around the primary camera. */
    const val PAGED_TERRAIN: String = "paged-terrain"

    /** `terrain`: Draws a `terrain`. */
    const val TERRAIN: String = "terrain"

    /** `terrain-collider`: Builds a `terrain` collider. */
    const val TERRAIN_COLLIDER: String = "terrain-collider"

    /** `mesh-collider`: Builds the triangle and convex-hull colliders of `mesh` shapes. */
    const val MESH_COLLIDER: String = "mesh-collider"

    /** `physics`: The physics step. */
    const val PHYSICS: String = "physics"

    /** `character-sensor-bodies`: Gives a character's sensor a body of its own. */
    const val CHARACTER_SENSOR_BODIES: String = "character-sensor-bodies"

    /** `character`: The character controller step. */
    const val CHARACTER_CONTROLLER: String = "character"

    /** `navigation-agents`: Steers `navigation_agent`s. */
    const val NAVIGATION_AGENTS: String = "navigation-agents"

    /** `ai-intents`: Resets each agent's intent for the frame. */
    const val AI_INTENTS: String = "ai-intents"

    /** `ai-perception`: Sight over transforms and factions. */
    const val AI_PERCEPTION: String = "ai-perception"

    /** `ai-definitions`: Attaches behavior-tree and state-machine definitions. */
    const val AI_DEFINITIONS: String = "ai-definitions"

    /** `ai-trees`: Runs behavior trees. */
    const val AI_TREES: String = "ai-trees"

    /** `ai-machines`: Runs AI state machines. */
    const val AI_MACHINES: String = "ai-machines"

    /** `ai-patrol`: The `patrol` behaviour. */
    const val AI_PATROL: String = "ai-patrol"

    /** `ai-chase`: The `chase` behaviour. */
    const val AI_CHASE: String = "ai-chase"

    /** `ai-flee`: The `flee` behaviour. */
    const val AI_FLEE: String = "ai-flee"

    /** `path-requests`: Answers the behaviours' route requests. */
    const val PATH_REQUESTS: String = "path-requests"

    /** `spin-clock`: The clock `spin` reads. */
    const val SPIN_CLOCK: String = "spin-clock"

    /** `spin`: Spins entities. */
    const val SPIN: String = "spin"

    /** `locomotion`: Picks a model's locomotion clips. */
    const val LOCOMOTION: String = "locomotion"

    /** `keyframes`: Plays `keyframe_animation` tracks. */
    const val KEYFRAMES: String = "keyframes"

    /** `texture-clips`: Steps a sprite sheet's clips. */
    const val TEXTURE_CLIPS: String = "texture-clips"

    /** `sprite-clips`: Steps each sprite's clip. */
    const val SPRITE_CLIPS: String = "sprite-clips"

    /** `particles-2d`: Advances 2D particle emitters. */
    const val PARTICLES_2D: String = "particles-2d"

    /** `particle-content`: Makes the particle sprites on the GPU. */
    const val PARTICLE_CONTENT: String = "particle-content"

    /** `particles`: Advances 3D particle emitters. */
    const val PARTICLES: String = "particles"

    /** `day-cycle`: The sun's path and the blended sky, light and fog. */
    const val DAY_CYCLE: String = "day-cycle"

    /** `shader-effects`: Draws `shader_effect` documents. */
    const val SHADER_EFFECTS: String = "shader-effects"

    /** `audio-listener`: Places the listener at the camera. */
    const val AUDIO_LISTENER: String = "audio-listener"

    /** `audio`: Plays `audio_source`s. */
    const val AUDIO: String = "audio"

    /** `animation`: Skinned glTF animation. */
    const val ANIMATION: String = "animation"

    /** Every Core system name, in the order Core plans them. */
    val all: List<String> = listOf(
        PLAYER_INPUT,
        CANVAS_ACTIONS,
        MOVEMENT,
        CAMERA,
        OCCLUDER_FADE,
        CANVAS_OCCLUSION,
        PAGED_TERRAIN,
        TERRAIN,
        TERRAIN_COLLIDER,
        MESH_COLLIDER,
        PHYSICS,
        CHARACTER_SENSOR_BODIES,
        CHARACTER_CONTROLLER,
        NAVIGATION_AGENTS,
        AI_INTENTS,
        AI_PERCEPTION,
        AI_DEFINITIONS,
        AI_TREES,
        AI_MACHINES,
        AI_PATROL,
        AI_CHASE,
        AI_FLEE,
        PATH_REQUESTS,
        SPIN_CLOCK,
        SPIN,
        LOCOMOTION,
        KEYFRAMES,
        TEXTURE_CLIPS,
        SPRITE_CLIPS,
        PARTICLES_2D,
        PARTICLE_CONTENT,
        PARTICLES,
        DAY_CYCLE,
        SHADER_EFFECTS,
        AUDIO_LISTENER,
        AUDIO,
        ANIMATION,
    )
}
