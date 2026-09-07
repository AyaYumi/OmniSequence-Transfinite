---
navigation:
  parent: omnisequence-index.md
  title: Omni-Computation Core
  icon: molecularmanipulator:omni_computation_controller
  position: 1020
item_ids:
- molecularmanipulator:omni_computation_controller
- molecularmanipulator:omni_computation_casing
- molecularmanipulator:omni_computation_glass
- molecularmanipulator:infinite_parallel_matrix
- molecularmanipulator:infinite_crafting_storage
- molecularmanipulator:universal_pattern_matrix
- molecularmanipulator:computation_data_entangler
- molecularmanipulator:computation_energy_stabilizer
- molecularmanipulator:computation_output_node
- molecularmanipulator:computation_crystal_pylon
---

# Omni-Computation Core

<BlockImage id="molecularmanipulator:omni_computation_controller" scale="8" />

The Omni-Computation Core is a floating 65x65x35 end-game [crafting CPU](ae2:items-blocks-machines/crafting_cpu_multiblock.md).
When formed and online, it provides effectively unlimited logical crafting storage and parallelism.

This machine requires AdvancedAE. Its controller and components are produced in the [Matter Fabrication Well](matter_fabrication_well.md)
after the first completion of **Stage II: Omni-Computation** research. Defaults require one stage-one completion and 30 seconds
per stage-two round. Without AdvancedAE, that branch and its recipes are unavailable.

## Building the structure

1. Place the controller facing outward. The 65x65x35 structure needs 17 buildable blocks below and 17 above it,
   extending 32 blocks to either side, 22 in front and 42 behind.
2. Right-click the controller and enable the projection. Ghost blocks show missing positions, while red outlines show
   conflicts.
3. Use the JEI structure category for the complete layer view and material list.
4. Clear conflicting blocks, then use **Build**. Missing materials are taken from the player's inventory first and then
   from the connected ME Network.
5. Connect and power the controller, then use **Check** if the structure does not form immediately.

The enlarged celestial crown has three distinct layers: one complete horizontal outer ring, two smaller complete
orbital rails inclined about 45 degrees and crossing each other, and an open spherical cage at the center. The outer
centerline radius is 31 blocks, the inner tracks have a 23.5-block radius, and the cage ribs have a 10.5-block radius.
Clear space separates these layers. Six crystal brackets belong to the outer ring; no separate front tray or broken
front ring section remains.

The controller stays on the cage's front equator, 10 blocks in front of the star, with a clear access window. The star
and its small gyroscopic halos scale with the enlarged body. Light traces follow the actual three orbital tracks.
The client receives the server's validated structure layout before selecting its matching visual effects.

Legacy support retains only the official 1.3.9 radial core (31×31×39), labeled **Legacy 1.3.9**. Other historical and experimental layouts are no longer recognized or migrated.
This version changes the building substantially: open the projection from the legacy prompt first. Click **Update Structure**, wait briefly, then click again to confirm within five seconds.
Updating recovers the old structure and builds the current layout, requiring materials and recovery space. The controller moves fifteen blocks up and five blocks behind its old position; active jobs and quantum-slot contents are retained.

An AE2 wrench rotates the controller and recalculates the structure.

## Autocrafting

The core creates virtual CPU lanes and keeps an idle lane available. Once formed and online, crafting requests on its network
can use the AELIS planner supplied by AppliedEnhancements. Cyclic crafting still needs a valid starting seed and all other
ingredients. A successful plan does not remove material or power requirements.

Dispatch adapts to machine acceptance and server load. Ingredients, energy, backpressure and server tick time still limit actual throughput.

## Persistence and remote access

The core force-loads required chunks while formed and during construction, dismantling or structure updates. Normal controller
drops retain stored contents, active jobs and quantum-slot contents. Restore the structure and network after replacing it.

If part of the structure is broken or unloaded, active jobs, stored ingredients, and progress are preserved. Work resumes
after the complete structure is loaded and valid again.

The quantum slot accepts one half of a paired entangled singularity. Put the other half in a powered AE2 Quantum Ring to
connect the core across dimensions. The quantum link consumes an additional 512 AE/t and one channel. A wired connection
and a conflicting remote network cannot be used at the same time.

**Dismantle** removes the structure in batches while keeping the controller. Recovered blocks go to the ME Network first,
then to the player's inventory; dismantling pauses safely if both are full.
The queue contains only actual matching blocks of the selected layout. It completes layers from highest to lowest with serpentine rows, skips air and externally changed targets without using the removal budget, and resumes the same queue after pauses or reloads instead of scanning all historical layouts.

## Recipe

<RecipeFor id="molecularmanipulator:omni_computation_controller" fallbackText="This recipe requires AdvancedAE and is unavailable if the dependency is absent or the modpack removes it." />
