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

Order a complete building through ME and unfold it with a reusable Star Chart Calibrator. Seal an installed machine into one package to move its contents, including across dimensions within the same world.

<ItemGrid>
<ItemIcon id="molecularmanipulator:folding_assembler" />
<ItemIcon id="molecularmanipulator:folded_building_package" />
<ItemIcon id="molecularmanipulator:star_chart_calibrator" />
</ItemGrid>

## ME orders

1. Craft a **Spatial Folding Assembler** and connect it to a powered ME network with item storage and a crafting CPU.
2. The network advertises complete packages for the **Matter Fabrication Well, Sequence Array, Omni Computation Core and ordinary Star Computation Engine**.
3. Order one package. The job consumes the **controller and every standard construction block**, then returns **one complete package** to ME.

The assembler uses ordinary AE2 ingredients and is available before the first well. Building ingredients retain their original recipes and research requirements. Packages arrive ready to use.

Fresh packages contain the standard structure. Install optional well assemblies and ports as needed; sealing an existing machine includes the parts actually installed.

## Survey and unfold

Keep the package in your inventory and **use the calibrator on the site**. Select a package, adjust X/Y/Z and rotate horizontally. Hold Shift for eight-block adjustments. **Base Y is the lowest physical block's elevation**; building dimensions are fixed.

Pale blue outlines show occupied positions, the gold frame shows the boundary, and red outlines identify conflicts. Required empty spaces also participate in site validation. Clear conflicts before confirming.

Unfolding progresses through **star chart → foundation → rising walls → descending core**. The structure remains paused until all native contents are restored and saved. The package is consumed on successful completion; the calibrator remains reusable. Cancelling the survey keeps the package.

A sealed package's **Archive contents** button opens paged component summaries, relative positions and stored data sizes. Full contents remain in the server's world files.

## Seal an installed machine

1. Finish or cancel external ME CPU jobs on the machine's grid and allow their materials, outputs and refunds to settle. Finish construction or dismantling.
2. **Sneak-use the calibrator on the controller** and confirm sealing.
3. Collect one package, then survey and unfold it at the destination. If your inventory is full, free a slot and use the calibrator in the air to collect it.

Sealing preserves the controller, installed structure parts, ports and assemblies, with native patterns, catalysts, items, fluids, names, settings, research, invested materials, internal progress, pending outputs and refunds. The array controller saves before its 14 crystals so pattern shards synchronize first. Rotated deployment also rotates port output faces and rebuilds controller connections and ME nodes.

## World archives and recovery

Sealed packages work in ME storage, across restarts and across dimensions of the same world. They carry a small archive reference; complete native data lives under `data/molecularmanipulator/buildings/` in the world folder. **Back up and transfer the complete world folder**.

During deployment the package remains in its inventory slot as a transfer receipt. Interrupted transfers resume on server restart. A completed archive cannot deploy again. An unavailable archive leaves the package and existing archive data available for recovery.

Copying the item to another save does not transfer its archive. Cross-save export requires a separate export workflow.

## Recipes

<RecipeFor id="molecularmanipulator:folding_assembler" />
<RecipeFor id="molecularmanipulator:star_chart_calibrator" />
