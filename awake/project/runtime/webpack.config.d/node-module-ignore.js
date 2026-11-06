// Kotlin/Wasm emits a Node-only fallback that imports node:module. The branch is
// unreachable in a browser, but webpack still parses the URI unless it is ignored.
config.plugins.push(new (require('webpack')).IgnorePlugin({
    resourceRegExp: /^node:/
}));
