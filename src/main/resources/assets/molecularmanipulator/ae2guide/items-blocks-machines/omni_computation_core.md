---
navigation:
  parent: omnisequence-index.md
  title: Omni-Computation Core
  icon: molecularmanipulator:omni_computation_controller
  position: 1020
item_ids:
- molecularmanipulator:omni_computation_controller
---

# Omni-Computation Core

<BlockImage id="molecularmanipulator:omni_computation_controller" scale="8" />

The Omni-Computation Core is a fixed 31x31x39 end-game [crafting CPU](ae2:items-blocks-machines/crafting_cpu_multiblock.md).
When formed and online, it provides effectively unlimited logical crafting storage and parallelism.

## Building the structure

1. Place the controller facing outward. The structure extends 2 blocks below it, 36 blocks above it, and 15 blocks to
   either side.
2. Right-click the controller and enable the projection. Ghost blocks show missing positions, while red outlines show
   conflicts.
3. Use the JEI structure category for the complete layer view and material list.
4. Clear conflicting blocks, then use **Build**. Missing materials are taken from the player's inventory first and then
   from the connected ME Network.
5. Connect and power the controller, then use **Check** if the structure does not form immediately.

Both the legacy and current singularity layouts remain valid. The current layout leaves the exact effect center as air
and places the data entangler in the front lower pylon. When a complete legacy layout is detected, the controller shows
an optional structure-update notice. Ignoring it keeps the legacy structure operational; accepting it recovers and
relocates the center data entangler safely.

An AE2 wrench rotates the controller and recalculates the structure.

## Autocrafting

The core creates virtual CPU lanes as crafting requests arrive and keeps an idle lane available for new work. Its SAFE
calculation mode accelerates deterministic recipe trees. Recipes with substitution, container remainders, dynamic
inputs, cycles, or unknown behavior automatically fall back to the normal AE2 calculation path.

Crafting dispatch adapts to machine acceptance and server load. The displayed unlimited capacity is logical capacity;
ingredients, power, provider back-pressure, and server tick time still limit real throughput.

## Persistence and remote access

If part of the structure is broken or unloaded, active jobs, stored ingredients, and progress are preserved. Work resumes
after the complete structure is loaded and valid again.

The quantum slot accepts one half of a paired entangled singularity. Put the other half in a powered AE2 Quantum Ring to
connect the core across dimensions. The quantum link consumes an additional 512 AE/t and one channel. A wired connection
and a conflicting remote network cannot be used at the same time.

**Dismantle** removes the structure in batches while keeping the controller. Recovered blocks go to the ME Network first,
then to the player's inventory; dismantling pauses safely if both are full.

## Recipe

<RecipeFor id="molecularmanipulator:omni_computation_controller" />
