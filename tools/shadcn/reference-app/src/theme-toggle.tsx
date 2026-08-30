import * as React from "react"
import { Moon, Sun } from "lucide-react"

import { Button } from "./ui/button"
import { Tooltip, TooltipContent, TooltipTrigger } from "./ui/tooltip"

/**
 * Flips the page between light and dark.
 *
 * Not a vendored shadcn component -- upstream has no theme toggle, because in its own docs the
 * site owns the theme and every framework does it differently. This is the reference app's own
 * chrome, kept beside the vendored files rather than inside them so the drift check (which
 * compares `src/ui/*.tsx` against the pinned registry) does not see a file the registry has no
 * counterpart for -- it lives in `src/`, not `src/ui/`, which is vendored territory.
 *
 * State is read from the DOM rather than held above, so this can be rendered anywhere -- including
 * inside a case's own top bar -- without threading a prop through the case map. `?theme=dark` still
 * decides the initial value, because that is what the capture passes and it must not depend on
 * anything a human clicked.
 */
export function ThemeToggle({ className }: { className?: string }) {
  const [dark, setDark] = React.useState(
    () => document.documentElement.classList.contains("dark"),
  )

  const toggle = () => {
    const next = !document.documentElement.classList.contains("dark")
    document.documentElement.classList.toggle("dark", next)
    document.documentElement.style.colorScheme = next ? "dark" : "light"
    setDark(next)
  }

  const label = dark ? "Switch to light theme" : "Switch to dark theme"
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          variant="outline"
          size="icon-sm"
          aria-label={label}
          onClick={toggle}
          className={className}
        >
          {dark ? <Sun /> : <Moon />}
        </Button>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  )
}
