# Welcome to Awake Studio

Studio is the reference host for the Awake editor. Everything in this window --
the hierarchy, the inspector, the dock -- is a library panel, not Studio code.

## The file panel

The Files tab lists what Studio bundles and previews it with whichever viewer
claims the extension. Studio ships a plain-text viewer, so `.json` and `.txt`
open. This file does not: nothing registered claims `.md`.

That gap is the point. Register an `EditorFileViewer` that handles `md` and this
document renders instead -- the same seam an editor add-on would use.
