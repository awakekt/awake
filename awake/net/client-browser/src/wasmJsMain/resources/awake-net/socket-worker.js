// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0

// A browser WebSocket in a dedicated worker of its own (awakekt/awake#1280). The page runs the game's
// frame on its one thread, where a socket's message events wait behind the frame, so a reply is seen
// late and bufferedAmount is lowered late. Here the socket's events run at once: each message is
// stamped as it arrives and handed to the page with its stamp, and the socket's bufferedAmount is
// reported back as it changes. com.awakekt.awake.net.browser's SocketConnection is the page's side.
//
// Page to worker: {t: 'open', url} once; then each packet to send as a bare ArrayBuffer, transferred;
// {t: 's', id, ms} to fire timer `id` after `ms` milliseconds on the worker's unthrottled clock;
// {t: 'close'}.
// Worker to page:
//   {t: 'open'}                     the socket opened;
//   {t: 'm', data, at}              a binary message, its ArrayBuffer transferred, `at` when it arrived;
//   {t: 'b', buffered, taken}       the socket's bufferedAmount, and the bytes of every packet the page
//                                   has posted that the worker has taken (sent, or dropped once the
//                                   socket closed): after each send, then every 50 ms while
//                                   bufferedAmount isn't 0, and once more as it reaches 0;
//   {t: 's', id}                    scheduled timer `id` came due;
//   {t: 'x', code, reason, error}   the socket closed (`error` is true when an error event came first).
//
// Times are milliseconds since the Unix epoch on the high-resolution clock: performance.timeOrigin
// plus performance.now(). A dedicated worker's performance.now() counts from the worker's own
// creation, not the page's, so the page compares `at` with its own timeOrigin plus now(); the two
// agree to well under a millisecond.

'use strict';

const REPORT_MS = 50;

let socket = null;
let taken = 0;
let reporting = 0;
let errored = false;
const timers = new Map();

const now = () => performance.timeOrigin + performance.now();

function stopReporting() {
    if (reporting) clearInterval(reporting);
    reporting = 0;
}

function stopTimers() {
    for (const handle of timers.values()) clearTimeout(handle);
    timers.clear();
}

function report() {
    const buffered = socket ? socket.bufferedAmount : 0;
    postMessage({ t: 'b', buffered, taken });
    if (buffered > 0 && !reporting) {
        reporting = setInterval(() => {
            const still = socket ? socket.bufferedAmount : 0;
            postMessage({ t: 'b', buffered: still, taken });
            if (still === 0) stopReporting();
        }, REPORT_MS);
    }
}

function open(url) {
    try {
        socket = new WebSocket(url);
    } catch (_) {
        // A URL the browser won't open (not ws: or wss:, or malformed) fails as a refused connection.
        postMessage({ t: 'x', code: 1006, reason: '', error: true });
        return;
    }
    socket.binaryType = 'arraybuffer';
    socket.addEventListener('open', () => postMessage({ t: 'open' }));
    socket.addEventListener('message', (e) => {
        const at = now();
        // Packets are binary; a text message is not one.
        if (e.data instanceof ArrayBuffer) postMessage({ t: 'm', data: e.data, at }, [e.data]);
    });
    socket.addEventListener('error', () => { errored = true; });
    socket.addEventListener('close', (e) => {
        stopReporting();
        stopTimers();
        postMessage({ t: 'x', code: e.code, reason: e.reason, error: errored });
        close();
    });
}

onmessage = (e) => {
    const m = e.data;
    if (m instanceof ArrayBuffer) {
        taken += m.byteLength;
        if (socket && socket.readyState === WebSocket.OPEN) socket.send(m);
        report();
    } else if (m.t === 'open') {
        open(m.url);
    } else if (m.t === 's') {
        const id = m.id;
        const delay = m.ms > 0 ? m.ms : 0;
        const handle = setTimeout(() => {
            timers.delete(id);
            postMessage({ t: 's', id });
        }, delay);
        timers.set(id, handle);
    } else if (m.t === 'close') {
        stopTimers();
        try { if (socket) socket.close(1000); } catch (_) {}
    }
};
