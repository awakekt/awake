/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Loading screen for an AwakeKt WebGPU page. Load it before the app bundle:
 *
 *   <script src="awake-loader.js" data-product="Studio"></script>
 *   <script src="studio.js"></script>
 *
 * It needs nothing from the app. Download progress is the .wasm response's bytes; later stages
 * come from the WebGPU calls the engine makes while it starts; the first frame drawn to the canvas
 * dismisses it. An engine startup error, or a browser without WebGPU, shows a message instead.
 */
(() => {
  const product = document.currentScript?.dataset.product ?? "";
  // The Ember Signal glyph without its field; its gold stops follow the scheme through --glyph-*.
  const MARK = `<svg class="mark" viewBox="166 146 680 680" aria-hidden="true"><defs><linearGradient id="awake-glyph-left" x1="145" y1="810" x2="512" y2="150" gradientUnits="userSpaceOnUse"><stop stop-color="#FF4D2E"/><stop offset="1" stop-color="#FF9F1C"/></linearGradient><linearGradient id="awake-glyph-right" x1="512" y1="150" x2="890" y2="810" gradientUnits="userSpaceOnUse"><stop offset="0" style="stop-color: var(--glyph-r0)"/><stop offset="1" style="stop-color: var(--glyph-r1)"/></linearGradient><linearGradient id="awake-glyph-top" x1="418" y1="570" x2="606" y2="570" gradientUnits="userSpaceOnUse"><stop offset="0" style="stop-color: var(--glyph-t0)"/><stop offset="1" style="stop-color: var(--glyph-t1)"/></linearGradient></defs><path d="M505 208 505 339 346 618 266 765 194 690 415 275Z" fill="url(#awake-glyph-left)"/><path d="M266 765 346 618 402 674Z" fill="#8A2E20"/><path d="M505 208 598 275 818 690 747 765 664 618 505 339Z" fill="url(#awake-glyph-right)"/><path d="M609 674 664 618 747 765Z" fill="#D9482B"/><path d="m505 522 89 53-89 55-87-55Z" fill="url(#awake-glyph-top)"/><path d="m418 575 87 55v96l-87-55Z" fill="#8A2E20"/><path d="m594 575-89 55v96l89-55Z" fill="#D9482B"/></svg>`;
  // Share of the bar each stage owns, in order.
  const DOWNLOAD = 60, GPU = 10, SHADERS = 25;

  const style = document.createElement("style");
  style.textContent = `
    #awake-loader { --bg: radial-gradient(120% 90% at 36% 20%, #24202A 0%, #15151C 56%, #10131A 100%);
      --text: #ECEAE6; --muted: #A7A3AD; --faint: #7E7A86; --track: rgba(236, 234, 230, 0.08); --error: #FF9F80;
      --glyph-r0: #FFF0B3; --glyph-r1: #FFD166; --glyph-t0: #FFB84A; --glyph-t1: #FFE29A;
      --glow: rgba(255, 107, 53, 0.18); --glow-peak: rgba(255, 107, 53, 0.36);
      position: fixed; inset: 0; z-index: 2147483647; display: grid; place-items: center;
      background: var(--bg); color: var(--text); font: 400 15px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif;
      -webkit-font-smoothing: antialiased; transition: opacity 320ms cubic-bezier(0.23, 1, 0.32, 1); }
    @media (prefers-color-scheme: light) {
      #awake-loader { --bg: #F6F6F7; --text: #16161C; --muted: #4E4A55; --faint: #66626D;
        --track: rgba(22, 22, 28, 0.10); --error: #B3361D;
        --glyph-r0: #F7BE3A; --glyph-r1: #E8920E; --glyph-t0: #F29A2E; --glyph-t1: #F7BF48;
        --glow: rgba(232, 146, 14, 0.10); --glow-peak: rgba(232, 146, 14, 0.22); }
    }
    #awake-loader.done { opacity: 0; pointer-events: none; }
    #awake-loader .box { width: min(320px, 80vw); display: flex; flex-direction: column; align-items: center; }
    #awake-loader svg.mark { width: 88px; height: 88px; filter: drop-shadow(0 8px 20px var(--glow));
      animation: awake-loader-breathe 2.4s ease-in-out infinite; }
    @keyframes awake-loader-breathe { 50% { filter: drop-shadow(0 8px 28px var(--glow-peak)); } }
    #awake-loader .name { margin: 20px 0 0; font-size: 17px; font-weight: 600; letter-spacing: -0.01em; }
    #awake-loader .name span { font-weight: 400; color: var(--muted); }
    #awake-loader .track { width: 100%; height: 4px; margin-top: 28px; border-radius: 999px;
      background: var(--track); overflow: hidden; }
    #awake-loader .bar { height: 100%; border-radius: inherit; transform-origin: left; transform: scaleX(0);
      background: linear-gradient(90deg, #FF4D2E, #FF9F1C 60%, #FFD166);
      transition: transform 280ms cubic-bezier(0.23, 1, 0.32, 1); }
    #awake-loader .status { display: flex; justify-content: space-between; width: 100%; margin-top: 10px;
      font-size: 13px; color: var(--faint); font-variant-numeric: tabular-nums; }
    #awake-loader .step { color: var(--muted); }
    #awake-loader .error { display: none; margin: 16px 0 0; font-size: 13px; color: var(--error); text-align: center; }
    #awake-loader.failed .error { display: block; }
    #awake-loader.failed .bar { background: #D9482B; }
    @media (prefers-reduced-motion: reduce) {
      #awake-loader, #awake-loader .bar { transition: none; }
      #awake-loader svg.mark { animation: none; }
    }`;

  const root = document.createElement("div");
  root.id = "awake-loader";
  root.setAttribute("role", "progressbar");
  root.setAttribute("aria-label", `Loading AwakeKt ${product}`.trim());
  root.setAttribute("aria-valuemin", "0");
  root.setAttribute("aria-valuemax", "100");
  root.innerHTML = `<div class="box">${MARK}
      <p class="name">AwakeKt${product ? ` <span>${product}</span>` : ""}</p>
      <div class="track"><div class="bar"></div></div>
      <div class="status"><span class="step">Starting…</span><span class="pct">0%</span></div>
      <p class="error" role="alert"></p></div>`;
  const mount = () => { document.head.append(style); document.body.append(root); };
  document.body ? mount() : document.addEventListener("DOMContentLoaded", mount, { once: true });

  const bar = root.querySelector(".bar"), stepEl = root.querySelector(".step"), pctEl = root.querySelector(".pct");
  // A failure pauses the bar, but a frame on screen still dismisses the loader: an error the engine
  // logged and survived must not cover a working app.
  let shown = 0, finished = false, failedState = false;
  const progress = (value, step) => {
    if (finished || failedState) return;
    shown = Math.max(shown, Math.min(value, 100));
    bar.style.transform = `scaleX(${shown / 100})`;
    pctEl.textContent = `${Math.round(shown)}%`;
    root.setAttribute("aria-valuenow", String(Math.round(shown)));
    if (step) stepEl.textContent = step;
  };
  const fail = (message) => {
    if (finished || failedState) return;
    failedState = true;
    root.classList.add("failed");
    stepEl.textContent = "Couldn't start";
    root.querySelector(".error").textContent = message;
  };
  const finish = () => {
    if (finished) return;
    failedState = false;
    progress(100, "Ready");
    finished = true;
    root.classList.add("done");
    setTimeout(() => root.remove(), 400);
  };

  if (!("gpu" in navigator)) {
    fail("This browser doesn't support WebGPU. Try a current Chrome or Edge, or Safari 26 or later.");
    return;
  }

  // Download: count the .wasm response's bytes. A compressed response's Content-Length is the
  // compressed size, so without a usable total the bar eases toward the stage's end instead.
  const nativeFetch = window.fetch.bind(window);
  window.fetch = async (input, init) => {
    const response = await nativeFetch(input, init);
    const url = typeof input === "string" ? input : input?.url ?? "";
    if (!/\.wasm(\?|$)/.test(url) || !response.body) return response;
    if (!response.ok) {
      fail(`Couldn't download the engine (HTTP ${response.status}).`);
      return response;
    }
    const encoded = response.headers.get("content-encoding");
    const total = encoded && encoded !== "identity" ? 0 : Number(response.headers.get("content-length")) || 0;
    let received = 0;
    const reader = response.body.getReader();
    const counted = new ReadableStream({
      async pull(controller) {
        const { done, value } = await reader.read();
        if (done) { progress(DOWNLOAD, "Starting WebGPU…"); controller.close(); return; }
        received += value.byteLength;
        const share = total ? received / total : 1 - Math.exp(-received / 4e6);
        progress(share * DOWNLOAD, "Downloading engine…");
        controller.enqueue(value);
      },
      cancel(reason) { reader.cancel(reason); },
    });
    return new Response(counted, { status: response.status, statusText: response.statusText, headers: response.headers });
  };
  progress(0, "Downloading engine…");

  // Startup: the engine's own WebGPU calls, in the order it makes them.
  const wrap = (proto, name, before) => {
    const original = proto?.[name];
    if (typeof original !== "function") return;
    proto[name] = function (...args) { before.call(this, args); return original.apply(this, args); };
  };
  wrap(window.GPU?.prototype, "requestAdapter", () => progress(DOWNLOAD + GPU / 2, "Starting WebGPU…"));
  wrap(window.GPUAdapter?.prototype, "requestDevice", () => progress(DOWNLOAD + GPU, "Compiling shaders…"));
  let pipelines = 0;
  const pipeline = () => {
    pipelines += 1;
    progress(DOWNLOAD + GPU + SHADERS * (1 - Math.exp(-pipelines / 6)), "Compiling shaders…");
  };
  wrap(window.GPUDevice?.prototype, "createRenderPipeline", pipeline);
  wrap(window.GPUDevice?.prototype, "createRenderPipelineAsync", pipeline);
  // The first submit after the canvas handed out a texture is the first frame on screen.
  let drewToCanvas = false;
  wrap(window.GPUCanvasContext?.prototype, "getCurrentTexture", () => {
    if (!drewToCanvas) progress(DOWNLOAD + GPU + SHADERS, "Loading scene…");
    drewToCanvas = true;
  });
  wrap(window.GPUQueue?.prototype, "submit", () => { if (drewToCanvas) requestAnimationFrame(finish); });

  // Startup failures: the WebGPU host's console.error reports and the engine log's error lines.
  const nativeError = console.error.bind(console), nativeLog = console.log.bind(console);
  const failed = (text) => /WebGPU (startup|frame) failed|WebGPU uncaptured error|^f\d+ E {2}\S+ {2}/.test(text);
  console.error = (...args) => { const text = args.join(" "); if (failed(text)) fail(text.replace(/^f\d+ E {2}/, "")); nativeError(...args); };
  console.log = (...args) => { const text = args.join(" "); if (failed(text)) fail(text.replace(/^f\d+ E {2}/, "")); nativeLog(...args); };
  window.addEventListener("error", (event) => fail(event.message));
  window.addEventListener("unhandledrejection", (event) => fail(String(event.reason?.message ?? event.reason)));
})();
