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

Harvest basic resources simultaneously and generate Sequence Matter with miniature black holes to duplicate items. Both workflows use the hub's ME network.

## Build and connect

<ItemGrid>
<ItemIcon id="molecularmanipulator:event_horizon_singularity_hub" />
<ItemIcon id="molecularmanipulator:singularity_ring_track" />
<ItemIcon id="molecularmanipulator:event_horizon_lens" />
<ItemIcon id="molecularmanipulator:black_hole_capture_node" />
<ItemIcon id="molecularmanipulator:white_hole_resource_core" />
<ItemIcon id="molecularmanipulator:singularity_crystal_tower" />
</ItemGrid>

1. Complete **Tier 3: Event Horizon** in the well and fabricate the parts. The controller requires 10,000 Miniature Black Holes; the resource core requires 10,000 Miniature White Holes.
2. Gather the JEI structure materials, place the controller, and enable **Projection**.
3. Clear obstructions and select **Build**. Player inventory is used before ME materials.
4. Connect a powered ME network with channels and check structure and network status in Overview.
5. Prepare item storage for harvested products and fluid storage for Sequence Matter.

The quantum slot inside **Hub Overview** accepts only a **Quantum Entangled Singularity**. Put its partner in a powered AE2 quantum bridge to access remote construction materials before formation. The quantum link adds 512 AE/t and one channel.

All 13 hub part recipes now require **10,000 times the previous input quantities**, including miniature holes. Batch outputs, duration and power remain the same. The miniature holes’ own recipes still cost 100K AE2 Singularities and 1G AE2 Matter Balls respectively.

## Three pages

| Page | Purpose |
| --- | --- |
| Overview | Building and operating status; projection, build, and dismantle |
| [Resource Collection](singularity_resource_collection.md) | Full resource list; start and stop harvesting |
| [Matter Duplication](singularity_matter_duplication.md) | Sample and black-hole slots, matter stock, FE / AE usage |

Starting resource collection initiates startup and building motion. Stopping collection docks the rings. Sequence Matter generation works independently according to the black-hole and network conditions.

## Recover and relocate

**Dismantle** keeps the controller and returns materials to ME first, then the player. Full destinations pause recovery. Stop collection and let the building finish docking before proceeding.

Normal controller removal preserves the quantum slot, sample, miniature black holes, and pending recovery contents. The multiblock chunk-loading setting controls the building's required chunks.

## Related pages

<SubPages icons={true} />
