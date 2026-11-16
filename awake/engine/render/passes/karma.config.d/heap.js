// gc and a byte-exact heap size, for the compile heap breakdown's measurement (#1621).
config.customLaunchers = config.customLaunchers || {};
config.customLaunchers.AwakeHeapHeadless = {
  base: 'ChromeHeadless',
  flags: ['--js-flags=--expose-gc', '--enable-precise-memory-info'],
};
config.browsers = ['AwakeHeapHeadless'];
