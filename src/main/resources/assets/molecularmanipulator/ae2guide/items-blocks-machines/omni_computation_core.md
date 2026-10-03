---
navigation:
  parent: omnisequence-index.md
  title: "Omni Computation Core"
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

# Omni Computation Core

A large ME crafting CPU. Form the floating crown to manage multiple crafting requests at once.

## Structure and parts

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

| Preparation | Requirement |
| --- | --- |
| Unlock | Tier 2: Omni Computation in the well |
| Bounds | 65 × 65 blocks, 35 blocks tall |
| Relative to controller | 17 above/below, 32 left/right |
| Front/rear clearance | 22 in front, 42 behind |

1. Fabricate the controller and parts. Use **Projection** and the JEI structure page to inspect the site.
2. Clear obstructions and select **Build**. Player inventory materials are taken before ME materials.
3. Connect a powered, online ME network and verify the formed structure.
4. Request crafting from an ME terminal. The core creates independent CPU lanes automatically.

## Crafting operation

<Row>
<BlockImage id="molecularmanipulator:infinite_crafting_storage" scale="4" />

<BlockImage id="molecularmanipulator:infinite_parallel_matrix" scale="4" />

<ItemImage id="ae2:crafting_terminal" scale="4" />
</Row>

| Core provides | You still supply |
| --- | --- |
| Large logical crafting storage | Required ingredients |
| Parallel virtual CPU lanes | Providers and machines that accept inputs |
| Accelerated planning while online | Energy and output capacity |

Cyclic recipes still need seed materials. Dispatch adapts to receiving machines and server load; logical capacity is not a fixed production rate.

## Quantum access and persistence

Insert one paired Quantum Entangled Singularity into the quantum slot and the other into a remote AE2 quantum bridge. The link adds **512 AE/t and one channel**. Resolve conflicting networks before connecting.

| Situation | Result |
| --- | --- |
| Structure damage or unloading | Retain jobs, ingredients, and progress until restored |
| Normal controller removal | Carry jobs and quantum inventory with the block |
| Dismantle | Keep the controller; return materials to ME, then the player |
| No recovery space | Pause and retain dismantling progress |

The multiblock chunk-loading setting keeps needed chunks loaded during formation, construction, and updates. Occupied chunks have natural-spawning protection.

## Update a legacy structure

The official **1.3.9** radial core supports **Update Structure**. Inspect the new projection and material requirements first. Click once to confirm, then again within five seconds to execute. The controller and jobs migrate to the new position.

## Controller recipe

<RecipeFor id="molecularmanipulator:omni_computation_controller" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

For a single-block CPU, see [Transfinite Compute Nexus](transfinite_compute_nexus.md).
