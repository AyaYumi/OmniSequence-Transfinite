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

A building package carries an entire machine as one item. Order new buildings through ME and unfold them with a Star Chart Calibrator. To move an installed machine with its contents, reclaim it through its own panel. You can relocate between dimensions of the same world save.

<Row>
<BlockImage id="molecularmanipulator:folding_assembler" scale="4" />

<ItemImage id="molecularmanipulator:folded_building_package" scale="4" />

<ItemImage id="molecularmanipulator:star_chart_calibrator" scale="4" />
</Row>

Left to right: Spatial Folding Assembler, building package and Star Chart Calibrator. Use the assembler to order packages and the reusable calibrator to choose a site and unfold them.

## Requirements

- For a new building: an assembler, an ME network with autocrafting, and the building's controller and full materials. The assembler uses ordinary AE2 ingredients and is available before your first well. Building ingredients still follow their own recipes and research requirements.
- For relocation: the installed machine, a calibrator and a free inventory slot for the reclaimed package.
- At the destination: enough clear space, build permission and vertical room. One empty block for the controller is not enough.

### New packages and sealed packages

| Package | What it contains |
| --- | --- |
| A new package ordered through ME | A standard building, including its controller and full construction materials. Install optional well ports and Pattern Assemblies separately. |
| A sealed package reclaimed from a machine | Your installed machine, including ports, assemblies, inventories, fluids, settings, research, processing progress and wireless binding. |

Keep both the package item and its building archive in the world save. Back up or transfer the complete world folder. Copying only the item to another save does not move the building.

## Step 1: order a new building through ME

1. Connect a Spatial Folding Assembler to a powered ME network with item storage and a crafting CPU.
2. Find and order the building package in your ME terminal. The assembler needs no patterns.
3. ME consumes the building's controller and all standard construction blocks, then delivers one package. Take it into your inventory.

| Complete packages you can order | Layout and materials |
| --- | --- |
| Matter Fabrication Well | [Matter Fabrication Well](matter_fabrication_well.md) |
| Sequence Array | [Sequence Array](sequence_array_controller.md) |
| Omni Computation Core | [Omni Computation Core](omni_computation_core.md) |
| Event Horizon Singularity Hub | [Event Horizon Singularity Hub](event_horizon_singularity_hub.md) |
| Star Orrery L1, L2 and L3 | [Star Computation Engine](star_computation_engine.md). Each is a complete building with its center, foundation and first 1, 2 or 3 rings. Upgrades use individual materials. |

JEI's structure categories show full materials and package outputs, with rotation, panning, zoom and layer controls. Creative inventory also offers these seven new-building packages. Orrery L0 and L4 to L9 packages come from whole-machine reclaim and cannot be ordered through ME.

## Step 2: survey and unfold

1. Keep the package in your inventory, hold the calibrator and right-click the site. Select the package in the panel.
2. Use the X/Y/Z buttons to adjust its position and rotate to change the horizontal facing. Each click moves 1 block; hold Shift to move 8.
3. Check the height. Base Y is the elevation of the lowest physical block. An orrery's core is at base Y + 37. A hub's controller is at base Y + 65, within a 97×97×129-block building.
4. Inspect the projection. Pale blue marks the building, gold marks its boundary and red marks conflicts. Clear obstacles, including spaces that must remain empty, such as the light well.
5. Confirm deployment and wait for the building to unfold. It pauses while its contents are restored. When deployment finishes, the complete building stands at the site, the package is consumed and the calibrator remains.

Press Esc to close the panel and walk around the projection. The site stays selected. Right-click the calibrator again to reopen it, even when aiming elsewhere. Select Cancel point before choosing a different site. This leaves construction already underway running.

During unfolding, the package stays in its original inventory slot and cannot be moved. To withdraw unfinished construction, reopen the panel and choose Interrupt and reclaim building. After cleanup, the original package can unfold again. Keep the package while construction is in progress and use this action instead of dismantling the unfinished structure. Construction interrupted by a server restart continues when the server returns.

## Moving an installed machine

1. Finish construction, dismantling or upgrades. Finish or cancel external ME CPU jobs on the machine's network and wait for materials, outputs and refunds to settle. Stop resource collection on a hub too.
2. Open the machine's panel, choose Reclaim package or its reclaim action and confirm as prompted. For an orrery or hub, click again about 0.25 to 3 seconds after the first click.
3. Collect one sealed package. If your inventory was full, free a slot and use the calibrator in the air to collect it. The calibrator panel's Archive contents button lets you inspect the package's components.
4. Follow Step 2 at the destination in the same save. Restore the chosen ME connection and external pipes, check contents and settings, then resume work. Port directions rotate with the building.

Whole-machine reclaim supports the Matter Fabrication Well, Sequence Array, multiblock Omni Computation Core, Event Horizon Singularity Hub and formed Star Orrery. The Transfinite Compute Nexus and Compact Singularity Hub do not use it. Right-clicking the calibrator opens surveying; use the machine's own panel to reclaim it.

Sealing retains items, fluids, patterns, catalysts, invested materials, processing progress, pending products, refunds and wireless bindings. Settle external ME CPU crafting jobs first, as those jobs do not travel with the package.

For a wireless receiver, keep external ME cables removed and the quantum slot empty after deployment. The saved binding can reconnect to the same hub when both ends are loaded and ownership and access still match. Wireless does not load chunks. If you break, move or replace the Sequence Wireless Hub itself, its new identity requires each receiver to be rebound. See [Sequence Wireless Hub setup](sequence_array_controller.md).

An orrery's reclaimed package is graded L0 to L9 by its consecutive complete rings starting at ring 1. The original purchase grade does not determine the reclaimed grade. A hub docks its moving parts before sealing; keep the whole motion area loaded and clear. Its reclaim request survives restarts and can continue when the requester returns. Cancelling reclaim withdraws sealing while safe docking continues. Start hub collection manually after deployment.

Each sealed building can deploy once. Copying its package item does not create a second machine.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| No packages in ME | Check the assembler's powered connection and whether the terminal shows craftable items. |
| Cannot confirm deployment | Wait for surveying, clear red conflicts and check height, world border and build permission. |
| Right-click keeps opening the old site | Select Cancel point before choosing a new site. Esc only closes the panel. |
| Construction continues after Cancel point | Reopen the panel and use Interrupt and reclaim building. |
| Machine is busy and will not seal | Finish construction or research/calculation jobs and settle external ME CPU jobs. A hub must also stop collection and dock. |
| Sealed package reports an unavailable archive | Keep the item. Check that you are in the original save and recover the archive from a complete-world backup. |

## Tool recipes

<RecipeFor id="molecularmanipulator:folding_assembler" />

<RecipeFor id="molecularmanipulator:star_chart_calibrator" />
