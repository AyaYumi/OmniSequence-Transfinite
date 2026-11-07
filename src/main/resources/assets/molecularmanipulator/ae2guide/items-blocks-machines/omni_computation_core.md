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

The Omni Computation Core is a large ME crafting CPU. High-tier Star Computation Engine benefits let it run multiple independent requests. It manages jobs; the network's Pattern Providers and machines perform the recipes.

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

Without Star Computation Engine benefits, the core has **1 MiB of crafting storage and parallelism 16**, using its physical CPU rather than unlimited virtual jobs. The [Star Computation Engine](star_computation_engine.md) raises these limits; L7 enables virtual CPU lanes, and only paid L9 overclocking provides unlimited storage and parallelism. Large orders still need ingredients, energy, Pattern Providers, machines and output space.

Ordinary providers adapt to the number of complete recipes their machines accept: calls grow in the same tick until the provider becomes busy, with another capacity probe after 20 ticks. A final single craft can still run without waiting for a batch. Returned tools and deterministic durability changes are reused for later crafts where supported; they are not copied or silently replaced with fresh tools.

Cyclic recipes need initial seed materials. Blocked cycle phases yield to other runnable work instead of monopolizing dispatch. If a job stalls, check missing ingredients, seeds and machine input/output conditions in crafting status. Optional ECO compatibility requires **21.2.10+** and preserves job identity through full batches, final single crafts and blocking fallback.

With Data Energistics **3.3.0 or 3.3.5**, the Digital Storage Depot's simulation counts actual room in each slot once. It does not enlarge storage or insert items during the capacity check.

The controller's **Effects** button selects 0 off, 1 reduced or 2 full for this machine and its viewers. Shift-click restores each client's global effects setting; production is unchanged.

## Quantum connection

Place one **Quantum Entangled Singularity** in the quantum slot and its partner in a powered remote AE2 quantum bridge. The link requires an additional **512 AE/t and one channel**.

## Recovery and structure updates

Structure damage and chunk unloading retain tasks, materials and progress until operating conditions recover. Normal controller removal carries jobs and quantum contents with the block.

**Reclaim Package** seals the controller, installed structure and contents into a lightweight package backed by this world's archive. Finish or cancel and settle crafting jobs first. See [Folded Buildings and Relocation](folded_buildings.md). Server-enabled multiblock chunk loading keeps required chunks loaded during operation, construction and updates. Occupied chunks prevent natural mob spawning.

Legacy **1.3.9** radial structures support **Update Structure**. Inspect the new projection and materials first. Click once to confirm, then again within five seconds to execute. The update relocates the controller and its jobs.

For a compact CPU, see [Transfinite Compute Nexus](transfinite_compute_nexus.md).

## Recipes

<RecipeFor id="molecularmanipulator:omni_computation_controller" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
