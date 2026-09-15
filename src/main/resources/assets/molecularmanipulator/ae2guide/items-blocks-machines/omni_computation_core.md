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

The Omni-Computation Core is a floating 65x65x35 end-game [crafting CPU](ae2:items-blocks-machines/crafting_cpu_multiblock.md). When formed and online, it provides effectively unlimited logical crafting storage and parallelism.

<ItemGrid>
<ItemIcon id="molecularmanipulator:omni_computation_controller" />
<ItemIcon id="molecularmanipulator:omni_computation_casing" />
<ItemIcon id="molecularmanipulator:omni_computation_glass" />
<ItemIcon id="molecularmanipulator:infinite_parallel_matrix" />
<ItemIcon id="molecularmanipulator:infinite_crafting_storage" />
<ItemIcon id="molecularmanipulator:universal_pattern_matrix" />
<ItemIcon id="molecularmanipulator:computation_data_entangler" />
<ItemIcon id="molecularmanipulator:computation_energy_stabilizer" />
<ItemIcon id="molecularmanipulator:computation_output_node" />
<ItemIcon id="molecularmanipulator:computation_crystal_pylon" />
</ItemGrid>

> This is the multiblock machine in the Omni-Computation branch. For the placeable single-block CPU in the same branch, see the [Transfinite Compute Nexus](transfinite_compute_nexus.md).

## At a glance

| Property | Value |
| --- | --- |
| Structure | 65 × 65 × 35 |
| Space needed | 17 blocks below and 17 above the controller; 32 to either side, 22 in front, 42 behind |
| Unlocked by | Stage II: Omni-Computation, first completion |
| Controller requirement | One stage-one completion; 30 seconds per stage-two round |

Its controller and components are produced in the [Matter Fabrication Well](matter_fabrication_well.md).

## Building the structure

1. Place the controller facing outward.
2. Right-click the controller and enable the projection. Ghost blocks show missing positions, while red outlines show conflicts.
3. Use the JEI structure category for the complete layer view and material list.
4. Clear conflicting blocks, then use **Build**. Missing materials are taken from the player's inventory first and then from the connected ME Network.
5. Connect and power the controller, then use **Check** if the structure does not form immediately.

### How it is shaped

The enlarged celestial crown has three distinct layers:

| Layer | Radius |
| --- | --- |
| One complete horizontal outer ring | 31 blocks centerline |
| Two smaller complete orbital rails, inclined about 45 degrees and crossing each other | 23.5 blocks |
| An open spherical cage at the center | 10.5-block ribs |

Clear space separates these layers. Six crystal brackets belong to the outer ring; no separate front tray or broken front ring section remains.

The controller stays on the cage's front equator, 10 blocks in front of the star, with a clear access window. The star and its small gyroscopic halos scale with the enlarged body. Light traces follow the actual three orbital tracks. The client receives the server's validated structure layout before selecting its matching visual effects.

### Legacy 1.3.9

Legacy support retains only the official 1.3.9 radial core (31×31×39), labeled **Legacy 1.3.9**. Other historical and experimental layouts are no longer recognized or migrated.

> This version changes the building substantially: open the projection from the legacy prompt first. Click **Update Structure**, wait briefly, then click again to confirm within five seconds.

Updating recovers the old structure and builds the current layout, requiring materials and recovery space. The controller moves fifteen blocks up and five blocks behind its old position; active jobs and quantum-slot contents are retained.

An AE2 wrench rotates the controller and recalculates the structure.

## Autocrafting

The core creates virtual CPU lanes and keeps an idle lane available. Once formed and online, crafting requests on its network can use the accelerated planner when its normal conditions are met.

> Cyclic crafting still needs a valid starting seed and all other ingredients. A successful plan does not remove material or power requirements.

Dispatch adapts to machine acceptance and server load. Ingredients, energy, backpressure and server tick time still limit actual throughput.

## Persistence and remote access

* The core force-loads required chunks while formed and during construction, dismantling or structure updates.
* Normal controller drops retain stored contents, active jobs and quantum-slot contents. Restore the structure and network after replacing it.
* If part of the structure is broken or unloaded, active jobs, stored ingredients and progress are preserved. Work resumes after the complete structure is loaded and valid again.

### Quantum link

The quantum slot accepts one half of a paired entangled singularity. Put the other half in a powered AE2 Quantum Ring to connect the core across dimensions.

| Property | Value |
| --- | --- |
| Extra power | 512 AE/t |
| Channels | 1 |
| Conflict | A wired connection and a conflicting remote network cannot be used at the same time |

### Dismantling

**Dismantle** removes the structure in batches while keeping the controller. Recovered blocks go to the ME Network first, then to the player's inventory; dismantling pauses safely if both are full.

The queue contains only actual matching blocks of the selected layout. It completes layers from highest to lowest with serpentine rows, skips air and externally changed targets without using the removal budget, and resumes the same queue after pauses or reloads instead of scanning all historical layouts.

## Natural spawning protection

While formed or performing construction, dismantling or a structure update, this multiblock blocks natural spawning throughout the full height of its occupied chunks, including monsters, animals, aquatic mobs and bats. Patrol and reinforcement spawns are also blocked.

Spawners, spawn eggs, breeding, commands and existing mobs are unaffected. Protection does not require AE power; when no structure or operation owns a chunk, spawning there returns to normal.

## Recipe

<RecipeFor id="molecularmanipulator:omni_computation_controller" fallbackText="This recipe is unavailable when the research or recipe is disabled." />
