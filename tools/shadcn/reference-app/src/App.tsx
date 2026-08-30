import { useEffect } from "react"
import { CASES } from "./cases"
import { ThemeToggle } from "./theme-toggle"
import { TooltipProvider } from "./ui/tooltip"

/**
 * Applies `?theme=` before anything renders.
 *
 * At module scope, not in an effect. A child's `useState` initialiser runs before its parent's
 * effect, so `ThemeToggle` read the class while it was still unset, rendered "switch to dark"
 * over an already-dark page, and its first click then turned the theme off. Setting it here also
 * removes the light flash an effect leaves on the first paint.
 */
const initialParams = new URLSearchParams(window.location.search)
if (initialParams.get("theme") === "dark") {
  document.documentElement.classList.add("dark")
  document.documentElement.style.colorScheme = "dark"
}

/**
 * Renders exactly one reference case, selected by query string, with nothing else on the page.
 *
 * One case per load rather than a gallery: the capture screenshots `#case` and a gallery would
 * force the tiling that made the previous scraped references only as trustworthy as our own
 * arrangement choices.
 */
export function App() {
  const params = new URLSearchParams(window.location.search)
  const id = params.get("case") ?? "button-variants"
  const radius = params.get("radius")
  // Theme is already applied above. Radius is not a thing anything toggles, so an effect is fine.
  useEffect(() => {
    if (radius) document.documentElement.style.setProperty("--radius", radius)
  }, [radius])

  const entry = CASES[id]
  if (!entry) {
    return <div id="case" data-error="unknown-case">{`unknown case: ${id}`}</div>
  }
  return (
    <>
      {/*
        Outside `#case`, deliberately. The capture screenshots that element, so anything rendered
        in it becomes reference pixels Awake has no counterpart for. This is page chrome for a
        human comparing themes by eye, and the capture never sees it.

        Skipped for a case that draws its own -- a full-screen shell has a top bar to put one in,
        and two controls writing the same class is one control too many.
      */}
      {!entry.ownsThemeToggle && (
        <TooltipProvider delayDuration={300}>
          <div className="fixed top-2 right-2 z-50">
            <ThemeToggle />
          </div>
        </TooltipProvider>
      )}
      {/*
        `w-fit` with no padding: the captured element must be exactly the component, since any
        wrapper padding is 16px of reference that the Awake preview has no counterpart for and
        would show up as a fidelity difference it isn't.
      */}
      <div id="case" className="w-fit" data-case={id}>
        {entry.render()}
      </div>
    </>
  )
}
