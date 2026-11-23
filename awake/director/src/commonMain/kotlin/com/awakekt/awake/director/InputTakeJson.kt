/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.director

import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.InputTake
import com.awakekt.awake.core.input.InputTakeFrame
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import kotlinx.serialization.Serializable

/**
 * Pure Kotlin Multiplatform JSON serializer/deserializer for [InputTake] (`*.take.json`), keeping
 * `:awake:core:input` free of kotlinx.serialization dependencies.
 */
object InputTakeJson {
    /** Encodes [take] to formatted JSON text. */
    fun encode(take: InputTake): String = DirectorScript.json.encodeToString(
        SerializableInputTake.serializer(),
        SerializableInputTake(
            version = take.version,
            scenePath = take.scenePath,
            fixedStepSeconds = take.fixedStepSeconds,
            totalFrames = take.totalFrames,
            seed = take.seed,
            frames = take.frames.map { frame ->
                SerializableInputTakeFrame(
                    frameIndex = frame.frameIndex,
                    pointerX = frame.pointerX,
                    pointerY = frame.pointerY,
                    scrollDeltaX = frame.scrollDeltaX,
                    scrollDeltaY = frame.scrollDeltaY,
                    keysDown = frame.keysDown.map(Key::name),
                    buttonsDown = frame.buttonsDown.map(PointerButton::name),
                    gamepadButtonsDown = frame.gamepadButtonsDown.map(GamepadButton::name),
                    gameplayCameraYaw = frame.gameplayCameraYaw,
                    gameplayCameraPitch = frame.gameplayCameraPitch,
                )
            },
        ),
    )

    /** Decodes an [InputTake] from JSON text, skipping any unknown enum names gracefully. */
    fun decode(text: String): InputTake {
        val dto = DirectorScript.json.decodeFromString(SerializableInputTake.serializer(), text)
        return InputTake(
            version = dto.version,
            scenePath = dto.scenePath,
            fixedStepSeconds = dto.fixedStepSeconds,
            totalFrames = dto.totalFrames,
            seed = dto.seed,
            frames = dto.frames.map { f ->
                InputTakeFrame(
                    frameIndex = f.frameIndex,
                    pointerX = f.pointerX,
                    pointerY = f.pointerY,
                    scrollDeltaX = f.scrollDeltaX,
                    scrollDeltaY = f.scrollDeltaY,
                    keysDown = f.keysDown.mapNotNull { name -> Key.entries.firstOrNull { it.name == name } }.toSet(),
                    buttonsDown = f.buttonsDown.mapNotNull { name -> PointerButton.entries.firstOrNull { it.name == name } }.toSet(),
                    gamepadButtonsDown = f.gamepadButtonsDown.mapNotNull { name -> GamepadButton.entries.firstOrNull { it.name == name } }.toSet(),
                    gameplayCameraYaw = f.gameplayCameraYaw,
                    gameplayCameraPitch = f.gameplayCameraPitch,
                )
            },
        )
    }

    @Serializable
    private data class SerializableInputTake(
        val version: Int = InputTake.CURRENT_VERSION,
        val scenePath: String = "",
        val fixedStepSeconds: Float = InputTake.DEFAULT_FIXED_STEP_SECONDS,
        val totalFrames: Int = 0,
        val seed: Long = 0L,
        val frames: List<SerializableInputTakeFrame> = emptyList(),
    )

    @Serializable
    private data class SerializableInputTakeFrame(
        val frameIndex: Int,
        val pointerX: Float = 0f,
        val pointerY: Float = 0f,
        val scrollDeltaX: Float = 0f,
        val scrollDeltaY: Float = 0f,
        val keysDown: List<String> = emptyList(),
        val buttonsDown: List<String> = emptyList(),
        val gamepadButtonsDown: List<String> = emptyList(),
        val gameplayCameraYaw: Float? = null,
        val gameplayCameraPitch: Float? = null,
    )
}
