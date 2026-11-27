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
 * First-party AwakeKt products (`data-product="Studio"` or `"Engine Showcase"`) show the full-colour
 * Ember Signal glyph and ember-to-gold bar. Creator games (`data-mode="game"` or `?play=` in the URL)
 * show a minimalist dark/light canvas with a flowing ember-gold liquid-slide hairline along the bottom
 * edge and a full-colour `Powered by [mark] AwakeKt` stamp in the bottom-right corner (customizable
 * via `data-logo`, `data-bg`, `data-accent`, and `data-attribution="none"` for Studio Pro).
 *
 * It needs nothing from the app. Download progress is the .wasm response's bytes; later stages
 * come from the WebGPU calls the engine makes while it starts; the first frame drawn to the canvas
 * dismisses it. An engine startup error, or a browser without WebGPU, shows a message instead.
 */
(() => {
  const script = document.currentScript;
  const product = script?.dataset.product ?? "";
  const playing = new URLSearchParams(location.search).has("play");
  const gameMode = script?.dataset.mode === "game" || playing;
  const showAttribution = script?.dataset.attribution !== "none";
  const escapeHtml = (value) =>
    value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  const safeHex = (value) => {
    const trimmed = (value ?? "").trim();
    if (!/^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i.test(trimmed)) return "";
    return trimmed.length === 4 ? `#${trimmed[1]}${trimmed[1]}${trimmed[2]}${trimmed[2]}${trimmed[3]}${trimmed[3]}` : trimmed;
  };
  const safeLogoUrl = (value) => {
    const trimmed = (value ?? "").trim();
    if (!trimmed) return "";
    if (/^(?:javascript|vbscript|data):/i.test(trimmed) || trimmed.startsWith("//")) return "";
    return trimmed;
  };
  const hexIsLight = (hex) => {
    const raw = hex.slice(1);
    const full = raw.length === 3 ? raw.split("").map((c) => c + c).join("") : raw;
    const r = parseInt(full.slice(0, 2), 16), g = parseInt(full.slice(2, 4), 16), b = parseInt(full.slice(4, 6), 16);
    return (0.299 * r + 0.587 * g + 0.114 * b) > 160;
  };
  const explicitTitle = (script?.dataset.title ?? "").trim();
  const docTitle = playing ? "" : (document.title ?? "").trim();
  const gameTitle = explicitTitle || (/^Awake(Kt)?\b/i.test(docTitle) ? "" : docTitle);
  const customLogo = gameMode ? safeLogoUrl(script?.dataset.logo) : "";
  const customBg = gameMode ? safeHex(script?.dataset.bg) : "";
  const customAccent = gameMode ? safeHex(script?.dataset.accent) : "";

  // Ember Signal glyph; its gold stops adapt to dark/light schemes via --glyph-*.
  const colorMark = (cls, idPrefix) => `<svg class="${cls}" viewBox="166 146 680 680" aria-hidden="true"><defs><linearGradient id="${idPrefix}-l" x1="145" y1="810" x2="512" y2="150" gradientUnits="userSpaceOnUse"><stop stop-color="#FF4D2E"/><stop offset="1" stop-color="#FF9F1C"/></linearGradient><linearGradient id="${idPrefix}-r" x1="512" y1="150" x2="890" y2="810" gradientUnits="userSpaceOnUse"><stop offset="0" style="stop-color: var(--glyph-r0)"/><stop offset="1" style="stop-color: var(--glyph-r1)"/></linearGradient><linearGradient id="${idPrefix}-t" x1="418" y1="570" x2="606" y2="570" gradientUnits="userSpaceOnUse"><stop offset="0" style="stop-color: var(--glyph-t0)"/><stop offset="1" style="stop-color: var(--glyph-t1)"/></linearGradient></defs><path d="M505 208 505 339 346 618 266 765 194 690 415 275Z" fill="url(#${idPrefix}-l)"/><path d="M266 765 346 618 402 674Z" fill="#8A2E20"/><path d="M505 208 598 275 818 690 747 765 664 618 505 339Z" fill="url(#${idPrefix}-r)"/><path d="M609 674 664 618 747 765Z" fill="#D9482B"/><path d="m505 522 89 53-89 55-87-55Z" fill="url(#${idPrefix}-t)"/><path d="m418 575 87 55v96l-87-55Z" fill="#8A2E20"/><path d="m594 575-89 55v96l89-55Z" fill="#D9482B"/></svg>`;
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
    #awake-loader.game { --bg: #090A0E; --text: #F2F0EC; --muted: rgba(242, 240, 236, 0.54);
      --faint: rgba(242, 240, 236, 0.42); --track: rgba(242, 240, 236, 0.08); }
    @media (prefers-color-scheme: light) {
      #awake-loader.game { --bg: #F7F7F8; --text: #16161C; --muted: rgba(22, 22, 28, 0.54);
        --faint: rgba(22, 22, 28, 0.44); --track: rgba(22, 22, 28, 0.08); }
    }
    #awake-loader.done { opacity: 0; pointer-events: none; }
    #awake-loader .box { width: min(320px, 80vw); display: flex; flex-direction: column; align-items: center; }
    #awake-loader svg.mark { width: 88px; height: 88px; filter: drop-shadow(0 8px 20px var(--glow));
      animation: awake-loader-breathe 2.4s ease-in-out infinite; }
    @keyframes awake-loader-breathe { 50% { filter: drop-shadow(0 8px 28px var(--glow-peak)); } }
    @keyframes awake-liquid-slide { 0% { background-position: 0% 50%; } 100% { background-position: 200% 50%; } }
    @keyframes awake-liquid-wave {
      0%, 100% { transform: translateX(-30%) scaleX(0.9); opacity: 0.55; }
      50% { transform: translateX(40%) scaleX(1.15); opacity: 0.95; }
    }
    @keyframes awake-float-soft { 0%, 100% { opacity: 0.88; } 50% { opacity: 1; } }
    #awake-loader .name { margin: 20px 0 0; font-size: 17px; font-weight: 600; letter-spacing: -0.01em; }
    #awake-loader .name span { font-weight: 400; color: var(--muted); }
    #awake-loader .track { width: 100%; height: 4px; margin-top: 28px; border-radius: 999px;
      background: var(--track); overflow: hidden; }
    #awake-loader .bar { height: 100%; border-radius: inherit; transform-origin: left; transform: scaleX(0);
      background: linear-gradient(90deg, #FF4D2E, #FF9F1C 60%, #FFD166);
      transition: transform 280ms cubic-bezier(0.23, 1, 0.32, 1); }
    #awake-loader.game .box { animation: awake-float-soft 2.8s ease-in-out infinite; }
    #awake-loader.game .name { margin: 0; font-size: 19px; font-weight: 600; letter-spacing: -0.015em; text-align: center; }
    #awake-loader.game img.custom-logo { max-width: 64px; max-height: 64px; object-fit: contain;
      margin-bottom: 14px; border-radius: 14px; display: block; }
    #awake-loader.game svg.center-mark { width: 44px; height: 44px; filter: drop-shadow(0 6px 18px var(--glow)); }
    #awake-loader.game .edge-track { position: fixed; left: 0; right: 0; bottom: 0; height: 3px;
      background: var(--track); overflow: hidden; }
    #awake-loader.game .bar { position: relative; width: 0%; height: 100%; transform: none;
      border-radius: 0 999px 999px 0; overflow: hidden;
      background: var(--liquid-bg, linear-gradient(90deg, #FF4D2E 0%, #FF8C1A 28%, #FFD166 52%, #FF6B35 76%, #FF4D2E 100%));
      background-size: 200% 100%; animation: awake-liquid-slide 1.8s linear infinite;
      box-shadow: var(--liquid-glow, 0 0 12px rgba(255, 130, 40, 0.55), 0 0 4px rgba(255, 209, 102, 0.7));
      transition: width 340ms cubic-bezier(0.23, 1, 0.32, 1); }
    #awake-loader.game .bar::after { content: ""; position: absolute; inset: 0;
      background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.65) 50%, transparent 100%);
      animation: awake-liquid-wave 1.4s ease-in-out infinite; }
    #awake-loader.game .attribution { position: fixed; bottom: max(20px, env(safe-area-inset-bottom, 20px));
      right: max(24px, env(safe-area-inset-right, 24px)); display: inline-flex; align-items: center; gap: 6px;
      font-size: 12px; letter-spacing: -0.01em; color: var(--muted); white-space: nowrap; }
    #awake-loader.game .attribution svg.corner-mark { width: 18px; height: 18px; flex: none;
      filter: drop-shadow(0 2px 6px rgba(255, 107, 53, 0.35)); }
    #awake-loader.game .attribution strong { font-weight: 600; color: var(--text); opacity: 0.94; }
    #awake-loader .status { display: flex; justify-content: space-between; width: 100%; margin-top: 10px;
      font-size: 13px; color: var(--faint); font-variant-numeric: tabular-nums; }
    #awake-loader .step { color: var(--muted); }
    #awake-loader .error { display: none; margin: 16px 0 0; font-size: 13px; color: var(--error); text-align: center; }
    #awake-loader.failed .error { display: block; }
    #awake-loader.failed .bar { background: #D9482B; animation: none; box-shadow: none; }
    #awake-loader.failed .bar::after { display: none; }
    @media (prefers-reduced-motion: reduce) {
      #awake-loader, #awake-loader .bar { transition: none; animation: none; }
      #awake-loader.game .bar::after, #awake-loader.game .box, #awake-loader svg.mark { animation: none; }
    }`;

  const root = document.createElement("div");
  root.id = "awake-loader";
  if (gameMode) root.className = "game";
  root.setAttribute("role", "progressbar");
  root.setAttribute("aria-label", gameMode ? (gameTitle ? `Loading ${gameTitle}` : "Loading game") : `Loading AwakeKt ${product}`.trim());
  root.setAttribute("aria-valuemin", "0");
  root.setAttribute("aria-valuemax", "100");
  if (gameMode) {
    if (customBg) {
      const light = hexIsLight(customBg);
      root.style.setProperty("--bg", customBg);
      root.style.setProperty("--text", light ? "#16161C" : "#F2F0EC");
      root.style.setProperty("--muted", light ? "rgba(22, 22, 28, 0.54)" : "rgba(242, 240, 236, 0.54)");
      root.style.setProperty("--faint", light ? "rgba(22, 22, 28, 0.44)" : "rgba(242, 240, 236, 0.42)");
      root.style.setProperty("--track", light ? "rgba(22, 22, 28, 0.10)" : "rgba(242, 240, 236, 0.10)");
      root.style.setProperty("--glyph-r0", light ? "#F7BE3A" : "#FFF0B3");
      root.style.setProperty("--glyph-r1", light ? "#E8920E" : "#FFD166");
      root.style.setProperty("--glyph-t0", light ? "#F29A2E" : "#FFB84A");
      root.style.setProperty("--glyph-t1", light ? "#F7BF48" : "#FFE29A");
    }
    if (customAccent) {
      root.style.setProperty("--liquid-bg", `linear-gradient(90deg, ${customAccent} 0%, ${customAccent}CC 50%, ${customAccent} 100%)`);
      root.style.setProperty("--liquid-glow", `0 0 10px ${customAccent}80`);
    }
    const centerFallback = !gameTitle && !customLogo ? colorMark("center-mark", "awake-center") : "";
    const heading = gameTitle ? `<p class="name">${escapeHtml(gameTitle)}</p>` : "";
    const corner = showAttribution
      ? `<div class="attribution"><span>Powered by</span>${colorMark("corner-mark", "awake-corner")}<strong>AwakeKt</strong></div>`
      : "";
    root.innerHTML = `<div class="box">${centerFallback}${heading}<p class="error" role="alert"></p></div>${corner}<div class="edge-track"><div class="bar"></div></div>`;
    if (customLogo) {
      const img = document.createElement("img");
      img.className = "custom-logo";
      img.alt = "";
      img.src = customLogo;
      img.addEventListener("error", () => img.remove(), { once: true });
      root.querySelector(".box")?.prepend(img);
    }
  } else {
    root.innerHTML = `<div class="box">${colorMark("mark", "awake-glyph")}
      <p class="name">AwakeKt${product ? ` <span>${escapeHtml(product)}</span>` : ""}</p>
      <div class="track"><div class="bar"></div></div>
      <div class="status"><span class="step">Starting…</span><span class="pct">0%</span></div>
      <p class="error" role="alert"></p></div>`;
  }
  const mount = () => { document.head.append(style); document.body.append(root); };
  document.body ? mount() : document.addEventListener("DOMContentLoaded", mount, { once: true });

  const bar = root.querySelector(".bar"), stepEl = root.querySelector(".step"), pctEl = root.querySelector(".pct");
  // A failure pauses the bar, but a frame on screen still dismisses the loader: an error the engine
  // logged and survived must not cover a working app.
  let shown = 0, finished = false, failedState = false;
  const progress = (value, step) => {
    if (finished || failedState) return;
    shown = Math.max(shown, Math.min(value, 100));
    if (gameMode) {
      bar.style.width = `${shown}%`;
    } else {
      bar.style.transform = `scaleX(${shown / 100})`;
    }
    if (pctEl) pctEl.textContent = `${Math.round(shown)}%`;
    root.setAttribute("aria-valuenow", String(Math.round(shown)));
    if (step && stepEl) stepEl.textContent = step;
  };
  const fail = (message) => {
    if (finished || failedState) return;
    failedState = true;
    root.classList.add("failed");
    if (stepEl) stepEl.textContent = "Couldn't start";
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
