---
navigation:
  parent: omnisequence-index.md
  title: "Event Horizon Singularity Hub"
  icon: molecularmanipulator:event_horizon_singularity_hub
  position: 1100
item_ids:
- molecularmanipulator:event_horizon_singularity_hub
- molecularmanipulator:singularity_base_casing
- molecularmanipulator:gravity_gilded_block
- molecularmanipulator:spacetime_anchor_pillar
- molecularmanipulator:singularity_ring_track
- molecularmanipulator:event_horizon_lens
- molecularmanipulator:event_horizon_flux_pillar
- molecularmanipulator:black_hole_capture_node
- molecularmanipulator:white_hole_resource_core
- molecularmanipulator:event_horizon_stabilizer
- molecularmanipulator:singularity_crystal_tower
- molecularmanipulator:singularity_base_stairs
- molecularmanipulator:singularity_base_slab
---

# Event Horizon Singularity Hub

The Event Horizon Singularity Hub collects resources continuously and duplicates items using Sequence Matter. Collection and duplication share its ME network and can operate independently.

<ItemGrid>
<ItemIcon id="molecularmanipulator:event_horizon_singularity_hub" />
<ItemIcon id="molecularmanipulator:singularity_ring_track" />
<ItemIcon id="molecularmanipulator:event_horizon_lens" />
<ItemIcon id="molecularmanipulator:black_hole_capture_node" />
<ItemIcon id="molecularmanipulator:white_hole_resource_core" />
<ItemIcon id="molecularmanipulator:singularity_crystal_tower" />
</ItemGrid>

## Construction and connection

1. Complete **Tier 3: Event Horizon** in the well and fabricate the hub parts. The controller and White Hole Resource Core each require a [Miniature Supernova](black_and_white_holes.md).
2. Gather JEI's structure materials, place the controller and enable **Projection**.
3. Clear obstructions and select **Build**. Construction uses your inventory before ME storage.
4. Connect ME power and channels, then check formation and network status in Overview.
5. Provide ME item storage for collected and copied products, and ME fluid storage for Sequence Matter.

Base Stairs and Slabs are crafted at a crafting table: six Base Casings in the stair shape yield four stairs; three casings in a row yield six slabs.

## Controller interface

| Page | Function |
| --- | --- |
| Overview | Operating status; projection, construction and dismantling |
| [Resource Collection](singularity_resource_collection.md) | Resource list; start and stop collection |
| [Matter Duplication](singularity_matter_duplication.md) | Sample and miniature black-hole slots; matter stock and energy use |

Start collection and wait for startup, then products enter ME. Stopping collection docks the structure. Black-hole matter generation and sample duplication do not require active resource collection.

## Quantum connection

Overview's quantum slot accepts only a **Quantum Entangled Singularity**. Put its partner in a powered AE2 quantum bridge to connect a remote network, including construction materials before formation. The link requires an additional **512 AE/t and one channel**.

## Dismantling and relocation

Stop collection and wait for docking before selecting **Dismantle**. The controller stays in place. Materials return to ME before your inventory, pausing if there is no room.

Normal controller removal retains the quantum slot, sample, miniature black holes and pending recovery contents. Continuous production needs loaded chunks; the server's multiblock chunk-loading setting can keep the structure's required chunks loaded.

## Recipes

<RecipeFor id="molecularmanipulator:event_horizon_singularity_hub" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<RecipeFor id="molecularmanipulator:white_hole_resource_core" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<SubPages icons={true} />
