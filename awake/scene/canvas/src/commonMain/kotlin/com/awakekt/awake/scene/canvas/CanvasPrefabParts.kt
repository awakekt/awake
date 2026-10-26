/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions") // One function for each element kind, beside the builder's own.

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.scene.document.SceneValidationException

// The prefab builder's functions that take a typed handle, beside the ones that take a string id:
// the element gets the handle's id and kind, so the saved prefab is the one string ids build.

/**
 * A prefab whose root element is [root]: [canvasPrefab] with the root's kind and id taken from the
 * handle, such as a widget's `panel()`.
 *
 * @param guid The prefab's GUID; keep it the same from one build to the next.
 * @param name The prefab's name, which is its root node's name too.
 * @param root The root element's handle.
 * @param block Sets up the root element and adds its children.
 * @return The validated prefab.
 * @throws SceneValidationException when the prefab has a problem, listing each one.
 */
fun canvasPrefab(guid: String, name: String, root: CanvasPart, block: CanvasElementScope.() -> Unit): ScenePrefab =
    canvasPrefab(guid = guid, name = name, kind = root.kind, id = root.id, block = block)

/**
 * Adds a child element for [part], of its kind and with its id, named [name], set up by [block].
 * The name defaults to the part's id, which is unique in the prefab as node names must be.
 */
fun CanvasElementScope.element(part: CanvasPart, name: String = part.id, block: CanvasElementScope.() -> Unit = {}) =
    element(name, part.kind, part.id, block)

/** Adds the Text child [part]; see [element]. */
fun CanvasElementScope.text(part: CanvasTextPart, name: String = part.id, block: CanvasElementScope.() -> Unit = {}) =
    element(part, name, block)

/** Adds the Panel child [part]; see [element]. */
fun CanvasElementScope.panel(part: CanvasPanelPart, name: String = part.id, block: CanvasElementScope.() -> Unit = {}) =
    element(part, name, block)

/** Adds the Image child [part]; see [element]. */
fun CanvasElementScope.image(part: CanvasImagePart, name: String = part.id, block: CanvasElementScope.() -> Unit = {}) =
    element(part, name, block)

/** Adds the View child [part]; see [element]. */
fun CanvasElementScope.view(part: CanvasViewPart, name: String = part.id, block: CanvasElementScope.() -> Unit = {}) =
    element(part, name, block)

/** Adds the Bar child [part]; see [element]. */
fun CanvasElementScope.bar(part: CanvasBarPart, name: String = part.id, block: CanvasElementScope.() -> Unit = {}) =
    element(part, name, block)

/** Adds the Sweep child [part]; see [element]. */
fun CanvasElementScope.sweep(part: CanvasSweepPart, name: String = part.id, block: CanvasElementScope.() -> Unit = {}) =
    element(part, name, block)

/** Adds the Button child [part]; see [element]. */
fun CanvasElementScope.button(part: CanvasButtonPart, name: String = part.id, block: CanvasElementScope.() -> Unit = {}) =
    element(part, name, block)

/** Adds the TextField child [part]; see [element]. */
fun CanvasElementScope.textField(part: CanvasTextFieldPart, name: String = part.id, block: CanvasElementScope.() -> Unit = {}) =
    element(part, name, block)

/** Adds the Joystick child [part]; see [element]. */
fun CanvasElementScope.joystick(part: CanvasJoystickPart, name: String = part.id, block: CanvasElementScope.() -> Unit = {}) =
    element(part, name, block)

/** Adds the LookArea child [part]; see [element]. */
fun CanvasElementScope.lookArea(part: CanvasLookAreaPart, name: String = part.id, block: CanvasElementScope.() -> Unit = {}) =
    element(part, name, block)

/** Adds the Stepper child [part]; see [element]. */
fun CanvasElementScope.stepper(part: CanvasStepperPart, name: String = part.id, block: CanvasElementScope.() -> Unit = {}) =
    element(part, name, block)
