// WebGPU validation tests run against the browser driver, including software adapters in CI.
config.customLaunchers = config.customLaunchers || {};
config.customLaunchers.AwakeWebGpuHeadless = {
  base: 'ChromeHeadless',
  flags: ['--enable-unsafe-webgpu', '--use-webgpu-adapter=swiftshader'],
};
config.browsers = ['AwakeWebGpuHeadless'];
