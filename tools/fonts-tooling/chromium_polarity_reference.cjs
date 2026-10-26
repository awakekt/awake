#!/usr/bin/env node
// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0
//
// GENERATOR: writes the Chromium renders awake:compose:ui-testing measures text weight against.
// Kinds are defined in tools/README.md. Only a GATE can fail a build; visual tooling tests cover
// the helper itself.
/*
 * Renders the text-polarity reference: one label in Roboto Regular, dark on light and light on dark,
 * at every size TextPolarityWeightTest checks, 9 to 48 px, in headless Chromium at device scale 1.
 * The test compares the ink Awake draws with the ink Chromium draws, per size and polarity, so the
 * text coverage rule (UiRasterizer.textCoverageAlpha, AslUiShaders.UiGlyphShader) is held to a
 * renderer most readers already trust, in both polarities (#1003, #1064).
 *
 * The font is the face the built-in family is made from (awake/ui/font-atlas-generator/fonts/
 * Roboto-Regular.ttf), embedded as a data URL so Chromium draws exactly it. Kerning is off, as the
 * test lays the label out unkerned; the ink a label holds barely depends on where its glyphs fall,
 * so the two layouts need not match pixel for pixel. The images, the label, the sizes and the two
 * colours must match TextPolarityWeightTest's.
 *
 * Chromium draws grayscale text on Linux through Skia with its mask gamma 1.2 and contrast 0.2; the
 * version used is written to chromium.txt beside the images. Rerunning with the same Chromium writes
 * the same pixels. Don't rerun it to make the test pass: the reference is the fixed point the rule is
 * judged against.
 *
 * Usage (Playwright's Node package and its Chromium installed):
 *     node tools/fonts-tooling/chromium_polarity_reference.cjs
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const REPO = path.resolve(__dirname, '..', '..');
const FONT = path.join(REPO, 'awake/ui/font-atlas-generator/fonts/Roboto-Regular.ttf');
const OUT_DIR = path.join(REPO, 'awake/compose/ui-testing/src/desktopTest/resources/text-reference/chromium');

const LABEL = 'Mesh Terrain Texture Illi 0123';
const SIZES = [9, 10, 11, 12, 13, 14, 16, 18, 20, 22, 24, 25, 28, 32, 36, 40, 48];
const DARK = '#17171c';
const LIGHT = '#f5f5f7';
const POLARITIES = { 'dark-on-light': [DARK, LIGHT], 'light-on-dark': [LIGHT, DARK] };
const PADDING = 4;

/** The image size for [size]: room for the label at about 0.5 em a character, and a 1.6 em line. */
function imageSize(size) {
  return [Math.ceil(size * 16.5) + 2 * PADDING, Math.floor(size * 1.6) + 2 * PADDING];
}

async function main() {
  const font = fs.readFileSync(FONT).toString('base64');
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: 1 });
  for (const size of SIZES) {
    for (const [name, [ink, ground]] of Object.entries(POLARITIES)) {
      const [width, height] = imageSize(size);
      await page.setViewportSize({ width, height });
      await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>
        @font-face { font-family: AwakeRoboto; src: url(data:font/ttf;base64,${font}) format('truetype'); }
        html, body { margin: 0; padding: 0; background: ${ground}; }
        div { font-family: AwakeRoboto; font-size: ${size}px; line-height: ${Math.floor(size * 1.6)}px;
              color: ${ink}; padding: ${PADDING}px; white-space: pre; font-kerning: none;
              font-variant-ligatures: none; }
      </style></head><body><div>${LABEL}</div></body></html>`);
      await page.evaluate(() => document.fonts.ready);
      if (!(await page.evaluate(() => document.fonts.check('12px AwakeRoboto')))) {
        throw new Error('Chromium did not load the font');
      }
      const file = path.join(OUT_DIR, `${name}-${size}.png`);
      await page.screenshot({ path: file, clip: { x: 0, y: 0, width, height } });
      console.log(path.relative(REPO, file));
    }
  }
  fs.writeFileSync(path.join(OUT_DIR, 'chromium.txt'), `Chromium ${browser.version()}\n`);
  await browser.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
