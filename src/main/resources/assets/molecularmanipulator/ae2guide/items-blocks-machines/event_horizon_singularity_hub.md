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

### Complete building package

Connect a **Spatial Folding Assembler** to ME to order an **Event Horizon Singularity Hub package**. Each order consumes one controller and every standard construction block in the current suspended design; creative inventory offers the same package. The hub's JEI structure page lists the full materials and package output, with rotation, panning, zoom and layer controls.

Use the **Star Chart Calibrator** to select the site, package and facing, then confirm to unfold and form the structure automatically. Allow a **97×97 footprint and 129 blocks of height**, with **controller Y = base Y + 65**. Clear obstructions from the 566 required empty light-well cells too. During unfinished construction, choose **Interrupt and reclaim building** in the calibrator panel; once cleanup finishes, the original package can unfold again. See [Folded Buildings and Relocation](folded_buildings.md).

## Controller interface

| Page | Function |
| --- | --- |
| Overview | Operating status; projection, construction and whole-machine reclaim |
| [Resource Collection](singularity_resource_collection.md) | Resource list; start and stop collection |
| [Matter Duplication](singularity_matter_duplication.md) | Sample and miniature black-hole slots; matter stock and energy use |

Start collection and wait for startup, then products enter ME. Stopping collection docks the structure. Black-hole matter generation and sample duplication do not require active resource collection.

The **Effects** button chooses 0 off, 1 reduced or 2 full for this controller and all viewers. Shift-click restores each client's global setting; production and motion rules are unchanged.

## Quantum connection

Overview's quantum slot accepts only a **Quantum Entangled Singularity**. Put its partner in a powered AE2 quantum bridge to connect a remote network, including construction materials before formation. The link requires an additional **512 AE/t and one channel**.

After reloading, let the loaded endpoints complete normal AE2 initialization to reconnect. A destroyed, unloaded or retuned remote ring is no longer mistaken for a live endpoint because of a stale frequency entry. A genuinely occupied pair still rejects the link.

## Reclaiming and relocation

1. Stop resource collection and finish any construction or structure upgrade in progress. Finish or cancel external ME CPU jobs on the hub's network and let materials, outputs and refunds settle.
2. Use the existing **reclaim action in Overview**: click once, then again **5–60 ticks later** (normally 0.25–3 seconds). This seals the controller and the whole machine as **one archived building package**.
3. Use the Star Chart Calibrator to survey and deploy it elsewhere in the same world. Deployment leaves collection, motion and reclaim stopped; start collection manually when ready.

If moving parts remain, sealing waits for safe docking. Keep the motion area loaded and clear. The pending request survives reloads and the requester's time offline, and can continue when the requester returns and conditions permit. **Cancel** prevents the eventual archive while docking continues safely.

The archive preserves the controller, its saved blueprint version, legacy collection nodes and required empty light-well cells (566 in the current suspended design). It retains native inventories, the Quantum Entangled Singularity and its frequency, sample, miniature black holes, duplication progress, pending outputs, fluids and refunds, buffered energy, effect settings, portable materials and recovery contents. Restoring keeps that structure version. **The compact single-block hub does not use this multiblock reclaim action.**

Normal controller removal retains the quantum slot, sample, miniature black holes and pending recovery contents. Continuous production needs loaded chunks; the server's multiblock chunk-loading setting can keep the structure's required chunks loaded.

That setting controls this mod's chunk tickets, not motion permission. With it off, the hub can still start, move and dock while every required chunk in the full motion area is loaded by other means. Missing chunks pause motion and block starting or docking; these checks do not synchronously load them.

## Recipes

<RecipeFor id="molecularmanipulator:event_horizon_singularity_hub" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<RecipeFor id="molecularmanipulator:white_hole_resource_core" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<SubPages icons={true} />
