// Baking a loaded font's full default set (UiFonts.DEFAULT_CHARS, 339 chars) takes seconds in
// headless Chrome, past mocha's 2 s default, and the test then fails with a bare "Error".
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 60000 });
