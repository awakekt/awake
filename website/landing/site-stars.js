/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
(() => {
  const targets = Array.from(document.querySelectorAll("[data-gh-stars]"));
  if (targets.length === 0) return;

  const fmt = (n) =>
    n < 1000
      ? String(n)
      : n < 10000
        ? `${(Math.floor(n / 100) / 10).toFixed(1).replace(/\.0$/, "")}k`
        : `${Math.floor(n / 1000)}k`;

  const applyCount = (count) => {
    const label = fmt(count);
    for (const el of targets) {
      el.textContent = label;
      const link = el.closest("a");
      if (link) link.setAttribute("aria-label", `Star AwakeKt Engine on GitHub (${count} stars)`);
    }
  };

  try {
    const cached = JSON.parse(sessionStorage.getItem("awake.github.stars.v1") || "null");
    if (cached && typeof cached.count === "number" && Date.now() - cached.at < 600000) {
      applyCount(cached.count);
      return;
    }
  } catch {}

  fetch("https://api.github.com/repos/awakekt/awake", {
    headers: { Accept: "application/vnd.github+json" },
  })
    .then((r) => (r.ok ? r.json() : null))
    .then((d) => {
      if (d && typeof d.stargazers_count === "number") {
        applyCount(d.stargazers_count);
        try {
          sessionStorage.setItem(
            "awake.github.stars.v1",
            JSON.stringify({ count: d.stargazers_count, at: Date.now() }),
          );
        } catch {}
      }
    })
    .catch(() => {});
})();
