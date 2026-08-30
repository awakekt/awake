import * as React from "react"
import type { ReactNode } from "react"
import { cn } from "./lib/utils"
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyTitle } from "./ui/empty"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "./ui/resizable"
import { Button } from "./ui/button"
import { Badge } from "./ui/badge"
import { Checkbox } from "./ui/checkbox"
import { RadioGroup, RadioGroupItem } from "./ui/radio-group"
import { Switch } from "./ui/switch"
import { Progress } from "./ui/progress"
import { Input } from "./ui/input"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "./ui/tabs"
import { Slider } from "./ui/slider"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select"
import { Card, CardContent, CardHeader, CardTitle } from "./ui/card"
import { Label } from "./ui/label"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "./ui/tooltip"
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "./ui/dialog"
import { Textarea } from "./ui/textarea"
import { Toggle } from "./ui/toggle"
import { Alert, AlertTitle, AlertDescription } from "./ui/alert"
import { Avatar, AvatarFallback } from "./ui/avatar"
import { Breadcrumb, BreadcrumbList, BreadcrumbItem, BreadcrumbLink, BreadcrumbPage, BreadcrumbSeparator } from "./ui/breadcrumb"
import { Collapsible, CollapsibleTrigger, CollapsibleContent } from "./ui/collapsible"
import { Kbd } from "./ui/kbd"
import { Skeleton } from "./ui/skeleton"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "./ui/dropdown-menu"
import { Popover, PopoverContent, PopoverTrigger } from "./ui/popover"
import { ButtonGroup, ButtonGroupSeparator } from "./ui/button-group"
import { ToggleGroup, ToggleGroupItem } from "./ui/toggle-group"
import { ThemeToggle } from "./theme-toggle"
import { Separator } from "./ui/separator"
import {
  Field, FieldContent, FieldDescription, FieldError, FieldGroup, FieldLabel, FieldLegend,
  FieldSeparator, FieldSet, FieldTitle,
} from "./ui/field"
import {
  Table, TableBody, TableCaption, TableCell, TableFooter, TableHead, TableHeader, TableRow,
} from "./ui/table"
// Glyphs are lucide, which is what shadcn's own docs use; Awake draws heroicons. Compare the
// button box, not the artwork -- an icon button with no child collapses to nothing, and an empty
// square would read as a layout difference that is only a missing glyph.
import {
  Box as BoxIcon, Camera, ChevronDown, ChevronRight, CircleX, CloudSun, Compass, Eraser, Eye,
  EyeOff, Frame, Grid2x2, Grid3x3, Layers, LayoutGrid, Lightbulb, List, MoreVertical,
  MousePointer2, Move, Pause, Play, Plus, Repeat, RotateCw, Redo2, Save, Search, Shrink,
  SkipBack, Sun, TriangleAlert, Trash2, Undo2, Video,
} from "lucide-react"

/**
 * A [ToggleGroupItem] that names itself on hover.
 *
 * Every control in the viewport overlay is icon-only, which is what got the strip from 790px down
 * to three small pills -- but an unlabelled glyph is only readable to someone who already knows it.
 * `aria-label` covers a screen reader and covers nothing for a sighted user. The tooltip is the
 * other half, and it carries the same string, so the two cannot drift.
 *
 * `asChild` on the trigger: Radix's ToggleGroup.Item is already a button, and nesting one inside a
 * tooltip's own button is invalid HTML that breaks arrow-key roving focus in the group.
 */
function ToggleItem({
  label,
  children,
  ...props
}: React.ComponentProps<typeof ToggleGroupItem> & { label: string }) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <ToggleGroupItem aria-label={label} {...props}>
          {children}
        </ToggleGroupItem>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  )
}

/** The same, for the two plain buttons -- the camera pair is actions, not state. */
function IconAction({
  label,
  children,
  ...props
}: React.ComponentProps<typeof Button> & { label: string }) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button variant="outline" size="icon-sm" aria-label={label} {...props}>
          {children}
        </Button>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  )
}

/**
 * A vertical [ToggleGroup].
 *
 * Upstream has no vertical variant for this one -- `ButtonGroup` has an `orientation` prop and
 * `ToggleGroup` does not, so its root is `flex … items-center` and its items round their left and
 * right ends. Radix's own `orientation` prop only moves keyboard navigation; it draws nothing.
 * Rather than fork the vendored file, which the drift check compares against the pin byte for byte,
 * the corrections live here at the call site: stack the root, and re-point the item rounding and
 * the collapsed border from the horizontal axis to the vertical one.
 *
 * The overrides repeat upstream's `data-[spacing=0]:` prefix deliberately. tailwind-merge resolves
 * conflicts within a variant, so a bare `rounded-none` would not replace a
 * `data-[spacing=0]:rounded-none` -- both would ship and the cascade would decide.
 */
function VerticalToggleGroup({
  children,
  ...props
}: React.ComponentProps<typeof ToggleGroup>) {
  return (
    <ToggleGroup {...props} className={cn("flex-col", props.className)}>
      {React.Children.map(children, (child) =>
        React.isValidElement<React.ComponentProps<typeof ToggleGroupItem>>(child)
          ? React.cloneElement(child, {
              className: cn(
                // Corner utilities, not `rounded-t-md`/`rounded-l-none`. tailwind-merge only
                // resolves a conflict it can see: `rounded-l-none` and `rounded-t-md` are
                // different groups, so both shipped and the cascade gave the first item a square
                // top-left inside a rounded pill. Naming all four corners leaves nothing to the
                // stylesheet's order.
                "data-[spacing=0]:first:rounded-tl-md data-[spacing=0]:first:rounded-tr-md",
                "data-[spacing=0]:first:rounded-bl-none data-[spacing=0]:first:rounded-br-none",
                "data-[spacing=0]:last:rounded-bl-md data-[spacing=0]:last:rounded-br-md",
                "data-[spacing=0]:last:rounded-tl-none data-[spacing=0]:last:rounded-tr-none",
                "data-[spacing=0]:data-[variant=outline]:border-l",
                "data-[spacing=0]:data-[variant=outline]:border-t-0",
                "data-[spacing=0]:data-[variant=outline]:first:border-t",
                child.props.className,
              ),
            })
          : child,
      )}
    </ToggleGroup>
  )
}

/**
 * One row of the scene tree.
 *
 * `depth` indents rather than nesting: a nested `<div>` per level would make the hover and
 * selection background stop at the indent instead of spanning the panel, which is the one thing a
 * tree row's background is for -- it tells you how far the row you are pointing at reaches.
 *
 * The visibility eye is rendered for every row and hidden until hover or selection. Reserving its
 * box unconditionally keeps names from shifting sideways as the pointer travels down the list; it
 * was the eye appearing that moved them.
 */
function TreeRow({
  icon,
  name,
  depth = 0,
  selected = false,
  hidden = false,
  expanded,
}: {
  icon: ReactNode
  name: string
  depth?: number
  selected?: boolean
  hidden?: boolean
  expanded?: boolean
}) {
  return (
    <div
      className={cn(
        "group flex h-7 w-full items-center gap-1.5 rounded-md pr-1 text-sm",
        "hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
        selected && "bg-sidebar-accent text-sidebar-accent-foreground",
      )}
      style={{ paddingLeft: `${depth * 12 + 4}px` }}
    >
      {/* A leaf still spends the chevron's width, so names line up down a mixed level. */}
      <span className="flex size-4 shrink-0 items-center justify-center text-muted-foreground">
        {expanded === undefined ? null : expanded ? <ChevronDown className="size-3.5" /> : <ChevronRight className="size-3.5" />}
      </span>
      <span className="flex size-4 shrink-0 items-center justify-center text-muted-foreground [&_svg]:size-3.5">
        {icon}
      </span>
      <span className={cn("flex-1 truncate", hidden && "text-muted-foreground line-through")}>{name}</span>
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label={hidden ? `Show ${name}` : `Hide ${name}`}
        className={cn("size-5 opacity-0 group-hover:opacity-100", selected && "opacity-100")}
      >
        {hidden ? <EyeOff /> : <Eye />}
      </Button>
    </div>
  )
}

/**
 * One inspector component section: a header that collapses it, and a menu for what to do to it.
 *
 * `Collapsible`, not a hand-rolled open flag -- the header is a button with the right ARIA state
 * and keyboard behaviour already, and the inspector has one of these per component on the entity.
 */
function InspectorSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Collapsible defaultOpen className="border-b last:border-b-0">
      <div className="flex h-8 items-center gap-1 pr-1">
        <CollapsibleTrigger className="group flex flex-1 items-center gap-1.5 text-left text-sm font-medium">
          <ChevronDown className="size-3.5 text-muted-foreground transition-transform group-data-[state=closed]:-rotate-90" />
          {title}
        </CollapsibleTrigger>
        <Button variant="ghost" size="icon-sm" className="size-6" aria-label={`${title} options`}>
          <MoreVertical />
        </Button>
      </div>
      <CollapsibleContent className="flex flex-col gap-2 pb-2">{children}</CollapsibleContent>
    </Collapsible>
  )
}

/**
 * A three-axis transform row.
 *
 * The label sits above rather than beside its three fields. Beside, at this panel width, the fields
 * get about 48px each -- narrower than the six characters a rotation in degrees needs, so every
 * value truncates. The label owns a line because the numbers cannot afford to share one.
 *
 * `tabular-nums` so a digit changing does not re-lay-out the row underneath a drag.
 */
function VectorRow({ label, value }: { label: string; value: [string, string, string] }) {
  return (
    <div className="flex flex-col gap-1">
      <Label className="text-xs font-normal text-muted-foreground">{label}</Label>
      <div className="flex items-center gap-1">
        {value.map((axis, index) => (
          <Input
            key={index}
            defaultValue={axis}
            aria-label={`${label} ${"XYZ"[index]}`}
            className="h-7 px-2 text-xs tabular-nums"
          />
        ))}
      </div>
    </div>
  )
}

/** A labelled control on one line, for the single-value fields a vector row is overkill for. */
function InspectorField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center gap-2">
      <Label className="w-16 shrink-0 text-xs font-normal text-muted-foreground">{label}</Label>
      <div className="flex-1 min-w-0">{children}</div>
    </div>
  )
}

/**
 * One console line: timestamp, severity glyph, message.
 *
 * Three columns, all fixed-width but the message. A log is read by scanning down one column at a
 * time -- every timestamp, or every error -- and a line that starts where the previous one ended
 * cannot be scanned at all. `tabular-nums` on the clock for the same reason: 12:47:11 must occupy
 * exactly what 12:47:02 did.
 *
 * The glyph column is spent even by an info line. Reserving it costs 16px once; not reserving it
 * costs every message its left edge the moment a warning appears above it.
 *
 * Only `error` gets a palette colour. `warn` goes to full-strength foreground against info's muted
 * -- the theme has one semantic colour and it is `destructive`, so inventing an amber here would
 * be a token this design system does not have and could not theme.
 */
function LogLine({
  time,
  level = "info",
  children,
  indent = false,
}: {
  time: string
  level?: "info" | "warn" | "error"
  children: ReactNode
  indent?: boolean
}) {
  const Glyph = level === "error" ? CircleX : level === "warn" ? TriangleAlert : null
  return (
    <div
      className={cn(
        "flex items-start gap-2",
        level === "error" && "text-destructive",
        level === "warn" && "text-foreground",
        level === "info" && "text-muted-foreground",
      )}
    >
      <span className="shrink-0 tabular-nums text-muted-foreground">{time}</span>
      <span className="flex w-4 shrink-0 justify-center pt-1">{Glyph && <Glyph className="size-3" />}</span>
      <span className={cn("min-w-0 flex-1", indent && "pl-4")}>{children}</span>
    </div>
  )
}

/**
 * One clip on the timeline.
 *
 * Percentages, not pixels: the track scales with the dock, and a bar positioned in pixels detaches
 * from the ruler above it the moment anything is dragged.
 */
function TimelineClip({
  name,
  start,
  end,
  selected = false,
}: {
  name: string
  start: number
  end: number
  selected?: boolean
}) {
  return (
    <div className="relative h-7 border-b border-border/50">
      <div
        className={cn(
          "absolute inset-y-1 flex items-center rounded-sm border px-2 text-xs",
          selected ? "border-primary bg-primary/20 text-foreground" : "border-border bg-muted text-muted-foreground",
        )}
        style={{ left: `${start}%`, width: `${end - start}%` }}
      >
        <span className="truncate">{name}</span>
      </div>
    </div>
  )
}

/**
 * One asset in the browser grid.
 *
 * A tile, not a list row. The dock is short and wide -- about 170px of usable height under its
 * toolbar -- so a vertical list shows four assets and a grid shows a dozen, and an asset is
 * identified by what it looks like before it is identified by its name.
 *
 * The thumbnail is a plain muted box. Awake fills it from a provider-owned offscreen render, so
 * what the reference owes the port is the tile's footprint and the ratio, not the picture.
 */
function AssetTile({
  name,
  kind,
  selected = false,
}: {
  name: string
  kind: string
  selected?: boolean
}) {
  return (
    <button
      className={cn(
        "flex w-24 shrink-0 flex-col gap-1 rounded-md border p-1 text-left transition-colors",
        selected ? "border-primary bg-accent" : "border-transparent hover:bg-accent/50",
      )}
    >
      <div className="aspect-square w-full rounded-sm border bg-muted" />
      <span className="truncate text-xs" title={name}>{name}</span>
      <Badge variant="outline" className="w-fit px-1 py-0 text-[10px] leading-4">{kind}</Badge>
    </button>
  )
}

/**
 * The bottom dock: three streams behind one tab strip.
 *
 * One toolbar row, then the panel. It was three stacked rows -- tabs, then counts with a `Clear`
 * pushed to the far right, then the log -- so `Clear` sat on its own line, vertically centred
 * against nothing and horizontally an entire panel away from the tabs it acts on. Tabs and their
 * controls belong on the same line because they describe the same thing: which stream, and what is
 * in it.
 *
 * Stateful, unlike every other case in this file, and controlled rather than `defaultValue`. Two
 * reasons. The toolbar's right-hand half is per-stream -- error counts for the console, transport
 * for the timeline, search for the assets -- and it lives in the tab strip's row rather than in
 * each panel, so it cannot be a `TabsContent`; it has to be chosen from the active value. And a
 * reference whose other two panels can only be seen by editing the source is a reference for one
 * panel.
 *
 * Every panel gets `min-h-0 overflow-auto`: `flex-1` alone does not stop a flex child growing past
 * its container, and all three of these are lists with no natural bound.
 */
function StudioDock() {
  const [tab, setTab] = React.useState("console")
  return (
    <Tabs value={tab} onValueChange={setTab} className="flex h-full flex-col gap-0 bg-card">
      <div className="flex h-10 shrink-0 items-center gap-2 border-b px-2">
        <TabsList>
          <TabsTrigger value="console">Console</TabsTrigger>
          <TabsTrigger value="timeline">Timeline</TabsTrigger>
          <TabsTrigger value="assets">Assets</TabsTrigger>
        </TabsList>

        {tab === "console" && (
          <>
            {/*
              `destructive` only while the count is non-zero. A permanently red "0 errors" is a
              warning light wired to nothing, and the badge that means something is then the one
              nobody looks at.
            */}
            <Badge variant="destructive">1 error</Badge>
            <Badge variant="secondary">1 warning</Badge>
            <div className="flex-1" />
            <IconAction label="Clear console"><Eraser /></IconAction>
          </>
        )}

        {tab === "timeline" && (
          <>
            <ButtonGroup>
              <IconAction label="Jump to start"><SkipBack /></IconAction>
              <IconAction label="Pause"><Pause /></IconAction>
              <ButtonGroupSeparator />
              <IconAction label="Loop"><Repeat /></IconAction>
            </ButtonGroup>
            {/*
              Elapsed over total, monospaced. A scrub redraws this every frame, and proportional
              digits make the whole readout jitter under the pointer that is causing it.
            */}
            <span className="font-mono text-xs tabular-nums text-muted-foreground">0:01.20 / 0:03.00</span>
            <div className="flex-1" />
            <Select defaultValue="walk">
              <SelectTrigger className="h-7 w-[140px] text-xs" size="sm"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="idle">Idle</SelectItem>
                <SelectItem value="walk">Walk</SelectItem>
                <SelectItem value="jump">Jump</SelectItem>
              </SelectContent>
            </Select>
          </>
        )}

        {tab === "assets" && (
          <>
            <div className="relative w-56">
              <Search className="pointer-events-none absolute left-2 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
              <Input placeholder="Search assets" className="h-7 pl-7 text-xs" />
            </div>
            <Select defaultValue="all">
              <SelectTrigger className="h-7 w-[120px] text-xs" size="sm"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All types</SelectItem>
                <SelectItem value="mesh">Mesh</SelectItem>
                <SelectItem value="material">Material</SelectItem>
                <SelectItem value="texture">Texture</SelectItem>
              </SelectContent>
            </Select>
            <div className="flex-1" />
            <ToggleGroup type="single" defaultValue="grid" variant="outline" size="sm">
              <ToggleItem value="grid" label="Grid view"><LayoutGrid /></ToggleItem>
              <ToggleItem value="list" label="List view"><List /></ToggleItem>
            </ToggleGroup>
          </>
        )}
      </div>

      {/*
        `font-mono` and a fixed line height: a console is columnar output, and a proportional font
        makes two stack traces impossible to compare by eye.
      */}
      <TabsContent value="console" className="min-h-0 overflow-auto px-2 py-1.5 font-mono text-xs leading-5">
        <LogLine time="12:47:02">Loaded rotating-cube (4 entities)</LogLine>
        <LogLine time="12:47:02">Vulkan device: Apple M3 Pro</LogLine>
        <LogLine time="12:47:04" level="warn">Mesh &apos;ground&apos; has no material; using fallback</LogLine>
        <LogLine time="12:47:09" level="error">Shader compile failed: skinned_textured.frag</LogLine>
        <LogLine time="12:47:09" level="error" indent>line 42: undeclared identifier &apos;uBoneMatrices&apos;</LogLine>
      </TabsContent>

      {/*
        Clips on rows, not keyframes on curves.

        That is what Awake models today: `EditorAnimationPanel` takes a clip list, a selected clip,
        a play flag and a seek position, and there is no keyframe or curve anywhere in the engine.
        A reference drawn as a curve editor would be specifying a data model that does not exist,
        and the port would have to invent one to match a picture.

        The name gutter is a sibling column rather than a first cell per row, so the ruler's ticks
        and the clip bars share one coordinate space -- `left: 30%` means the same thing in both.
      */}
      <TabsContent value="timeline" className="flex min-h-0 overflow-auto">
        <div className="w-32 shrink-0 border-r">
          <div className="h-6 border-b" />
          {["Idle", "Walk", "Jump"].map((name) => (
            <div key={name} className="flex h-7 items-center border-b border-border/50 px-2 text-xs">
              {name}
            </div>
          ))}
        </div>
        <div className="relative min-w-0 flex-1">
          {/*
            Six equal cells labelled at their left edge, so the track is 6 x 0.5s = 3.0s wide and
            every percentage below is that same 3.0s. Label the last cell 2.5s and call the total
            2.5s and the ruler is silently 3.0s long -- the playhead then sits at a different time
            from the one printed beside it, and a port copying the numbers inherits the
            discrepancy rather than the layout.
          */}
          <div className="flex h-6 border-b">
            {["0.0", "0.5", "1.0", "1.5", "2.0", "2.5"].map((tick) => (
              <div
                key={tick}
                className="flex-1 border-l border-border/50 pl-1 text-[10px] tabular-nums text-muted-foreground first:border-l-0"
              >
                {tick}s
              </div>
            ))}
          </div>
          {/* Percentages of 3.0s: Idle 0-1.92, Walk 1.08-3.0, Jump 2.16-3.0. */}
          <TimelineClip name="Idle" start={0} end={64} />
          <TimelineClip name="Walk" start={36} end={100} selected />
          <TimelineClip name="Jump" start={72} end={100} />
          {/*
            The playhead spans the ruler as well as the tracks, so the time it points at is
            readable without counting rows down from a tick. 1.20s of 3.0s.
          */}
          <div className="pointer-events-none absolute inset-y-0 w-px bg-primary" style={{ left: "40%" }}>
            <div className="size-2 -translate-x-1/2 rounded-full bg-primary" />
          </div>
        </div>
      </TabsContent>

      <TabsContent value="assets" className="min-h-0 overflow-auto p-2">
        <div className="flex flex-wrap gap-1">
          <AssetTile name="cube.glb" kind="MESH" />
          <AssetTile name="ground.glb" kind="MESH" selected />
          <AssetTile name="lit-shadow" kind="MATERIAL" />
          <AssetTile name="skybox.ktx2" kind="TEXTURE" />
          <AssetTile name="particle-dot.png" kind="TEXTURE" />
          <AssetTile name="walk.anim" kind="CLIP" />
        </div>
      </TabsContent>
    </Tabs>
  )
}

/**
 * The reference cases. Each id is the single source of truth shared with the Awake side:
 * the capture renders `?case=<id>`, and the Kotlin preview of the same id renders the
 * equivalent Awake components. Pairing is therefore by construction rather than hand-matched.
 *
 * States that a docs demo cannot show -- focus, disabled, hover -- are ordinary cases here,
 * which is the whole reason for owning the reference page.
 */
export const CASES: Record<
  string,
  {
    render: () => ReactNode
    /**
     * True when the case draws its own theme control, so the app does not float a second one.
     *
     * Only a full-screen case has anywhere to put one. Every other case is a component on an
     * empty page, and its theme control belongs to the page.
     */
    ownsThemeToggle?: boolean
  }
> = {
  "button-variants": {
    render: () => (
      <div className="flex items-center gap-2">
        <Button data-parity-id="parity-default">Default</Button>
        <Button data-parity-id="parity-secondary" variant="secondary">Secondary</Button>
        <Button data-parity-id="parity-outline" variant="outline">Outline</Button>
        <Button data-parity-id="parity-ghost" variant="ghost">Ghost</Button>
        <Button data-parity-id="parity-destructive" variant="destructive">Destructive</Button>
        <Button data-parity-id="parity-link" variant="link">Link</Button>
      </div>
    ),
  },
  "button-icons": {
    render: () => (
      <div className="flex items-center gap-2">
        <Button data-parity-id="parity-icon-default" size="icon" aria-label="Camera"><Camera /></Button>
        <Button data-parity-id="parity-icon-outline" variant="outline" size="icon-sm" aria-label="Save"><Save /></Button>
        <Button data-parity-id="parity-icon-label" variant="outline"><Save />Save scene</Button>
      </div>
    ),
  },
  "button-sizes": {
    render: () => (
      <div className="flex items-center gap-2">
        <Button data-parity-id="parity-size-small" size="sm">Small</Button>
        <Button data-parity-id="parity-size-default">Default</Button>
        <Button data-parity-id="parity-size-large" size="lg">Large</Button>
      </div>
    ),
  },
  "button-disabled": {
    render: () => (
      <div className="flex items-center gap-2">
        <Button data-parity-id="parity-disabled-default" disabled>Default</Button>
        <Button data-parity-id="parity-disabled-outline" variant="outline" disabled>Outline</Button>
        <Button data-parity-id="parity-disabled-destructive" variant="destructive" disabled>Destructive</Button>
      </div>
    ),
  },
  "button-group-basic": {
    render: () => (
      <ButtonGroup data-parity-id="parity-button-group">
        <Button data-parity-id="parity-button-group.archive" variant="outline">Archive</Button>
        <Button data-parity-id="parity-button-group.report" variant="outline">Report</Button>
      </ButtonGroup>
    ),
  },
  "button-group-vertical": {
    render: () => (
      <ButtonGroup data-parity-id="parity-button-group-vertical" orientation="vertical" className="w-[156px]">
        <Button data-parity-id="parity-button-group-vertical.archive" variant="outline">Archive</Button>
        <Button data-parity-id="parity-button-group-vertical.report" variant="outline">Report</Button>
      </ButtonGroup>
    ),
  },
  "checkbox-states": {
    render: () => (
      <div className="flex items-center gap-4">
        <Checkbox data-parity-id="parity-checkbox-unchecked" />
        <Checkbox data-parity-id="parity-checkbox-checked" defaultChecked />
        <Checkbox data-parity-id="parity-checkbox-disabled" disabled />
      </div>
    ),
  },
  "radio-group-states": {
    render: () => (
      <RadioGroup defaultValue="comfortable">
        {[
          ["default", "Default"],
          ["comfortable", "Comfortable"],
          ["compact", "Compact"],
        ].map(([value, label], idx) => (
          <div className="flex items-center gap-2" key={value}>
            <RadioGroupItem value={value} id={`radio-${value}`} data-parity-id={`parity-radio.${idx}`} />
            <Label htmlFor={`radio-${value}`} data-parity-id={`parity-radio.${idx}.label`}>{label}</Label>
          </div>
        ))}
      </RadioGroup>
    ),
  },
  "progress-states": {
    render: () => (
      <div className="flex w-[212px] flex-col gap-4">
        <Progress value={25} data-parity-id="parity-progress-1" />
        <Progress value={65} data-parity-id="parity-progress-2" />
      </div>
    ),
  },
  "switch-states": {
    render: () => (
      <div className="flex items-center gap-4">
        <Switch data-parity-id="parity-switch-off" />
        <Switch data-parity-id="parity-switch-on" defaultChecked />
        <Switch data-parity-id="parity-switch-disabled" disabled />
      </div>
    ),
  },
  "input-states": {
    render: () => (
      <div className="flex flex-col gap-3 w-64">
        <Input data-parity-id="parity-field-1" placeholder="Placeholder" />
        <Input data-parity-id="parity-field-2" defaultValue="Typed text" />
        <Input data-parity-id="parity-field-3" placeholder="Disabled" disabled />
      </div>
    ),
  },
  "tabs-states": {
    render: () => (
      <Tabs defaultValue="account">
        <TabsList data-parity-id="parity-tabs.track">
          <TabsTrigger value="account" data-parity-id="parity-tabs.Account">Account</TabsTrigger>
          <TabsTrigger value="password" data-parity-id="parity-tabs.Password">Password</TabsTrigger>
        </TabsList>
      </Tabs>
    ),
  },
  "slider-states": {
    render: () => (
      <div className="w-[300px]">
        <Slider defaultValue={[50]} max={100} step={1} data-parity-id="parity-slider" />
      </div>
    ),
  },
  "select-closed": {
    render: () => (
      <Select>
        <SelectTrigger className="w-[172px]" data-parity-id="parity-select">
          <SelectValue placeholder="Select a fruit" />
        </SelectTrigger>
        <SelectContent>
          {["Apple", "Banana", "Blueberry", "Grapes", "Pineapple"].map((f) => (
            <SelectItem key={f} value={f.toLowerCase()}>{f}</SelectItem>
          ))}
        </SelectContent>
      </Select>
    ),
  },
  "select-open": {
    render: () => (
      <Select value="banana" open>
        <SelectTrigger className="w-[172px]" data-parity-id="parity-select.trigger">
          <SelectValue placeholder="Select a fruit" />
        </SelectTrigger>
        <SelectContent data-parity-id="parity-select.content">
          {["Apple", "Banana", "Blueberry", "Grapes", "Pineapple"].map((f) => (
            <SelectItem
              key={f}
              value={f.toLowerCase()}
              data-parity-id={`parity-select.item.${f.toLowerCase()}`}
            >
              {f}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    ),
  },
  "select-scrolled": {
    render: () => (
      <Select value="item-6" open>
        <SelectTrigger className="w-[172px]" data-parity-id="parity-select.scrolled.trigger">
          <SelectValue />
        </SelectTrigger>
        <SelectContent data-parity-id="parity-select.scrolled.content">
          {Array.from({ length: 10 }, (_, index) => `Item ${index + 1}`).map((label, index) => (
            <SelectItem
              key={label}
              value={`item-${index + 1}`}
              data-parity-id={`parity-select.scrolled.item.${index}`}
            >
              {label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    ),
  },
  "card-login": {
    render: () => (
      <Card data-parity-id="parity-card" className="w-[288px]">
        <CardHeader data-parity-id="parity-card.header">
          <CardTitle data-parity-id="parity-card.title">Login to your account</CardTitle>
        </CardHeader>
        <CardContent data-parity-id="parity-card.content" className="flex flex-col gap-3">
          <Label htmlFor="email" data-parity-id="parity-card.label">Email</Label>
          <Input id="email" placeholder="Email" data-parity-id="parity-card.email" />
          <Button className="w-full" data-parity-id="parity-card.login">Login</Button>
        </CardContent>
      </Card>
    ),
  },
  // Tooltip and dialog render open via `defaultOpen`/`open` rather than needing the capture to
  // hover or click. A docs page can only be scraped in whatever state it happens to be in;
  // owning the page means an open overlay is just another case.
  "tooltip-open": {
    render: () => (
      <TooltipProvider>
        <Tooltip defaultOpen>
          <TooltipTrigger asChild>
            <Button variant="outline" className="w-[110px]" data-parity-id="parity-tooltip-trigger">Hover me</Button>
          </TooltipTrigger>
          <TooltipContent side="bottom">Add to library</TooltipContent>
        </Tooltip>
      </TooltipProvider>
    ),
  },
  "dialog-open": {
    render: () => (
      <Dialog open>
        <DialogContent showCloseButton={false} className="w-[320px]" data-parity-id="parity-dialog">
          <DialogHeader>
            <DialogTitle>Edit profile</DialogTitle>
            <DialogDescription>
              Make changes to your profile here. Click save when you're done.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button data-parity-id="parity-dialog-save">Save changes</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    ),
  },
  "badge-variants": {
    render: () => (
      <div className="flex items-center gap-2">
        {/* data-parity-id maps this element to the Awake semantic node of the same id, so the
            capture can export its box and a test can compare numbers instead of pixels. */}
        <Badge data-parity-id="badge.Default">Default</Badge>
        <Badge data-parity-id="badge.Secondary" variant="secondary">Secondary</Badge>
        <Badge data-parity-id="badge.Destructive" variant="destructive">Destructive</Badge>
        <Badge data-parity-id="badge.Outline" variant="outline">Outline</Badge>
      </div>
    ),
  },
  "empty-states": {
    render: () => (
      <div className="w-[320px]">
        <Empty data-parity-id="parity-empty">
          <EmptyHeader>
            <EmptyTitle data-parity-id="parity-empty.title">No entities</EmptyTitle>
            <EmptyDescription data-parity-id="parity-empty.description">
              Add one to get started.
            </EmptyDescription>
          </EmptyHeader>
          <EmptyContent data-parity-id="parity-empty.content" />
        </Empty>
      </div>
    ),
  },
  "resizable-horizontal": {
    render: () => (
      // Sized by the wrapper: the group is `h-full w-full`, so inside an unsized parent it
      // collapses to nothing -- the first capture came out 3x2.
      <div className="w-[400px] h-[160px]">
        <ResizablePanelGroup
          orientation="horizontal"
          data-parity-id="parity-resizable.group"
          className="rounded-lg border"
        >
          <ResizablePanel defaultSize={50} data-parity-id="parity-resizable.first">
            <div className="flex h-full items-center justify-center text-sm">One</div>
          </ResizablePanel>
          <ResizableHandle data-parity-id="parity-resizable.handle" />
          <ResizablePanel defaultSize={50} data-parity-id="parity-resizable.second">
            <div className="flex h-full items-center justify-center text-sm">Two</div>
          </ResizablePanel>
        </ResizablePanelGroup>
      </div>
    ),
  },
  /**
   * Studio's whole shell, in real shadcn.
   *
   * Static markup only -- nothing here is wired, because the question it answers is what the
   * screen should look like, not what it should do. Every other case is one component; this one
   * exists because a shell's fidelity lives in the seams between components (does the dock meet
   * the status bar, does the sidebar share the top bar's border) and no per-component capture
   * can show that.
   *
   * There is no vendored `sidebar.tsx`: upstream's Sidebar is an application shell with its own
   * provider, sheet and cookie state, and the Awake recipe deliberately ports only the panel. The
   * panels here are the panel -- `bg-sidebar`, `p-2` -- which is the thing to compare against.
   *
   * Fixed 1440x900 to match `StudioShellRenderPreview`'s frame, so the two PNGs line up pixel for
   * pixel instead of by eye.
   */
  /**
   * The Field family, every part at once.
   *
   * One case rather than ten: these parts only mean anything in composition -- a legend's `mb-3`,
   * a group's `gap-7` against a field's `gap-3`, a description's `gap-1.5` under its label. Ten
   * separate captures would each show a correct part and none of them the spacing between parts,
   * which is the whole component.
   */
  "field-anatomy": {
    render: () => (
      <div className="w-[420px]">
        <FieldSet data-parity-id="parity-field.set">
          <FieldLegend data-parity-id="parity-field.legend">Account</FieldLegend>
          <FieldGroup data-parity-id="parity-field.group">
            <Field data-parity-id="parity-field.vertical">
              <FieldLabel htmlFor="email" data-parity-id="parity-field.label">Email</FieldLabel>
              <Input id="email" defaultValue="ada@example.com" />
              <FieldDescription data-parity-id="parity-field.description">
                We only use this to sign you in.
              </FieldDescription>
            </Field>

            <Field data-parity-id="parity-field.invalid">
              <FieldLabel htmlFor="handle">Handle</FieldLabel>
              <Input id="handle" defaultValue="" aria-invalid />
              <FieldError data-parity-id="parity-field.error" errors={[{ message: "Handle is required" }]} />
            </Field>

            <FieldSeparator data-parity-id="parity-field.separator" />

            <Field orientation="horizontal" data-parity-id="parity-field.horizontal">
              <FieldContent data-parity-id="parity-field.content">
                <FieldTitle data-parity-id="parity-field.title">Notifications</FieldTitle>
                <FieldDescription>Email me when something breaks.</FieldDescription>
              </FieldContent>
              <Switch defaultChecked />
            </Field>

            <FieldSeparator data-parity-id="parity-field.separator-labelled">Or</FieldSeparator>
          </FieldGroup>
        </FieldSet>
      </div>
    ),
  },
  "table-demo": {
    render: () => {
      const invoices = [
        { invoice: "INV001", status: "Paid", method: "Credit Card", amount: "$250.00" },
        { invoice: "INV002", status: "Pending", method: "PayPal", amount: "$150.00" },
        { invoice: "INV003", status: "Unpaid", method: "Bank Transfer", amount: "$350.00" },
      ]
      return (
        <div className="w-[520px]" data-parity-id="parity-table.root">
          <Table>
            <TableCaption data-parity-id="parity-table.caption">
              A list of your recent invoices.
            </TableCaption>
            <TableHeader data-parity-id="parity-table.header">
              <TableRow data-parity-id="parity-table.header-row">
                <TableHead className="w-[100px]" data-parity-id="parity-table.head.0">Invoice</TableHead>
                <TableHead data-parity-id="parity-table.head.1">Status</TableHead>
                <TableHead data-parity-id="parity-table.head.2">Method</TableHead>
                <TableHead className="text-right" data-parity-id="parity-table.head.3">Amount</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody data-parity-id="parity-table.body">
              {invoices.map((row, index) => (
                <TableRow key={row.invoice} data-parity-id={`parity-table.row.${index}`}>
                  <TableCell className="font-medium" data-parity-id={`parity-table.cell.${index}.0`}>
                    {row.invoice}
                  </TableCell>
                  <TableCell data-parity-id={`parity-table.cell.${index}.1`}>{row.status}</TableCell>
                  <TableCell data-parity-id={`parity-table.cell.${index}.2`}>{row.method}</TableCell>
                  <TableCell className="text-right" data-parity-id={`parity-table.cell.${index}.3`}>
                    {row.amount}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
            <TableFooter data-parity-id="parity-table.footer">
              <TableRow data-parity-id="parity-table.footer-row">
                <TableCell colSpan={3} data-parity-id="parity-table.footer-cell">Total</TableCell>
                <TableCell className="text-right">$750.00</TableCell>
              </TableRow>
            </TableFooter>
          </Table>
        </div>
      )
    },
  },
  "studio-shell": {
    ownsThemeToggle: true,
    render: () => (
      <TooltipProvider delayDuration={300}>
        <div className="w-[1440px] h-[900px] flex flex-col bg-background text-foreground">
        <header className="flex h-11 shrink-0 items-center gap-2 px-3">
          <span className="text-sm font-medium">Awake Studio</span>
          <Select defaultValue="rotating-cube">
            <SelectTrigger className="w-[180px] h-8" size="sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="rotating-cube">Rotating cube</SelectItem>
            </SelectContent>
          </Select>

          {/*
            Save, undo, redo as outlined icon buttons, not a command menu.

            A command menu is shadcn's `Command` -- a searchable palette behind a shortcut, for
            reaching any of a hundred things by name. These are three, they are the three a user
            reaches for constantly, and burying undo one keystroke and one search deep is worse
            than a toolbar in every way. The right pairing is both: these stay, and a palette gets
            added later for everything that has no button.

            Outlined rather than ghost, to match the viewport pills -- and because a ghost button
            with no label and no border is invisible until hovered.
          */}
          <ButtonGroup>
            {/*
              The unsaved dot rides the Save button, not the scene name.

              On the name it is one glyph inside a select trigger the eye reads as a label, and it
              was invisible next to a caret. On the button it sits on the control that clears it,
              which is also the only place a reader can act on what it says.
            */}
            <IconAction label="Save scene (unsaved changes)" className="relative">
              <Save />
              <span className="absolute right-0.5 top-0.5 size-1.5 rounded-full bg-primary" />
            </IconAction>
            <ButtonGroupSeparator />
            <IconAction label="Undo"><Undo2 /></IconAction>
            <IconAction label="Redo" disabled><Redo2 /></IconAction>
          </ButtonGroup>

          {/* Everything above is the document; everything after the spacer is the app. */}
          <div className="flex-1" />
          {/*
            Play carries its label. It is the one control that changes what the whole window means
            -- edit or run -- and the only irreversible-feeling thing on the bar, so it is the one
            control that cannot afford to be a glyph the user has to recognise.
          */}
          <Button size="sm"><Play />Play</Button>
          <ThemeToggle />
        </header>
        <Separator />

        <ResizablePanelGroup orientation="vertical" className="flex-1">
          {/* 76/24, not 72/28: the inspector now has content, and the dock is one log line. */}
          <ResizablePanel defaultSize={76}>
            <ResizablePanelGroup orientation="horizontal" className="h-full">
              <ResizablePanel defaultSize={18}>
                <div className="flex h-full flex-col gap-2 bg-sidebar p-2 text-sidebar-foreground">
                  {/*
                    Header, search, tree -- in that order, and the tree is the only part that
                    scrolls. A search box that scrolls away is a search box you cannot reach from
                    the bottom of a thousand-entity scene, which is the only place you need it.
                  */}
                  <div className="flex h-7 shrink-0 items-center gap-1">
                    <span className="flex-1 text-sm font-medium">Scene</span>
                    <IconAction label="Add entity" className="size-6"><Plus /></IconAction>
                    <IconAction label="Delete selected" className="size-6"><Trash2 /></IconAction>
                  </div>

                  <div className="relative shrink-0">
                    <Search className="pointer-events-none absolute left-2 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
                    <Input placeholder="Search entities" className="h-7 pl-7 text-xs" />
                  </div>

                  <div className="flex min-h-0 w-full min-w-0 flex-1 flex-col gap-px overflow-auto">
                    <TreeRow icon={<Camera />} name="camera" />
                    <TreeRow icon={<BoxIcon />} name="props" expanded />
                    <TreeRow icon={<BoxIcon />} name="cube" depth={1} selected />
                    <TreeRow icon={<Grid3x3 />} name="ground" depth={1} hidden />
                    <TreeRow icon={<Lightbulb />} name="light" />
                  </div>
                </div>
              </ResizablePanel>
              <ResizableHandle />

              <ResizablePanel defaultSize={62}>
                <div className="flex h-full flex-col p-2">
                {/*
                  Three anchors, one per kind of control, floating over the 3D view.

                  A single strip across the top costs the scene a full band of its most useful
                  area -- the top of the frame is where a horizon and an object's head sit -- and
                  at 790px it was 88% of the panel's width. Splitting by kind puts each group
                  where it is reached from and leaves the centre of the view clear:

                    top left       tools      modal, one at a time
                    top centre     display    render style and debug overlays, all independent
                    top right      gizmo      which way is up
                    right, centred camera     which camera and how it projects
                    bottom right   preview    what the authored camera sees

                  Tools are anchored to the corner, not centred on the edge. Centred was the
                  earlier choice, for a hand resting mid-edge, and it moves: the palette is
                  centred in the viewport panel, so dragging the console dock taller slides
                  Select/Move/Rotate/Scale up the screen while the user is aiming at them. A
                  corner is the only position in a resizable panel that stays where it was.

                  ToggleGroup, not ButtonGroup. shadcn's own rule: ButtonGroup groups buttons that
                  perform an action, ToggleGroup groups buttons that toggle a state. One control
                  here performs an action (cycle camera mode); the other thirteen are state.
                  `type="single"` is the tool pill exactly -- modal, one at a time.

                  Every toggle is icon-only. `Persp`/`Sky`/`Light`/`Shadow` were ~60px of text
                  each against 32px for a glyph, and they are the four that overflowed their
                  fixed-square buttons in the Awake render. A vertical pill makes that mandatory
                  rather than merely tidier: a column sized to fit "Shadow" is a column 60px wide.

                  No glyph is used twice. The Awake port reused one for Sky and the orientation
                  gizmo, and another for Wireframe and orthographic projection, which makes an
                  icon-only strip unreadable however good the tooltips are.
                */}
                  <div className="relative flex-1">
                  {/* Tools: modal, one at a time. */}
                  <div className="absolute left-2 top-2">
                    <VerticalToggleGroup type="single" defaultValue="move" variant="outline" size="sm">
                      <ToggleItem value="select" label="Select"><MousePointer2 /></ToggleItem>
                      <ToggleItem value="move" label="Move"><Move /></ToggleItem>
                      <ToggleItem value="rotate" label="Rotate"><RotateCw /></ToggleItem>
                      <ToggleItem value="scale" label="Scale"><Shrink /></ToggleItem>
                    </VerticalToggleGroup>
                  </div>

                  {/*
                    Camera: one action then one toggle, which is what a split ButtonGroup is for.
                    Cycling the camera mode is not a state this button holds.

                    Still centred on its edge rather than cornered, because both corners on this
                    side are taken -- gizmo above, preview below -- and it is reached from the
                    viewport rather than aimed at repeatedly the way a tool is.
                  */}
                  <div className="absolute right-2 top-1/2 -translate-y-1/2">
                    <ButtonGroup orientation="vertical">
                      <IconAction label="Cycle camera mode"><Eye /></IconAction>
                      <ButtonGroupSeparator orientation="horizontal" />
                      <IconAction label="Perspective or orthographic"><Frame /></IconAction>
                    </ButtonGroup>
                  </div>

                  {/* Display: render style, then debug overlays. Independent toggles, both. */}
                  <div className="absolute left-1/2 top-2 flex -translate-x-1/2 items-center gap-2">
                    <ToggleGroup type="multiple" defaultValue={["shadows"]} variant="outline" size="sm">
                      <ToggleItem value="wireframe" label="Wireframe"><Grid2x2 /></ToggleItem>
                      <ToggleItem value="shadows" label="Shadows"><Sun /></ToggleItem>
                      <ToggleItem value="sky" label="Sky"><CloudSun /></ToggleItem>
                    </ToggleGroup>

                    {/*
                      Preview and Gizmo join the debug group. They were labelled checkboxes -- the
                      widest items on the old row, and toggles sitting outside every toggle group.
                    */}
                    <ToggleGroup type="multiple" defaultValue={["preview", "gizmo"]} variant="outline" size="sm">
                      <ToggleItem value="frustum" label="Camera frustum"><Video /></ToggleItem>
                      <ToggleItem value="bounds" label="Bounds"><BoxIcon /></ToggleItem>
                      <ToggleItem value="occlusion" label="Occlusion"><EyeOff /></ToggleItem>
                      <ToggleItem value="lights" label="Lights"><Lightbulb /></ToggleItem>
                      <ToggleItem value="shadow-frustum" label="Shadow frustum"><Layers /></ToggleItem>
                      <ToggleItem value="preview" label="Camera preview"><Camera /></ToggleItem>
                      <ToggleItem value="gizmo" label="Orientation gizmo"><Compass /></ToggleItem>
                    </ToggleGroup>
                  </div>

                  {/*
                    Orientation gizmo and camera preview: both are renderer output in Awake -- an
                    offscreen target composited as a texture -- so they are drawn here as the box
                    that output lands in, at the size it lands at. The reference owes the port
                    their placement and footprint, which is what a corner widget gets wrong; it
                    does not owe it their pixels, which the renderer produces.
                  */}
                  <div className="absolute right-2 top-2 flex size-16 items-center justify-center rounded-md border bg-card/80">
                    <span className="text-[10px] text-muted-foreground">gizmo</span>
                  </div>

                  <div className="absolute bottom-2 right-2 w-[220px] rounded-md border bg-card p-1.5 shadow-xs">
                    <div className="flex items-center justify-between pb-1.5">
                      <span className="text-xs font-medium">Camera Preview</span>
                      <Button variant="ghost" size="sm" className="h-6 px-2 text-xs">Align View</Button>
                    </div>
                    <div className="h-[124px] rounded-sm bg-muted" />
                  </div>
                </div>
                </div>
              </ResizablePanel>
              <ResizableHandle />

              <ResizablePanel defaultSize={20}>
                {/*
                  The inspector with a selection, not its empty state.

                  The empty state is two lines of text and needs no reference; the populated one is
                  where every layout question actually lives -- how a component section is headed,
                  how a three-axis row fits 300px, where "Add component" sits. Awake's own inspector
                  stops at Transform, so this is the part of the shell the reference is furthest
                  ahead on and the part it exists to specify.

                  No collapse chevron in the header. There was one, and the resizable handle
                  immediately to its left already collapses this panel -- better, because it is
                  draggable and remembers a width. The hierarchy never had one either, so it was
                  also a control on one of two matched panels.
                */}
                <div className="flex h-full flex-col bg-sidebar text-sidebar-foreground">
                  <div className="flex h-9 shrink-0 items-center gap-2 border-b px-2">
                    <span className="text-sm font-medium">Inspector</span>
                    <Badge variant="secondary" className="font-mono text-[10px]">#2</Badge>
                  </div>

                  <div className="flex min-h-0 flex-1 flex-col gap-2 overflow-auto p-2">
                    <InspectorField label="Name">
                      <Input defaultValue="cube" className="h-7 text-xs" />
                    </InspectorField>

                    <InspectorSection title="Transform">
                      <VectorRow label="Position" value={["0", "0.5", "0"]} />
                      <VectorRow label="Rotation" value={["0", "45", "0"]} />
                      <VectorRow label="Scale" value={["1", "1", "1"]} />
                    </InspectorSection>

                    <InspectorSection title="Mesh Renderer">
                      <InspectorField label="Mesh">
                        <Select defaultValue="cube">
                          <SelectTrigger className="h-7 w-full text-xs" size="sm"><SelectValue /></SelectTrigger>
                          <SelectContent><SelectItem value="cube">cube</SelectItem></SelectContent>
                        </Select>
                      </InspectorField>
                      <InspectorField label="Material">
                        <Select defaultValue="lit">
                          <SelectTrigger className="h-7 w-full text-xs" size="sm"><SelectValue /></SelectTrigger>
                          <SelectContent><SelectItem value="lit">lit-shadow</SelectItem></SelectContent>
                        </Select>
                      </InspectorField>
                      <InspectorField label="Cull">
                        {/*
                          Three mutually exclusive values, all short: a segmented control shows the
                          options the select would hide behind a click, at the same width.

                          Items size themselves; the group is not stretched to the row. Upstream's
                          item is `h-[calc(100%-1px)]`, which needs a parent whose height is already
                          known -- inside a `w-full` group in a content-sized row that is circular,
                          and it resolved to 27px around 32px of content, clipping all three labels.
                        */}
                        <ToggleGroup type="single" defaultValue="back" variant="outline" size="sm">
                          <ToggleGroupItem value="none" className="text-xs">None</ToggleGroupItem>
                          <ToggleGroupItem value="back" className="text-xs">Back</ToggleGroupItem>
                          <ToggleGroupItem value="front" className="text-xs">Front</ToggleGroupItem>
                        </ToggleGroup>
                      </InspectorField>
                    </InspectorSection>

                    <InspectorSection title="PBR Material">
                      <InspectorField label="Metallic">
                        <Slider defaultValue={[0]} max={100} className="py-1.5" />
                      </InspectorField>
                      <InspectorField label="Roughness">
                        <Slider defaultValue={[50]} max={100} className="py-1.5" />
                      </InspectorField>
                    </InspectorSection>
                  </div>

                  {/*
                    Pinned to the panel's bottom, outside the scroll area. In the scroll area it
                    sits under the last component, which is off-screen on any entity with more than
                    about four -- exactly the entities you are most likely to be adding one to.
                  */}
                  <div className="shrink-0 border-t p-2">
                    <Button variant="outline" size="sm" className="w-full"><Plus />Add component</Button>
                  </div>
                </div>
              </ResizablePanel>
            </ResizablePanelGroup>
          </ResizablePanel>
          <ResizableHandle />

          <ResizablePanel defaultSize={24}>
            <StudioDock />
          </ResizablePanel>
        </ResizablePanelGroup>

        <Separator />
        {/*
          Segments with rules between them, and `tabular-nums` on every figure.

          It was one dash-joined string of eight values, four of them a frame breakdown -- ui,
          wait, stage, sim+render. Two problems, and the fix for each is the other half of this
          row. The digits re-lay-out the whole line every frame in a proportional font, so the
          entity count physically moves while you read it; `tabular-nums` and a fixed segment
          width stop that.

          The breakdown stays on the bar. F2 toggles whether the phases are *collected*, not a
          second surface that displays them -- there is no overlay to move them to. What it is
          shown here doing is disappearing when it was never measured: Awake's phaseStats() reports
          all-zero rather than null while collection is off, so the bar printed four 0.0ms readings
          on every frame of every run.
        */}
        <footer className="flex h-7 shrink-0 items-center gap-3 px-3 text-xs text-muted-foreground">
          <span className="font-medium text-foreground">Edit mode</span>
          <Separator orientation="vertical" className="h-3.5" />
          <span className="tabular-nums">4 entities</span>
          <Separator orientation="vertical" className="h-3.5" />
          <span className="tabular-nums">60 fps</span>
          <span className="tabular-nums">16.7 ms</span>
          <Separator orientation="vertical" className="h-3.5" />
          <span className="tabular-nums">ui 0.4</span>
          <span className="tabular-nums">wait 0.0</span>
          <span className="tabular-nums">stage 0.6</span>
          <span className="tabular-nums">sim+render 1.4</span>
          <div className="flex-1" />
          <Badge variant="secondary">Vulkan</Badge>
        </footer>
        </div>
      </TooltipProvider>
    ),
  },
  "textarea-states": {
    render: () => (
      <div className="flex flex-col gap-4 w-[272px]">
        <Textarea data-parity-id="parity-textarea-1" placeholder="Default textarea" />
        <Textarea data-parity-id="parity-textarea-2" defaultValue={"Line 1\nLine 2\nLine 3"} />
        <Textarea data-parity-id="parity-textarea-3" placeholder="Disabled textarea" disabled />
      </div>
    ),
  },
  "toggle-button-variants": {
    render: () => (
      <div className="flex items-center gap-[10px] h-[40px]">
        <Toggle data-parity-id="parity-toggle-off" className="w-[40px] h-[40px]">B</Toggle>
        <Toggle data-parity-id="parity-toggle-on" defaultPressed className="w-[40px] h-[40px]">B</Toggle>
        <Toggle data-parity-id="parity-toggle-disabled" disabled className="w-[40px] h-[40px]">B</Toggle>
      </div>
    ),
  },
  "alert-variants": {
    render: () => (
      <div className="flex flex-col gap-4 w-[272px]">
        <Alert data-parity-id="parity-alert-default">
          <AlertTitle>You can add components</AlertTitle>
          <AlertDescription>Use the CLI to add components to your project.</AlertDescription>
        </Alert>
        <Alert data-parity-id="parity-alert-destructive" variant="destructive">
          <AlertTitle>Unable to process your payment.</AlertTitle>
          <AlertDescription>Please verify your billing information and try again.</AlertDescription>
        </Alert>
      </div>
    ),
  },
  "avatar-states": {
    render: () => (
      <div className="flex items-center gap-3 h-[48px]">
        <Avatar data-parity-id="avatar.1">
          <AvatarFallback>CN</AvatarFallback>
        </Avatar>
        <Avatar data-parity-id="avatar.2" className="size-10">
          <AvatarFallback>RV</AvatarFallback>
        </Avatar>
      </div>
    ),
  },
  "breadcrumb-states": {
    render: () => (
      <Breadcrumb data-parity-id="breadcrumb-parity-test">
        <BreadcrumbList>
          <BreadcrumbItem><BreadcrumbLink href="#">Home</BreadcrumbLink></BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem><BreadcrumbLink href="#">Components</BreadcrumbLink></BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem><BreadcrumbPage>Breadcrumb</BreadcrumbPage></BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>
    ),
  },
  "collapsible-states": {
    render: () => (
      <Collapsible open className="w-[280px]">
        <CollapsibleTrigger data-parity-id="parity-collapsible.trigger" className="w-[280px] h-[36px] flex items-center justify-between font-semibold text-sm">Can I use this in my project?</CollapsibleTrigger>
        <CollapsibleContent className="text-sm text-muted-foreground mt-2">
          Yes. Free to use for personal and commercial projects.
        </CollapsibleContent>
      </Collapsible>
    ),
  },
  "kbd-states": {
    render: () => (
      <div className="flex items-center gap-[6px] h-[24px]">
        <Kbd data-parity-id="kbd.ctrl">Ctrl</Kbd>
        <Kbd data-parity-id="kbd.k">K</Kbd>
      </div>
    ),
  },
  "skeleton-states": {
    render: () => (
      <div className="flex flex-col gap-[10px] w-[192px]">
        <Skeleton data-parity-id="parity-skeleton-1" className="w-[192px] h-[16px]" />
        <Skeleton data-parity-id="parity-skeleton-2" className="w-[140px] h-[16px]" />
      </div>
    ),
  },
  "spinner-states": {
    render: () => (
      <div data-parity-id="parity-spinner" className="size-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
    ),
  },
  "dropdown-menu-states": {
    render: () => (
      <DropdownMenu open>
        <DropdownMenuTrigger asChild>
          <Button data-parity-id="parity-dropdown.trigger" variant="outline">Open menu</Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent open data-parity-id="parity-dropdown.surface" className="w-[160px]">
          <DropdownMenuItem data-parity-id="parity-dropdown.item.0">My Account</DropdownMenuItem>
          <DropdownMenuItem data-parity-id="parity-dropdown.item.1">Edit</DropdownMenuItem>
          <DropdownMenuItem data-parity-id="parity-dropdown.item.2">Duplicate</DropdownMenuItem>
          <DropdownMenuItem data-parity-id="parity-dropdown.item.3" className="text-destructive">Delete</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    ),
  },
  "popover-states": {
    render: () => (
      <Popover open>
        <PopoverTrigger asChild>
          <Button data-parity-id="parity-popover.trigger" variant="outline">Open popover</Button>
        </PopoverTrigger>
        <PopoverContent open className="w-[260px]" data-parity-id="parity-popover.content">
          <p className="text-sm">Place content for the popover here.</p>
        </PopoverContent>
      </Popover>
    ),
  },
}
