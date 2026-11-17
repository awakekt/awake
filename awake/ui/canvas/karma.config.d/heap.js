// SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
//
// SPDX-License-Identifier: Apache-2.0
//
// gc and a byte-exact heap size, for the canvas revision heap test (#1512).
config.customLaunchers = config.customLaunchers || {};
config.customLaunchers.AwakeHeapHeadless = {
  base: 'ChromeHeadless',
  flags: ['--js-flags=--expose-gc', '--enable-precise-memory-info'],
};
config.browsers = ['AwakeHeapHeadless'];
