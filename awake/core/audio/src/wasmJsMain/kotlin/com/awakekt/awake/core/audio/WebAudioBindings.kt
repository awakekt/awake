/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.audio

import kotlin.js.JsAny

internal external interface WebAudioGraph : JsAny {
    val master: JsAny
    val sfx: JsAny
    val music: JsAny
}

internal external interface WebAudioVoice : JsAny {
    val playing: Boolean
}

@JsFun(
    """
(context, output) => {
    const master = context.createGain(), sfx = context.createGain(), music = context.createGain();
    sfx.connect(master); music.connect(master); master.connect(output);
    return { master, sfx, music };
}
""",
)
internal external fun createAudioGraph(context: JsAny, output: JsAny): WebAudioGraph

@JsFun("(node, value) => { node.gain.value = value; }")
internal external fun setAudioGain(node: JsAny, value: Float)

@JsFun("(graph) => { graph.sfx.disconnect(); graph.music.disconnect(); graph.master.disconnect(); }")
internal external fun disconnectAudioGraph(graph: WebAudioGraph)

@JsFun(
    """
(context, channels, frames, rate, sample) => {
    const buffer = context.createBuffer(channels, frames, rate);
    for (let channel = 0; channel < channels; channel++) {
        const data = buffer.getChannelData(channel);
        for (let frame = 0; frame < frames; frame++) data[frame] = sample(frame, channel);
    }
    return buffer;
}
""",
)
internal external fun createAudioBuffer(context: JsAny, channels: Int, frames: Int, rate: Int, sample: (Int, Int) -> Double): JsAny

@JsFun(
    """
(context, buffer, bus, volume, pan, loop, onEnded) => {
    const source = context.createBufferSource(), gain = context.createGain(), panner = context.createStereoPanner();
    source.buffer = buffer; source.loop = loop; gain.gain.value = volume; panner.pan.value = pan;
    source.connect(gain); gain.connect(panner); panner.connect(bus);
    const voice = { source, gain, panner, playing: true };
    voice.finish = () => {
        if (!voice.playing) return;
        voice.playing = false;
        source.disconnect(); gain.disconnect(); panner.disconnect();
        onEnded();
    };
    source.onended = voice.finish;
    source.start();
    return voice;
}
""",
)
@Suppress("LongParameterList") // The native graph must receive source parameters before start().
internal external fun startAudioVoice(
    context: JsAny,
    buffer: JsAny,
    bus: JsAny,
    volume: Float,
    pan: Float,
    loop: Boolean,
    onEnded: () -> Unit,
): WebAudioVoice

@JsFun(
    """
(voice, volume, pan) => {
    if (!voice.playing) return;
    voice.gain.gain.value = volume; voice.panner.pan.value = pan;
}
""",
)
internal external fun updateAudioVoice(voice: WebAudioVoice, volume: Float, pan: Float)

@JsFun("(voice) => { if (voice.playing) { voice.source.stop(); voice.finish(); } }")
internal external fun stopAudioVoice(voice: WebAudioVoice)
