---
navigation:
  parent: omnisequence-index.md
  title: "Star Marrow Crystals"
  icon: molecularmanipulator:star_marrow_crystal
  position: 1210
item_ids:
- molecularmanipulator:primordial_crystal_core
- molecularmanipulator:star_marrow_seed
- molecularmanipulator:star_marrow_crystal
- molecularmanipulator:star_marrow_crystal_block
- molecularmanipulator:star_marrow_crystal_dust
- molecularmanipulator:star_marrow_alloy_ingot
- molecularmanipulator:star_chart_circuit_board
- molecularmanipulator:star_chart_processor
- molecularmanipulator:star_marrow_crystal_bud_small
- molecularmanipulator:star_marrow_crystal_bud_medium
- molecularmanipulator:star_marrow_crystal_bud_large
- molecularmanipulator:star_marrow_crystal_cluster
- molecularmanipulator:heavily_damaged_star_marrow_parent_rock
- molecularmanipulator:damaged_star_marrow_parent_rock
- molecularmanipulator:flawed_star_marrow_parent_rock
- molecularmanipulator:flawless_star_marrow_parent_rock
---

# Star Marrow Crystals

Grow Star Marrow Crystals on parent rocks. Process them in the Fabrication Well to make Star Chart Processors, Star Marrow Alloy Ingots and other machine parts.

<Row>
<ItemImage id="molecularmanipulator:primordial_crystal_core" scale="5" />

<ItemImage id="molecularmanipulator:star_marrow_seed" scale="5" />

<BlockImage id="molecularmanipulator:flawed_star_marrow_parent_rock" scale="4" />

<ItemImage id="molecularmanipulator:star_marrow_crystal" scale="5" />
</Row>

## Before you start

- Gather one Charged Certus Quartz Crystal, one Sky Stone Dust, one Amethyst Shard, one Snowball and one Ender Pearl Dust to craft **2 Primordial Crystal Cores**.
- Find any of the four grades of AE2 Budding Certus Quartz.
- Bring a pickaxe and a way to supply 16 AE/t, or equivalent FE, to the Twin Singularity Observation Ring in the End. Its power console needs no channel.

<RecipeFor id="molecularmanipulator:primordial_crystal_core" />

## Turn cores into seeds

Find the small ruin south of the main End island, near **X 0, Z 224**. Read its three inscriptions in order. The symbols depend on the world seed and stay the same on later visits. Insert an Ender Pearl in the warp pad's socket, then right-click each key with an empty hand until it matches its inscription.

Stand on the pad and right-click its center with an empty hand. On your first trip, wait for the destination to load. Step off the pad to cancel.

The new laboratory pad is at Y 189, 24 blocks above the old pad. Existing laboratories and their return pads stay in place.

1. Supply the ring's **Power Console** with AE or FE. The ring must be intact.
2. Hold Primordial Crystal Cores and right-click the **Input Cradle** to load **1–64 cores**.
3. Empty-hand right-click the input cradle or central power console to start the whole stack. The **Output Cradle** needs room for every seed in the batch.
4. After **160 powered ticks**, or **8 seconds** at normal speed, right-click the output cradle with an empty hand to collect the seeds. You get one seed per core. A full stack takes the same time as one core, and you start each batch by hand.

To retrieve unstarted cores, hold Shift and right-click the input cradle with an empty hand. The batch pauses if power runs out or the output cradle cannot fit the result. Use the cradles by hand. They have no GUI or connection for hoppers or ME items.

## Convert a parent rock

Right-click the rock with a **Star Marrow Seed**. Each use spends one seed and moves the rock one step.

| Rock before use | Rock after use |
| --- | --- |
| Any of AE2's four Budding Certus Quartz stages | Heavily Damaged Star Marrow Parent Rock |
| Heavily Damaged Star Marrow Parent Rock | Damaged Star Marrow Parent Rock |
| Damaged Star Marrow Parent Rock | Flawed Star Marrow Parent Rock |

The first seed gives you a parent rock that can grow crystals. Three seeds in total take it from Budding Certus Quartz to Flawed, the highest grade seeds can reach.

## Grow and harvest crystals

Keep the area loaded and leave at least one face open. Buds can grow on any of the six faces. Small buds grow into medium buds, large buds and then clusters.

<Row>
<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_small" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_medium" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_large" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_cluster" scale="4" />
</Row>

Mine mature clusters with a pickaxe and leave the parent rock in place. A cluster drops **3 crystals** and Fortune can increase the yield. Silk Touch drops the cluster itself.

Whenever a bud appears or advances a stage, a Flawed, Damaged or Heavily Damaged parent rock has a **1 in 12** chance to lose one grade. Flawed becomes Damaged, Damaged becomes Heavily Damaged, and Heavily Damaged becomes a **Star Marrow Crystal Block** that no longer grows crystals. Flawless parent rocks never decay.

You can also craft one Star Marrow Crystal Block by filling a crafting table's 3×3 grid with **9 Star Marrow Crystals**.

<RecipeFor id="molecularmanipulator:star_marrow_crystal_block" />

## Make star materials in the well

Store **16 Star Marrow Crystals** and the other materials shown on the well's research page in ME. Then complete **Tier II: Star Marrow Materials** in [Fabrication Research](matter_fabrication_research.md).

The five well recipes below take **20 seconds** each before bonuses from the Star Computation Engine. They need no signal, Flawless parent rock or star research. Feed them through [ports](matter_fabrication_ports.md) or with [processing patterns](matter_fabrication_pattern_assembly.md).

- Make Star Chart Circuit Boards without a catalyst. Combine them with redstone and Printed Silicon to make Star Chart Processors.
- Make Star Marrow Crystal Dust. Use it with water to make Star Marrow Alloy Ingots.
- Use Star Signal Storage Components to craft [signal storage cells](star_signal_flow.md).

<RecipeFor id="molecularmanipulator:star_chart_circuit_board" />

<RecipeFor id="molecularmanipulator:star_chart_processor" />

<RecipeFor id="molecularmanipulator:star_marrow_crystal_dust" />

<RecipeFor id="molecularmanipulator:star_marrow_alloy_ingot" />

<RecipeFor id="molecularmanipulator:star_signal_storage_component" />

## Optional crystal production from signal and water

Use your first Star Chart Processor to make a [Star Sea Calculation Mainframe](star_sea_calculation_mainframe.md), then install [Star Signal Storage](star_signal_flow.md). Use a [Simulation Lantern](simulation_lantern.md) to enter the simulation and mine a Flawed Star Marrow Parent Rock yourself. The Flawless rock you get is the catalyst.

1. Complete **Tier II: Signal-Induced Crystallization** after Star Marrow Materials.
2. Put **one Flawless Star Marrow Parent Rock** in the **Catalyst** page of any Pattern Assembly in this well. You can reuse it in larger batches too.
3. Encode a processing pattern with **1,024 bit of Star Signal Flow** and **8,000 mB of water**. Keep the rock in the catalyst slot and put the pattern in a Pattern Assembly in this well.
4. Request crystals in ME. One run makes **64 crystals** in **20 seconds at 512 AE/t** before bonuses. Signal, water and power scale with the number of recipe copies.

<RecipeFor id="molecularmanipulator:star_marrow_crystal" />

## Troubleshooting

| Problem | Fix |
| --- | --- |
| The cradle rejects the input | Use a Primordial Crystal Core. Make Star Chart Circuit Boards in the well. |
| The signal recipe waits | Check the well's research, ME supplies of signal and water, and the Flawless parent rock in the Catalyst page. |
