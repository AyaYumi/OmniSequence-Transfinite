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

The Omni Computation Core is a large ME crafting CPU that runs multiple independent crafting requests. It manages jobs; the network's Pattern Providers and machines perform the recipes.

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

## Construction

Complete **Tier 2: Omni Computation** in the well, then fabricate the controller and structure parts.

| Clearance | Bounds |
| --- | --- |
| Overall structure | 65 × 65 blocks, 35 blocks tall |
| Relative to controller | 17 above and below; 32 left and right |
| Front and rear | 22 in front; 42 behind |

1. Place the controller, check JEI's material list and enable **Projection**.
2. Clear obstructions, gather materials and select **Build**. Your inventory is used before ME storage.
3. Connect a powered ME network with channels and check formation.
4. Request crafting from an ME terminal and monitor jobs through AE2's crafting status interface.

## Crafting requests

The core provides independent crafting capacity for multiple requests and can continue accepting new orders. Large orders still require ingredients, energy, Pattern Providers, machines and output space.

Cyclic recipes need initial seed materials. If a job stalls, check missing ingredients in crafting status and whether the required machines can receive inputs and return products.

## Quantum connection

Place one **Quantum Entangled Singularity** in the quantum slot and its partner in a powered remote AE2 quantum bridge. The link requires an additional **512 AE/t and one channel**.

## Recovery and structure updates

Structure damage and chunk unloading retain tasks, materials and progress until operating conditions recover. Normal controller removal carries jobs and quantum contents with the block.

**Dismantle** keeps the controller and returns materials to ME before your inventory. Recovery pauses if both are full. Server-enabled multiblock chunk loading keeps required chunks loaded during operation, construction and updates. Occupied chunks prevent natural mob spawning.

Legacy **1.3.9** radial structures support **Update Structure**. Inspect the new projection and materials first. Click once to confirm, then again within five seconds to execute. The update relocates the controller and its jobs.

For a compact CPU, see [Transfinite Compute Nexus](transfinite_compute_nexus.md).

## Recipes

<RecipeFor id="molecularmanipulator:omni_computation_controller" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
