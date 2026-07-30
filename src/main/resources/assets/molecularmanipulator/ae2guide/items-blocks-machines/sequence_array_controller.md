---
navigation:
  parent: omnisequence-index.md
  title: Sequence Array Controller
  icon: molecularmanipulator:molecular_center_controller
  position: 1030
item_ids:
- molecularmanipulator:molecular_center_controller
---

# Sequence Array Controller

<BlockImage id="molecularmanipulator:molecular_center_controller" scale="8" />

The Sequence Array Controller forms the fixed 31x46x31 Sequence Array. The completed multiblock combines massively
parallel autocrafting, an internal production pipeline, matter sequence rewriting, and an optional cross-dimensional
quantum connection.

## Building the structure

1. Place the controller facing outward. The structure extends 13 blocks below it, 32 blocks above it, and 15 blocks to
   either side.
2. Right-click the controller and enable the projection. Ghost blocks show missing positions, while red outlines show
   conflicts.
3. Use the JEI structure category for the complete layer view and material list.
4. Load every chunk covered by the structure, clear conflicts, and use **Build**. Construction fills missing blocks in
   batches.
5. Connect the completed structure to a powered ME Network. An AE2 wrench rotates the controller and refreshes the
   structure check.

Both the legacy and current center layouts remain valid. The current layout leaves the exact center of the energy field
as air and places the physical core at the top of the core sphere. When a complete legacy layout is detected, the
controller shows an optional structure-update notice. Ignoring it keeps the legacy structure operational; accepting it
recovers the old center core and relocates it safely.

The array does not force-load chunks. It pauses when its full area is not loaded and validates itself again when the
chunks return.

## Autocrafting and pipeline

The completed array supports virtual parallelism up to the signed 64-bit limit. Real throughput is still limited by
ingredients, energy, output capacity, and server tick time.

The pattern inventory accepts encoded AE2 crafting, smithing, and stonecutting patterns. Shift-moving a pattern fills
the current page first and continues into later pages when necessary; processing, blank, and invalid patterns are
rejected.

The pipeline tab shows active recipes, buffered key types, and pending outputs. Main products and byproducts can be
routed independently to the ME Network, the internal pipeline, or a selected output side.

## Matter sequence rewriting

The matter-rewriting system is similar to item transmutation in Equivalent Exchange: supported items are deconstructed
into stored sequence, and that sequence can then be spent to reproduce other supported items. Metal, mineral, crystal,
and organic sequence are stored independently. Deconstruction yields and rewriting costs are separate values, so an
item is not necessarily reproduced for the same amount recovered from deconstructing it.

### Deconstruction

Drag an item from JEI onto the **Deconstruction Marker** slot to select it; the marker is only a filter, so no physical
item is placed in or consumed by this slot. After deconstruction starts, the array automatically extracts matching
items from its connected ME Network and converts each one into the four configured sequence types.

The target controls how many items the current job processes. A target of `0` runs continuously. Missing input, ME
power, or a working network pauses the job and it retries automatically. Full sequence storage stops the job, while
excessive rewrite entropy puts it into the temporary **Cooling** state described below.

### Rewriting

Put one real item in the **Blueprint Sample** slot and start rewriting. The array spends that item's configured sequence
cost to create copies. The sample itself is never consumed, but it must remain in the slot unchanged for the entire job;
removing or replacing it stops the job.

Rewritten items can return directly to the ME Network or enter the controller's output slot. A target of `0` runs
continuously. If stored sequence or ME power is temporarily insufficient, the job waits and retries. A changed
blueprint or blocked output stops it.

Up to four AE2 acceleration cards affect both operations. They reduce the interval per item from 20 ticks with no card
to 10, 5, 2, or 1 tick with one through four cards.

### Rewrite entropy

Rewrite entropy is a heat-like throughput limit for matter deconstruction and rewriting; it does not affect normal
autocrafting. Every successful operation adds entropy based on the total sequence handled:

- Deconstruction adds at least 1 entropy, or the total recovered sequence divided by 64.
- Rewriting adds at least 1 entropy, or the total sequence cost divided by 16.

The array can hold 100,000 entropy and passively dissipates 25 entropy per second while loaded. If the next operation
would exceed the limit, that job pauses in **Cooling** before consuming anything and resumes automatically as soon as
enough entropy has dissipated. Cooling continues while the array works, which is why the bar can fill during heavy use
and fall again when processing slows or stops. Acceleration cards do not change entropy per item, but faster processing
can make it accumulate more quickly.

### Rules and item eligibility

Supported items and their exact deconstruction yields and rewriting costs are configured in:

`config/molecularmanipulator/matter_rewrite_rules.json`

The generated file supports exact item IDs, item tags, and tag-prefix wildcards, with separate `deconstruct` and
`rewrite` entries. Omit either entry to disable only that operation. Changes are loaded when the server or single-player
world starts.

Items with custom data, such as names, enchantments, durability, or container contents, are rejected.

Eligible item tooltips show a compact `[Hold Shift]` prompt by default. The client setting
`matter_sequence_tooltip_mode` can disable the details, keep Shift expansion, or show them permanently.

## Quantum link and visual settings

The quantum slot accepts one half of a paired entangled singularity. Put the other half in a powered AE2 Quantum Ring to
connect the array across dimensions. Before the structure is formed, this remote link can be used only to retrieve
construction materials. A full link consumes an additional 512 AE/t and one channel.

The colors tab changes the energy field, core, rings, and crystal effects. These settings are visual only and do not
change processing speed.

## Recipe

<RecipeFor id="molecularmanipulator:molecular_center_controller" />
