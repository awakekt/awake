/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

/**
 * Asks a running project to play another of its scenes: what a menu's Play button or a level's exit
 * does. A system reaches it through [SceneHostServices.sceneSwitch], and an app that plays a project
 * with [runProject] through its `SceneSwitch` service; a blueprint's Load Scene node asks through it.
 *
 * [runProject]'s switch reads the next scene off the frame thread while the one playing goes on, then
 * puts it in that scene's place at the start of the next fixed step or frame, never inside a system's
 * update. It closes the scene it leaves (its systems, entities, blueprints, sounds, spawned nodes and
 * physics world) and plays the next from its file, with the systems its components call for, so
 * nothing carries across. A scene that can't be read or checked is logged, once, and the one playing
 * goes on.
 *
 * A request has a [SceneSwitchState], which a game reads from the app's [SceneSwitchStatus] service to
 * show a loading curtain or an error: loading with the share of the next scene's bytes that have
 * arrived, ready once the new scene has had its first frame, or failed with the scene's path and cause.
 */
fun interface SceneSwitch {
    /**
     * Asks for the scene at [path], relative to the project root, such as `scenes/level-2.scene.json`.
     * Asking again before it plays changes nothing; asking for another scene instead replaces the
     * request; asking for the scene that is playing starts it again from its file. Call it on the
     * frame thread, as a system or a node does.
     */
    fun request(path: String)
}
