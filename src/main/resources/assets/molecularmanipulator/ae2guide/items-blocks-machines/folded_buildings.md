---
navigation:
  parent: omnisequence-index.md
  title: "Folded Buildings and Relocation"
  icon: molecularmanipulator:folded_building_package
  position: 890
item_ids:
- molecularmanipulator:folded_building_package
- molecularmanipulator:folding_assembler
- molecularmanipulator:star_chart_calibrator
---

# Folded Buildings and Relocation

**A building package turns an entire machine into one portable item.** Order new buildings through ME and unfold them with a Star Chart Calibrator. Reclaim an installed machine through its own panel to move it with its contents, including between **dimensions of the same world save**.

<Row>
<BlockImage id="molecularmanipulator:folding_assembler" scale="4" />

<ItemImage id="molecularmanipulator:folded_building_package" scale="4" />

<ItemImage id="molecularmanipulator:star_chart_calibrator" scale="4" />
</Row>

Left to right: **the Spatial Folding Assembler takes the order → the package carries the building → the Star Chart Calibrator selects the site and unfolds it**. The calibrator is reusable.

## What you need

- For a new building: an assembler, an ME network with autocrafting, and the building's controller and full materials. The assembler uses ordinary AE2 ingredients and is available before your first well. Building ingredients still follow their own recipes and research requirements.
- For relocation: the installed machine, a calibrator and a free inventory slot for the reclaimed package.
- At the destination: enough clear space, build permission and vertical room. One empty block for the controller is not enough.

### New packages and sealed packages

| Package | What it contains |
| --- | --- |
| A new package ordered through ME | A standard building, including its controller and full construction materials. It is neither an empty shell nor free construction. Install optional well ports and Pattern Assemblies separately. |
| A sealed package reclaimed from a machine | Your installed machine, including its ports, assemblies, inventories, fluids, settings, research and processing progress. |

**The package is the item you carry; the building archive is the complete machine stored in the world save. Keep both.** Keep the item safe, and back up or transfer the **complete world folder**. Copying only the package item to another save does not move the building.

## Step 1: order a new building through ME

1. Connect a **Spatial Folding Assembler** to a powered ME network with item storage and a crafting CPU.
2. Find and order the building package in your ME terminal. You do not need to insert patterns into the assembler.
3. ME consumes the building's **controller and all standard construction blocks**, then delivers one package. Take it into your inventory.

| Complete packages you can order | Layout and materials |
| --- | --- |
| Matter Fabrication Well | [Matter Fabrication Well](matter_fabrication_well.md) |
| Sequence Array | [Sequence Array](sequence_array_controller.md) |
| Omni Computation Core | [Omni Computation Core](omni_computation_core.md) |
| Event Horizon Singularity Hub | [Event Horizon Singularity Hub](event_horizon_singularity_hub.md) |
| Star Orrery L1, L2 and L3 | [Star Computation Engine](star_computation_engine.md). These include the first 1, 2 or 3 rings and the complete central body and foundation: **not incremental upgrade packages**. |

JEI's structure categories show full materials and package outputs, with rotation, panning, zoom and layer controls. Creative inventory also offers these seven new-building packages. Orrery **L0 and L4–L9** packages come from whole-machine reclaim, not ME orders.

## Step 2: survey and unfold

1. **Keep the package in your inventory, hold the calibrator and right-click the site.** Select the package in the panel.
2. Use X/Y/Z buttons to adjust its position and rotate to change the horizontal facing. Each click moves 1 block; hold Shift to move 8.
3. Check the height: **base Y is the lowest physical block's elevation**, not the controller height. An orrery's core is at **base Y + 37**. A hub's controller is at **base Y + 65**, within a **97×97×129-block** building.
4. Inspect the projection: **pale blue marks the building, gold marks its boundary and red marks conflicts**. Clear obstacles, including spaces that must remain empty, such as the light well.
5. Confirm deployment and wait for the foundation, walls and core to settle into place. The machine pauses while its contents are restored. On completion, the package is consumed and the calibrator remains.

**Want to walk around the projection? Press Esc to close the panel.** The site stays selected. Right-click the calibrator again to reopen it, even when aiming elsewhere. Only **Cancel point** releases the site for a new selection; it does not stop construction already underway.

During unfolding, the package temporarily stays in its original inventory slot and cannot be moved. To withdraw unfinished construction, reopen the panel and choose **Interrupt and reclaim building**. After cleanup, the original package can unfold again. Do not dismantle the half-built structure yourself.

## Moving an installed machine

1. **Settle jobs first.** Finish construction, dismantling or upgrades. Finish or cancel external ME CPU jobs on its network and wait for materials, outputs and refunds to settle. Stop resource collection on a hub too.
2. Open the machine's own panel, choose **Reclaim package** or its reclaim action and confirm as prompted. For an orrery or hub, click again about **0.25–3 seconds** after the first click.
3. Collect one sealed package. If your inventory was full, free a slot and use the calibrator in the air to collect it.
4. Follow Step 2 at the destination in the same save. Reconnect external ME cables and pipes, check contents and settings, then resume work. Port directions rotate with the building.

Whole-machine reclaim supports the **Matter Fabrication Well, Sequence Array, multiblock Omni Computation Core, Event Horizon Singularity Hub and formed Star Orrery**. The Transfinite Compute Nexus and Compact Singularity Hub do not use it. Right-clicking the calibrator opens surveying; it does not directly dismantle a machine.

Sealing retains the machine's items, fluids, patterns, catalysts, invested materials, processing progress, pending products and refunds. **External ME CPU crafting jobs do not travel with the package**, so settle them first.

An orrery's reclaimed package is graded L0–L9 by its **consecutive complete rings starting at ring 1**, not the grade originally purchased. A hub docks its moving parts before sealing; keep the whole motion area loaded and clear. Its reclaim request survives restarts and can continue when the requester returns. Cancelling reclaim withdraws sealing while safe docking continues. Start hub collection manually after deployment.

## How to tell it worked

- New construction: the ME order delivers a named package. After unfolding, the complete building stands at the site and the package is consumed.
- Relocation: the old machine has been reclaimed and its panel opens at the new site, with inventories, fluids, patterns and settings retained. A sealed package's **Archive contents** button in the calibrator panel lets you check archived components.
- Each sealed building can deploy once. Copying the package item does not create a second machine.

## Quick fixes

| Problem | What to check |
| --- | --- |
| No packages in ME | Check the assembler's powered connection and whether the terminal shows craftable items. |
| Cannot confirm deployment | Wait for surveying, clear red conflicts and check height, world border and build permission. |
| Right-click keeps opening the old site | Select Cancel point before choosing a new site. Esc only closes the panel. |
| Construction continues after Cancel point | Reopen the panel and use Interrupt and reclaim building. |
| Machine is busy and will not seal | Finish construction or research/calculation jobs and settle external ME CPU jobs. A hub must also stop collection and dock. |
| Sealed package reports an unavailable archive | Keep the item. Check that you are in the original save and recover the archive from a complete-world backup. |

An interrupted relocation continues after the server restarts. To change sites, use the panel's interrupt-and-reclaim action rather than discarding a package still under construction.

## Tool recipes

<RecipeFor id="molecularmanipulator:folding_assembler" />

<RecipeFor id="molecularmanipulator:star_chart_calibrator" />
