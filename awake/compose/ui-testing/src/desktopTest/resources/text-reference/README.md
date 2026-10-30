# Small-text reference renders

These PNGs are the reference `TextReferenceMetricsTest` holds Awake's small text against. They are
FreeType's rendering of the same labels `CrispTextEvidenceTest` draws: 11, 12 and 14 px, Roboto Regular
(`roboto-*`) and Roboto Medium as a project font (`ttf-*`), dark on light and light on dark.

They change only when `tools/fonts-tooling/reference_render.py` is deliberately rerun with its pinned
FreeType (`requirements-reference.txt`: freetype-py 2.5.1, FreeType 2.13.2), never to make a test pass.
A rerun writes the same bytes. The script's docstring records the settings: light hinting, stem darkening,
grayscale coverage, gamma-1.8 blending, and Awake's own pen positions and baseline.

```
python3 -m pip install -r tools/fonts-tooling/requirements-reference.txt
python3 tools/fonts-tooling/reference_render.py
```
