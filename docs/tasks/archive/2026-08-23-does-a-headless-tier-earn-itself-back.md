# 2026-08-23: does a headless tier earn itself back?

Status: **open question, deliberately unanswered.** Revisit after Stage 3 step 3, not before.

## Why this is not a reversal

Stage 3 deletes `ui-headless` because *that* layer did not earn its keep: measured across six
controls it held **zero state markers**, twelve of its files were re-export shims, and what remained
was rendering-with-the-colours-lifted-out — the opposite of Radix's behaviour-without-rendering. It
was not a headless tier. It was a drawing tier that `ui-core` forced into existence.

Deleting a layer that was mis-built is not the same as concluding the layer *idea* is wrong. Base UI
and Radix exist, they are load-bearing for a lot of people, and shadcn/ui itself is built on Radix.

## What makes this answerable now, and only now

Two layers means every recipe draws its own control against `:compose:foundation`. That is the
condition under which the cost of not having a headless tier becomes **visible instead of
theoretical**:

- The same keyboard/roving-focus logic appearing in menu, select, combobox and command.
- Two recipes independently deciding what "open" means for an anchored popup.
- A control whose behaviour cannot be reused unbranded — the debt `awake-ui-authoring` already
  records for Tabs, Collapsible, Dialog and DropdownMenu, reappearing.

Right now those are predictions. After 94 recipes exist against `:foundation`, they are either
duplication anyone can grep for or they are not.

**So the experiment is the port itself.** Nothing extra has to be built to run it.

## What to record while porting, so the question has evidence later

Cheap to note as it happens, expensive to reconstruct afterwards. When rewriting a recipe:

| Note it when | Because it is evidence of |
|---|---|
| Behaviour is copied from a recipe already written | a genuine shared primitive |
| Two recipes disagree about the same interaction | a missing canonical mechanism |
| A recipe needs a state machine, not just drawing | Radix's actual value |
| A test had to be duplicated to cover both copies | the cost compounding |

A running list in this file is enough. Not a new tracking system.

## The three outcomes, decided on the evidence

1. **No tier.** Duplication turned out to be small or mechanical. Two layers stay, and this file
   records that the question was asked properly rather than assumed.
2. **A few shared primitives, not a layer.** Roving focus, anchored-popup positioning, a dismiss
   stack — the things that recur. They live in `ui-designsystem` as internal helpers, or graduate to
   `:compose:foundation` **only if Compose Foundation ships an equivalent** (see the hard rule in
   `awake-ui-authoring`'s `references/compose-parity.md`; `Slider` is behaviour and Compose still
   puts it in Material). This is the likeliest outcome.
3. **A real headless tier**, built to the Radix contract this time: behaviour and state with no
   drawing at all, so the test of a control is that it renders nothing. That is a different thing
   from what is being deleted, and it would need its own plan.

## The one thing that would make this a mistake

Reintroducing a tier *because the old one existed*. The old one's own contents are the argument
against that, and they are measured in `2026-08-23-stage-3-plan.md`. A new tier needs its own
evidence, gathered above, and outcome 2 has to be ruled out before outcome 3 is proposed.
