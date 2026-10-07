/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.audio

import kotlin.js.JsAny

@JsFun("() => new AudioContext()")
internal external fun createAudioContext(): JsAny

@JsFun("(context) => context.destination")
internal external fun audioDestination(context: JsAny): JsAny

@JsFun(
    """
(context) => {
    const unlock = event => {
        if (!event.isTrusted || context.state === 'running' || context.state === 'closed') return;
        context.resume().catch(error => console.warn('Awake audio could not resume:', error));
    };
    window.addEventListener('pointerdown', unlock, true);
    // Touch user activation is granted on pointerup, unlike mouse pointerdown.
    window.addEventListener('pointerup', unlock, true);
    window.addEventListener('keydown', unlock, true);
    return unlock;
}
""",
)
internal external fun installAudioUnlock(context: JsAny): JsAny

@JsFun(
    """
(unlock) => {
    window.removeEventListener('pointerdown', unlock, true);
    window.removeEventListener('pointerup', unlock, true);
    window.removeEventListener('keydown', unlock, true);
}
""",
)
internal external fun removeAudioUnlock(unlock: JsAny)

@JsFun("(context) => { if (context.state !== 'closed') context.close().catch(error => console.warn('Awake audio could not close:', error)); }")
internal external fun closeAudioContext(context: JsAny)
