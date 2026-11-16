- The Maven fallback site fits again (#1694). Its first deploy under the keep-by-reference rule stopped
  at 970 MB, over its 950 MB limit, because the Core releases its Vulkan releases name (0.50.0 and 0.51.0)
  were served whole. Now only the artifacts a served POM or Gradle module names are served from such a
  release: 163 artifacts, 76 MB, instead of the 141 MB bundle. On the v0.54.0 releases the site is
  716 MB rather than 950 MB, before the wgpu4k mirror. The size is checked when the site is assembled,
  printing what each release adds, as well as before deploying. A BOM's managed versions no longer count
  as artifacts the site must hold; only its import does.
