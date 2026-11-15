// gc and a byte-exact heap size, for the lookup allocation gate's heap measurement.
config.customLaunchers = config.customLaunchers || {};
config.customLaunchers.AwakeHeapHeadless = {
  base: 'ChromeHeadless',
  flags: ['--js-flags=--expose-gc', '--enable-precise-memory-info'],
};
config.browsers = ['AwakeHeapHeadless'];
