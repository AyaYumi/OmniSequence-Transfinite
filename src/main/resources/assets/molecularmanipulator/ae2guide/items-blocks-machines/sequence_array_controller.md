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

The matter tab stores four independent sequence types: metal, mineral, crystal, and organic.

- Set a deconstruction marker to continuously pull matching items from the ME Network and convert them into sequence.
- Put one real item in the blueprint slot to reproduce it from stored sequence. The sample is not consumed, but removing
  or changing it stops the job.
- Choose whether rewritten items return to the ME Network or enter the controller's output slot.
- A target of 0 runs continuously. Up to four AE2 acceleration cards reduce processing time.

Only items allowed by `config/molecularmanipulator/matter_rewrite_rules.json` can be processed. Items with custom data,
such as names, enchantments, durability, or container contents, are rejected.

Matter Sequence details in item tooltips default to a compact `[Hold Shift]` prompt. The client configuration can
disable them, keep the Shift-expand behavior, or display them permanently.

## Quantum link and visual settings

The quantum slot accepts one half of a paired entangled singularity. Put the other half in a powered AE2 Quantum Ring to
connect the array across dimensions. Before the structure is formed, this remote link can be used only to retrieve
construction materials. A full link consumes an additional 512 AE/t and one channel.

The colors tab changes the energy field, core, rings, and crystal effects. These settings are visual only and do not
change processing speed.

## Recipe

<RecipeFor id="molecularmanipulator:molecular_center_controller" />
