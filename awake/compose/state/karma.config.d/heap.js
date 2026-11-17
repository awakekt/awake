// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0
//
// gc and a byte-exact heap size, for the selected-state poll test (#1512). A fixed
// 128 MB young generation holds a whole measured window, so no scavenge runs inside one and heap
// growth is what the window allocated, not what survived the collections it set off.
config.customLaunchers = config.customLaunchers || {};
config.customLaunchers.AwakeHeapHeadless = {
  base: 'ChromeHeadless',
  flags: ['--js-flags=--expose-gc --min-semi-space-size=128 --max-semi-space-size=128', '--enable-precise-memory-info'],
};
config.browsers = ['AwakeHeapHeadless'];
