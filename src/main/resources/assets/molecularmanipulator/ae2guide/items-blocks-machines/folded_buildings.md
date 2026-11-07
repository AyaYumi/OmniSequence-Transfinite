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
2. The network advertises seven complete packages: **Matter Fabrication Well, Sequence Array, Omni Computation Core, Event Horizon Singularity Hub, Star Orrery L1, Star Orrery L2 and Star Orrery L3**.
3. Order one package. The job consumes the **controller and every standard construction block**, then returns **one complete package** to ME.

The assembler uses ordinary AE2 ingredients and is available before the first well. Building ingredients retain their original recipes and research requirements. Packages arrive ready to use.

Fresh packages contain the standard structure. Install optional well assemblies and ports as needed; sealing an existing machine includes the parts actually installed.

Star Orrery L1/L2/L3 contain the first 1/2/3 rings, using 24/56/96 **Reference anchors** respectively. Every package also includes the **complete central 3×3×3 assembly (1 core, 22 casing and 4 observation ports)** and the **complete foundation (520 engraved plinths and 408 gilded plinths)**. Total block counts are **979 / 1011 / 1051**: full materials, not incremental upgrade packages.

L0 formation requires both all 27 central blocks and all 928 foundation blocks; any missing central part prevents formation. The core is at the exact center, with an observation port at the center of each horizontal-facing side and casing in the other 22 positions. L1/L2/L3 are not the Reference/Quantum/Terminal tiers; benefits and research still follow the server-verified consecutive rings from ring 1 through L9. The old cube cannot form independently, old four-direction columns are unsupported, and joining or stacking structures grants no extra benefits.

All three packages are also available in creative inventory. JEI's **Star Orrery Structure** category lists all materials and the package output for each recipe, with the same left-drag rotation, right-drag panning, scroll zoom and layer controls as other buildings. See [Star Computation Engine](star_computation_engine.md) for the layout and full material table.

The Event Horizon Singularity Hub package includes its controller and every construction block in the current suspended design, and is also available in creative inventory. Its JEI structure page shows the full bill and package output, with rotation, panning, zoom and layer controls. The structure is 97×97 blocks across and 129 blocks tall: **controller Y = base Y + 65**. Its 566 required empty light-well cells participate in site checks. After formation, use the hub's interface to manage collection, motion and material reclaim. See [Event Horizon Singularity Hub](event_horizon_singularity_hub.md).

## Survey and unfold

Keep the package in your inventory and **use the calibrator on the site**. Select a package, adjust X/Y/Z and rotate horizontally. Hold Shift for eight-block adjustments. **Base Y is the lowest physical block's elevation**; building dimensions are fixed. The orrery foundation starts at Y=-37 relative to its core, so for L1/L2/L3 packages **core Y = base Y + 37**.

**Esc closes the panel without releasing the pinned site.** Walk, fly and look around freely; right-click with the calibrator again to reopen the same site, even when aiming elsewhere. Only **Cancel point** releases it for a new location. There is no persistent HUD or return-to-panel hotkey. To withdraw an unfinished deployment, reopen the panel and choose **Interrupt and reclaim building**. Sneak-use no longer directly seals a machine or withdraws construction.

Pale blue outlines show occupied positions, the gold frame shows the boundary, and red outlines identify conflicts. Required empty spaces also participate in site validation. Clear conflicts before confirming.

Unfolding progresses through **star chart → foundation → rising walls → descending core**. The structure remains paused until all native contents are restored and saved. The package is consumed on successful completion; the calibrator remains reusable. Cancelling the survey keeps the package.

Once an orrery forms, its core presents a **single 3×3×3 model** and hides the original meshes of the **26 other central blocks (22 casing and 4 observation ports)**. Their blocks, block entities, collision and drops remain in place; hidden does not mean absent. The existing foundation mesh-hiding and visual effects are unchanged.

A sealed package's **Archive contents** button opens paged component summaries, relative positions and stored data sizes. Full contents remain in the server's world files.

## Seal an installed machine

1. Finish or cancel external ME CPU jobs on the machine's grid and allow their materials, outputs and refunds to settle. Finish construction or dismantling.
2. For a Matter Fabrication Well, Sequence Array, multiblock Omni Computation Core or formed Star Orrery, confirm sealing through the **machine interface's reclaim action**, including its controller or core. The single-block computation terminal does not use this action. Right-clicking with the calibrator only opens the survey panel; it does not dismantle a machine.
3. Collect one package, then survey and unfold it at the destination. If your inventory is full, free a slot and use the calibrator in the air to collect it.

Sealing preserves the controller, installed structure parts, ports and assemblies, with native patterns, catalysts, items, fluids, names, settings, research, invested materials, internal progress, pending outputs and refunds. The array controller saves before its 14 crystals so pattern shards synchronize first. Rotated deployment also rotates port output faces and rebuilds controller connections and ME nodes.

An orrery's whole-machine archive includes **all 27 central blocks: 1 core, 22 casing and 4 observation ports**. Valid central casing and observation ports, verified as members of that formed orrery, route menus and ME connections to its **actual core**; they are not independent machines and are not left behind when sealing. Loose parts do not connect to ME or proxy menus.

## World archives and recovery

Sealed packages work in ME storage, across restarts and across dimensions of the same world. They carry a small archive reference; complete native data lives under `data/molecularmanipulator/buildings/` in the world folder. **Back up and transfer the complete world folder**.

During deployment the package remains in its inventory slot as a transfer receipt. Interrupted transfers resume on server restart. A completed archive cannot deploy again. An unavailable archive leaves the package and existing archive data available for recovery.

Old unsealed small engine templates can no longer be ordered or deployed and are not exchanged for larger orrery level packages for free. The generic `star_computation_engine` identity remains only for historical and actual whole-machine archives. Existing sealed archives restore their original contents without rewriting saved data, supplying missing parts, refunding materials or upgrading to a larger package for free. A restored old cube cannot form independently: manually complete any missing central 3×3×3 blocks and the real orrery foundation. Existing casing and observation ports remain required central structure parts and retain their original ingredient uses; loose parts do not provide menu or ME access. Old four-direction anchor columns remain unsupported.

Copying the item to another save does not transfer its archive. Cross-save export requires a separate export workflow.

## Recipes

<RecipeFor id="molecularmanipulator:folding_assembler" />
<RecipeFor id="molecularmanipulator:star_chart_calibrator" />
