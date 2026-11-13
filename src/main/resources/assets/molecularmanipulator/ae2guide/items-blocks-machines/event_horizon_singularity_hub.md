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

This building supplies resources to ME continuously. Resource Collection produces the items in its list together. Matter Duplication spends Sequence Matter to copy a sample you choose. These operations run independently.

<ItemGrid>
<ItemIcon id="molecularmanipulator:event_horizon_singularity_hub" />
<ItemIcon id="molecularmanipulator:singularity_ring_track" />
<ItemIcon id="molecularmanipulator:event_horizon_lens" />
<ItemIcon id="molecularmanipulator:black_hole_capture_node" />
<ItemIcon id="molecularmanipulator:white_hole_resource_core" />
<ItemIcon id="molecularmanipulator:singularity_crystal_tower" />
</ItemGrid>

Shown in order: controller, track, lens, capture node, resource core and crystal tower. Operate the hub through its controller. Products go directly into ME.

## What you need

- Complete Tier IV: Event Horizon Materials at the well for structure parts, then Tier V: Stellar Cores for the controller and White Hole Resource Core. Each of those two needs a [Miniature Supernova](black_and_white_holes.md). JEI's hub structure page lists the full materials.
- Reserve **97×97 blocks across and 129 blocks tall** for the stationary building. Its controller is at **base Y + 65**; base Y is the lowest physical block's elevation. Keep the central light well empty and leave room around the building for moving parts.
- Prepare ME power, available channels and enough item storage. Matter generation and copying also need fluid storage; see [Matter Duplication](singularity_matter_duplication.md).

## Recommended: unfold a building package

<Row>
<BlockImage id="molecularmanipulator:folding_assembler" scale="4" />

<ItemImage id="molecularmanipulator:folded_building_package" scale="4" />

<ItemImage id="molecularmanipulator:star_chart_calibrator" scale="4" />
</Row>

The package includes the controller and all standard construction blocks. Use a Star Chart Calibrator to choose the site and unfold it.

1. Connect a Spatial Folding Assembler to an ME network with autocrafting and order a hub package. The order consumes the controller and full materials.
2. Keep the package in your inventory and **right-click the site with a Star Chart Calibrator**. Select the package, position and facing.
3. Inspect the pale blue projection and gold boundary. Clear red conflicts, including obstructions in the light well, then confirm deployment.
4. Wait for unfolding to finish, connect ME and check **formed** and **network online** in the controller's **Overview**. The calibrator is reusable.

See [Folded Buildings and Relocation](folded_buildings.md) for surveying and interrupting construction. JEI's structure preview supports rotation, panning, zoom and layer controls.

### Already have a controller? Use automatic construction

Place it, enable **Projection**, prepare the JEI materials and select **Build**. Materials come from your inventory first, then ME. Base Stairs and Slabs use crafting-table recipes: 6 casings make 4 stairs, or 3 casings in a row make 6 slabs.

## Start production

Once Overview shows **formed** and **network online**, choose the page for your goal:

| Your goal | Action and success sign |
| --- | --- |
| Collect raw ores, logs and other resources | [Resource Collection](singularity_resource_collection.md) → Start Collection. After startup, resource counts rise in ME. |
| Generate Sequence Matter fluid | [Matter Duplication](singularity_matter_duplication.md) → insert miniature black holes and supply energy. With no sample, watch ME fluid stock rise. |
| Copy a chosen item | [Matter Duplication](singularity_matter_duplication.md) → insert a sample and provide matter. Copies appear in ME; the sample stays. |

**Stop Collection** docks the moving parts, but does not stop matter generation or copying. Remove black holes or the sample to stop those operations. Lowering **Effects** in Overview changes visuals, not output.

### Remote ME connections

Use Remote connection in the right sidebar to bind this machine to a Sequence Wireless Hub. Follow the [wireless setup](sequence_array_controller.md) for requirements.

For a built-in quantum connection, put a Quantum Entangled Singularity in Overview's quantum slot and its partner in a powered, loaded AE2 quantum bridge. It can supply construction materials before formation.

Use one connection method at a time. Disconnect the wireless hub and remove external ME cables before using the quantum slot. The quantum link costs an additional **512 AE/t** and still needs channels. The pair must be free of other endpoints. After a restart, allow time for the network to reconnect.

## Relocation

1. Stop collection and settle jobs. Finish construction or upgrades. Finish or cancel external ME CPU jobs on its network and wait for materials, outputs and refunds to settle.
2. Select Reclaim package in Overview, then confirm as prompted. At normal speed, leave about **0.25 to 3 seconds** between clicks.
3. Let moving parts dock safely and collect one sealed package. It retains stored items and fluids, quantum singularity, sample, black holes, processing progress and pending deliveries, along with the machine's layout, settings and wireless binding.
4. Use the calibrator to unfold it elsewhere, including another dimension, within the same world save. Restore the chosen ME connection and check storage before starting collection manually. If the wireless hub was moved or replaced, rebind this machine.

While waiting for docking, keep the motion area loaded and clear, and have the requesting player return to operate it. Restarts or that player's temporary absence do not discard the reclaim request. **Cancel** withdraws sealing while safe docking continues. If your inventory was full, free a slot and use the calibrator in the air to collect the package.

**A sealed package needs its building archive in the original save.** Copying just the item to another save does not move the building. Back up or transfer the complete world folder. See [Folded Buildings and Relocation](folded_buildings.md). The [compact hub](compact_singularity_hub.md) does not use this whole-machine reclaim action.

## Quick fixes

| Problem | What to check |
| --- | --- |
| Cannot form or deploy | Check height, red conflicts, missing materials and the empty central light well. |
| Formed, but no products | Connect ME, start collection and provide item space for every resource. For copying, check the sample and matter separately. |
| Motion or docking stops | Clear obstacles and load chunks across the **whole motion area**, not just the controller. |
| Reclaim keeps waiting | Stop collection, settle jobs, have the requester present and let the moving parts dock. |

The server's multiblock chunk-loading switch controls whether this mod keeps chunks loaded. With it off, starting, motion and docking still work when all required chunks are loaded by other means. Missing chunks make the machine wait.

## Recipe lookup

<RecipeFor id="molecularmanipulator:event_horizon_singularity_hub" fallbackText="Check JEI for the well recipe and research requirements." />

<RecipeFor id="molecularmanipulator:white_hole_resource_core" fallbackText="Check JEI for the well recipe and research requirements." />

<SubPages icons={true} />
