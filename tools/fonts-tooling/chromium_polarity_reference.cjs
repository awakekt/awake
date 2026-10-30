#!/usr/bin/env node
// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0
//
// GENERATOR: writes the Chromium renders awake:compose:ui-testing measures text weight against.
// Kinds are defined in tools/README.md. Only a GATE can fail a build; visual tooling tests cover
// the helper itself.
/*
 * Renders the Chromium text-polarity references consumed by TextPolarityWeightTest and
 * TextProofSpecimenTest (#1003, #1064):
 *
 * 1. Single-line unkerned Roboto Regular labels at 9 to 48 px, dark on light and light on dark, at
 *    device scale 1 (`<polarity>-<size>.png`), held by TextPolarityWeightTest.
 * 2. The multi-band text proof specimen page (9 to 48 px size ramp with kerning, Roboto weights 300
 *    to 900, runtime-loaded project TTF at pixel-glyph and distance-field sizes, wrapped paragraph,
 *    and HUD readout) in both polarities at device scales 1 and 1.5
 *    (`proof-specimen-<polarity>-<scale>.png`), held band by band by TextProofSpecimenTest.
 *
 * All fonts come from awake/ui/font-atlas-generator/fonts/ and are embedded as data URLs so Chromium
 * draws the exact repository faces.
 *
 * Must run on Linux: Chromium draws grayscale text on Linux through Skia with mask gamma 1.2 and
 * contrast 0.2 (the rule Awake's glyph shader and CPU raster match), whereas Windows Chromium uses
 * DirectWrite's gamma-2.2 mask table and draws light-on-dark text up to 38% heavier at small sizes.
 * The Chromium version used is written to chromium.txt beside the images. Rerunning with the same
 * Linux Chromium writes the same bytes; never rerun merely to make a failing test pass.
 *
 * Usage (on Linux, with Playwright's Node package and its Chromium installed):
 *     node tools/fonts-tooling/chromium_polarity_reference.cjs
 */
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');

if (process.platform !== 'linux') {
  console.error(
    `chromium_polarity_reference.cjs must run on Linux (got ${process.platform}): ` +
      'Awake matches Linux Chromium/Skia mask gamma (1.2, contrast 0.2), whereas Windows DirectWrite uses gamma 2.2.'
  );
  process.exit(1);
}

const REPO = path.resolve(__dirname, '..', '..');
const FONT_DIR = path.join(REPO, 'awake/ui/font-atlas-generator/fonts');
const FONT = path.join(FONT_DIR, 'Roboto-Regular.ttf');
const OUT_DIR = path.join(REPO, 'awake/compose/ui-testing/src/desktopTest/resources/text-reference/chromium');

const LABEL = 'Mesh Terrain Texture Illi 0123';
const SIZES = [9, 10, 11, 12, 13, 14, 16, 18, 20, 22, 24, 25, 28, 32, 36, 40, 48];
const DARK = '#17171c';
const LIGHT = '#f5f5f7';
const POLARITIES = { 'dark-on-light': [DARK, LIGHT], 'light-on-dark': [LIGHT, DARK] };
const PADDING = 4;

const SPECIMEN_WIDTH_DP = 520;
const SPECIMEN_MARGIN_DP = 12;
const SPECIMEN_GAP_DP = 4;
const SPECIMEN_CONTENT_WIDTH_DP = SPECIMEN_WIDTH_DP - 2 * SPECIMEN_MARGIN_DP;
const SPECIMEN_DARK = '#171717';
const SPECIMEN_LIGHT = '#f5f5f5';
const SPECIMEN_POLARITIES = {
  'dark-on-light': [SPECIMEN_DARK, SPECIMEN_LIGHT],
  'light-on-dark': [SPECIMEN_LIGHT, SPECIMEN_DARK],
};
const SPECIMEN_SCALES = [1, 1.5];
const SPECIMEN_WEIGHT_FILES = {
  300: 'Roboto-Light.ttf',
  400: 'Roboto-Regular.ttf',
  500: 'Roboto-Medium.ttf',
  600: 'Roboto-SemiBold.ttf',
  700: 'Roboto-Bold.ttf',
  900: 'Roboto-Black.ttf',
};

/** Must match TextProofSpecimenTest.BANDS in order, geometry, font, weight, wrapping, and text. */
const SPECIMEN_BANDS = [
  ['size-9', 9, 400, 'AwakeRoboto', 18, false, '9 px · Harbor Watch — Mesh Terrain Texture Illi 0123'],
  ['size-10', 10, 400, 'AwakeRoboto', 20, false, '10 px · Harbor Watch — Mesh Terrain Texture Illi 0123'],
  ['size-11', 11, 400, 'AwakeRoboto', 20, false, '11 px · Harbor Watch — Mesh Terrain Texture Illi 0123'],
  ['size-12', 12, 400, 'AwakeRoboto', 22, false, '12 px · Harbor Watch — Mesh Terrain Texture Illi 0123'],
  ['size-14', 14, 400, 'AwakeRoboto', 24, false, '14 px · Harbor Watch — Mesh Terrain Texture Illi 0123'],
  ['size-16', 16, 400, 'AwakeRoboto', 28, false, '16 px · Harbor Watch — Mesh Terrain Texture Illi 0123'],
  ['size-18', 18, 400, 'AwakeRoboto', 30, false, '18 px · Harbor Watch — Mesh Terrain Texture Illi 0123'],
  ['size-24', 24, 400, 'AwakeRoboto', 38, false, '24 px · Harbor Watch — Illi 0123'],
  ['size-32', 32, 400, 'AwakeRoboto', 48, false, '32 px · Harbor Watch 0123'],
  ['size-48', 48, 400, 'AwakeRoboto', 68, false, '48 px · Harbor 0123'],
  ['weight-300-14', 14, 300, 'AwakeRoboto', 24, false, 'Light 300 · Harbor Watch — Mesh Terrain 0123'],
  ['weight-500-14', 14, 500, 'AwakeRoboto', 24, false, 'Medium 500 · Harbor Watch — Mesh Terrain 0123'],
  ['weight-600-14', 14, 600, 'AwakeRoboto', 24, false, 'SemiBold 600 · Harbor Watch — Mesh Terrain 0123'],
  ['weight-700-14', 14, 700, 'AwakeRoboto', 24, false, 'Bold 700 · Harbor Watch — Mesh Terrain 0123'],
  ['weight-900-14', 14, 900, 'AwakeRoboto', 24, false, 'Black 900 · Harbor Watch — Mesh Terrain 0123'],
  ['weight-700-28', 28, 700, 'AwakeRoboto', 42, false, 'Bold 700 · 28 px Harbor Watch 0123'],
  ['ttf-12', 12, 400, 'AwakeProjectMedium', 22, false, 'Project TTF 12 px · Harbor Watch — Mesh Terrain 0123'],
  ['ttf-32', 32, 400, 'AwakeProjectMedium', 48, false, 'Project TTF 32 px · Harbor 0123'],
  [
    'paragraph-13',
    13,
    400,
    'AwakeRoboto',
    40,
    true,
    'Harbor watch reports clear skies over the northern ridge. Steady winds hold at twelve knots while patrol lanterns mark the stone pier and outer channel.',
  ],
  ['hud-15', 15, 500, 'AwakeRoboto', 26, false, 'HP 87/100 · Shield 64% · Stamina 92 · 12.5 ms'],
];

const SPECIMEN_HEIGHT_DP =
  2 * SPECIMEN_MARGIN_DP +
  SPECIMEN_BANDS.reduce((sum, band) => sum + band[4], 0) +
  SPECIMEN_GAP_DP * (SPECIMEN_BANDS.length - 1);

/** The image size for [size]: room for the label at about 0.5 em a character, and a 1.6 em line. */
function imageSize(size) {
  return [Math.ceil(size * 16.5) + 2 * PADDING, Math.floor(size * 1.6) + 2 * PADDING];
}

function specimenFontFacesCss() {
  const rules = [];
  for (const [weight, fileName] of Object.entries(SPECIMEN_WEIGHT_FILES)) {
    const data = fs.readFileSync(path.join(FONT_DIR, fileName)).toString('base64');
    rules.push(
      `@font-face { font-family: AwakeRoboto; font-weight: ${weight}; src: url(data:font/ttf;base64,${data}) format('truetype'); }`
    );
  }
  const projectMedium = fs.readFileSync(path.join(FONT_DIR, 'Roboto-Medium.ttf')).toString('base64');
  rules.push(
    `@font-face { font-family: AwakeProjectMedium; font-weight: 400; src: url(data:font/ttf;base64,${projectMedium}) format('truetype'); }`
  );
  return rules.join('\n        ');
}

async function renderSingleLineReferences(browser) {
  const font = fs.readFileSync(FONT).toString('base64');
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
  await page.close();
}

async function renderProofSpecimens(browser) {
  const fontFaces = specimenFontFacesCss();
  for (const scale of SPECIMEN_SCALES) {
    const scaleName = scale === 1 ? '1x' : `${scale}x`;
    const page = await browser.newPage({ deviceScaleFactor: scale });
    await page.setViewportSize({ width: SPECIMEN_WIDTH_DP, height: SPECIMEN_HEIGHT_DP });
    for (const [polarity, [ink, ground]] of Object.entries(SPECIMEN_POLARITIES)) {
      const bandsHtml = SPECIMEN_BANDS.map(([, sizeSp, weight, family, heightDp, wrap, text]) => {
        const cssSize = Math.round(sizeSp * scale) / scale;
        const whiteSpace = wrap ? 'normal' : 'pre';
        return `<div class="band" style="height:${heightDp}px;font-family:${family};font-weight:${weight};font-size:${cssSize}px;line-height:normal;white-space:${whiteSpace};">${text}</div>`;
      }).join('');
      await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>
        ${fontFaces}
        html, body { margin: 0; padding: 0; background: ${ground}; }
        .sheet { padding: ${SPECIMEN_MARGIN_DP}px; display: flex; flex-direction: column; gap: ${SPECIMEN_GAP_DP}px; width: ${SPECIMEN_CONTENT_WIDTH_DP}px; }
        .band { width: ${SPECIMEN_CONTENT_WIDTH_DP}px; color: ${ink}; overflow: hidden; font-kerning: normal; font-variant-ligatures: none; }
      </style></head><body><div class="sheet">${bandsHtml}</div></body></html>`);
      await page.evaluate(() => document.fonts.ready);
      for (const weight of Object.keys(SPECIMEN_WEIGHT_FILES)) {
        if (!(await page.evaluate((w) => document.fonts.check(`${w} 14px AwakeRoboto`), weight))) {
          throw new Error(`Chromium did not load AwakeRoboto weight ${weight}`);
        }
      }
      if (!(await page.evaluate(() => document.fonts.check('12px AwakeProjectMedium')))) {
        throw new Error('Chromium did not load AwakeProjectMedium');
      }
      const file = path.join(OUT_DIR, `proof-specimen-${polarity}-${scaleName}.png`);
      await page.screenshot({
        path: file,
        clip: { x: 0, y: 0, width: SPECIMEN_WIDTH_DP, height: SPECIMEN_HEIGHT_DP },
      });
      console.log(path.relative(REPO, file));
    }
    await page.close();
  }
}

async function main() {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const fontconfigFile = path.join(os.tmpdir(), `awake-chromium-fontconfig-${process.pid}.conf`);
  fs.writeFileSync(
    fontconfigFile,
    `<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">
<fontconfig>
  <include ignore_missing="yes">/etc/fonts/fonts.conf</include>
  <match target="font">
    <edit name="antialias" mode="assign"><bool>true</bool></edit>
    <edit name="hinting" mode="assign"><bool>false</bool></edit>
    <edit name="hintstyle" mode="assign"><const>hintnone</const></edit>
    <edit name="rgba" mode="assign"><const>none</const></edit>
  </match>
</fontconfig>
`
  );
  try {
    const browser = await chromium.launch({
      args: ['--font-render-hinting=none'],
      env: {
        ...process.env,
        FONTCONFIG_FILE: fontconfigFile,
        FREETYPE_PROPERTIES: 'cff:no-stem-darkening=1 autofitter:no-stem-darkening=1',
      },
    });
    await renderSingleLineReferences(browser);
    await renderProofSpecimens(browser);
    fs.writeFileSync(path.join(OUT_DIR, 'chromium.txt'), `Chromium ${browser.version()}\n`);
    await browser.close();
  } finally {
    fs.rmSync(fontconfigFile, { force: true });
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
