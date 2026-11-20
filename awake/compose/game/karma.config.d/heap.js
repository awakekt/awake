// gc and a byte-exact heap size, for the HUD measure-scope heap measurement (#1621).
config.customLaunchers = config.customLaunchers || {};
config.customLaunchers.AwakeHeapHeadless = {
  base: 'ChromeHeadless',
  flags: ['--js-flags=--expose-gc', '--enable-precise-memory-info'],
};
config.browsers = ['AwakeHeapHeadless'];
