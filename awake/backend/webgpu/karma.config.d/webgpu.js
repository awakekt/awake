// WebGPU validation tests run against the browser driver, including software adapters in CI.
// Chrome rather than ChromeHeadless: that launcher adds --disable-gpu, which leaves macOS with no
// WebGPU adapter at all. New headless keeps the GPU, and the swiftshader flag picks the software
// adapter where one is installed.
config.customLaunchers = config.customLaunchers || {};
config.customLaunchers.AwakeWebGpuHeadless = {
  base: 'Chrome',
  flags: ['--headless=new', '--enable-unsafe-webgpu', '--use-webgpu-adapter=swiftshader'],
};
config.browsers = ['AwakeWebGpuHeadless'];
// A frame of a few thousand draws, timed over many frames, outlasts mocha's 2 s default.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 120000 });
