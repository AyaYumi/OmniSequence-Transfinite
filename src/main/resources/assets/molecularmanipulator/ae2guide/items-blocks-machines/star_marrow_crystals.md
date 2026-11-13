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

Grow Star Marrow Crystals on parent rocks, then process them in the Fabrication Well into boards, processors and alloys for star machinery. Start by crafting Primordial Crystal Cores and converting them into seeds at the Twin Singularity Observation Ring.

<Row>
<ItemImage id="molecularmanipulator:primordial_crystal_core" scale="5" />

<ItemImage id="molecularmanipulator:star_marrow_seed" scale="5" />

<BlockImage id="molecularmanipulator:flawed_star_marrow_parent_rock" scale="4" />

<ItemImage id="molecularmanipulator:star_marrow_crystal" scale="5" />
</Row>

Left to right: Primordial Crystal Core, Star Marrow Seed, Flawed parent rock and Star Marrow Crystal.

## Requirements

- For 2 Primordial Crystal Cores, gather one each of Charged Certus Quartz Crystal, Sky Stone Dust, Amethyst Shard, Snowball and Ender Pearl Dust.
- Find AE2 Budding Certus Quartz. Any of its four quality stages can become a Heavily Damaged Star Marrow Parent Rock with one seed.
- Bring a pickaxe and a way to supply 16 AE/t, or equivalent FE, to the Twin Singularity Observation Ring in the End. Its power console needs no channel.

<RecipeFor id="molecularmanipulator:primordial_crystal_core" />

## Turn cores into seeds

Reach the ring through the coordinate warp pad in the small End ruin south of the main island, near X 0, Z 224. Insert an Ender Pearl in the pad's socket. Empty-hand right-click its three coordinate keys to cycle their symbols until they match the ruin's inscriptions. Stand on the pad and empty-hand right-click its center to travel.

At the ring, find the Power Console, Input Cradle and Output Cradle.

1. Supply the power console with AE or FE and keep the ring intact.
2. Hold Primordial Crystal Cores and right-click the input cradle to load 1 to 64 cores.
3. Empty-hand right-click the input cradle to start the whole loaded stack. The output cradle needs room for all resulting seeds at once.
4. Wait 8 seconds at default power settings. Each core becomes one seed, and a full stack takes the same time as a single core.
5. Empty-hand right-click the output cradle to collect the seeds. Start each new batch yourself; completion does not start another.

Shift + empty-hand right-click takes unstarted cores back from the input. Power loss or insufficient output room pauses an active batch without losing it. The cradles use direct right-click controls and cannot be operated through a GUI, hopper or ME item connection.

## Convert a parent rock

Right-click a supported rock with a Star Marrow Seed. Each use consumes one seed:

| Rock before use | Rock after use |
| --- | --- |
| Any of AE2's four Budding Certus Quartz stages | Heavily Damaged Star Marrow Parent Rock |
| Heavily Damaged Star Marrow Parent Rock | Damaged Star Marrow Parent Rock |
| Damaged Star Marrow Parent Rock | Flawed Star Marrow Parent Rock |

Starting from any AE2 budding stage, three seeds in total reach Flawed. You can grow crystals at any Star Marrow parent-rock tier. Seeds stop at Flawed; obtaining Flawless requires the lantern simulation described below.

## Grow and harvest crystals

1. Leave space around the parent rock's faces. Buds can grow on all six sides.
2. Keep the area loaded and let buds grow through Small Bud, Medium Bud and Large Bud into a mature Cluster.
3. Mine the mature cluster with a pickaxe, leaving the parent rock in place to grow more.

<Row>
<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_small" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_medium" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_large" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_cluster" scale="4" />
</Row>

A mature cluster drops 3 crystals, increased by Fortune. Silk Touch gives the cluster block instead. Moving a cluster does not replace the parent rock needed for further growth.

Damaged and Flawed rocks can lose a quality tier as crystals grow. Heavily Damaged rocks cannot degrade further, and Flawless rocks never degrade.

## Make star materials in the well

Use naturally grown crystals to complete Tier II: Star Marrow Materials in [Fabrication Research](matter_fabrication_research.md). It needs 16 crystals plus the other materials listed in the research panel. Store research materials in ME.

The five products below are made in the Fabrication Well using [ports](matter_fabrication_ports.md) or [processing patterns](matter_fabrication_pattern_assembly.md). Their base processing time is 20 seconds before star-engine bonuses. They need no signal, Flawless parent rock or star-research unlock.

- Make Circuit Boards, then combine them with redstone and Printed Silicon for Star Chart Processors. Loose Silicon cannot replace Printed Silicon. Boards need no catalyst.
- Make Crystal Dust, then Alloy Ingots. Include the fluid input for the alloy recipe.
- Make Star Signal Storage Components for [signal storage cells](star_signal_flow.md).

<RecipeFor id="molecularmanipulator:star_chart_circuit_board" />

<RecipeFor id="molecularmanipulator:star_chart_processor" />

<RecipeFor id="molecularmanipulator:star_marrow_crystal_dust" />

<RecipeFor id="molecularmanipulator:star_marrow_alloy_ingot" />

<RecipeFor id="molecularmanipulator:star_signal_storage_component" />

## Optional: make crystals from signal and water

Natural growth supplies all crystals needed to get started. For automated production, first make a [Star Sea Calculation Mainframe](star_sea_calculation_mainframe.md), [signal storage](star_signal_flow.md) and a [Simulation Lantern](simulation_lantern.md).

### Obtain a Flawless parent rock

1. Repair a parent rock to Flawed with seeds.
2. Hold a lit Simulation Lantern, preferably in your offhand so you can use a pickaxe. Follow the [lantern controls](simulation_lantern.md) to enter its simulation.
3. Mine the Flawed rock yourself while the simulation is active. You receive a permanent Flawless parent rock. Place it for growth or keep it as a catalyst.

This costs no lantern fuel, AE or extra seed. Until harvested, the world block remains Flawed and can decay as buds grow. If it falls below Flawed, you receive its real lower tier. Automatic mining cannot give the simulation reward.

### Set up Signal-Induced Crystallization

1. Complete Tier II: Signal-Induced Crystallization after Star Marrow Materials.
2. Put one Flawless Star Marrow Parent Rock in the Catalyst page of any assembly in this well. It is retained without downgrading, and one is enough for larger batches.
3. Encode the recipe below as a processing pattern, including 1024 bit Star Signal Flow and 8000 mB water. Leave the rock out of the consumed inputs.
4. Request crystals in ME. Each execution produces 64 crystals at a base 20 seconds and 512 AE/t, without consuming existing crystals.

The Pattern Assembly supplies signal; item and fluid ports alone cannot do so. Signal, water and power requirements scale with the number of executions.

<RecipeFor id="molecularmanipulator:star_marrow_crystal" />

## Troubleshooting

| Problem | Fix |
| --- | --- |
| A core does nothing to a rock | Convert it into a seed at the ring first. |
| The cradle refuses an item or waits | It accepts cores only. Check ring power and space for the entire seed batch. Boards are made in the well. |
| A seed stops working | Check that the target is AE2 Budding Certus Quartz or a Heavily Damaged/Damaged Star Marrow rock. Seeds stop at Flawed; use the lantern route for Flawless. |
| No crystals grow | Clear the parent rock's faces and keep the area loaded. Wait for mature clusters before harvesting. |
| Signal recipe waits | Check its research, ME signal/water supply and a Flawless rock in this well's catalyst slots. |

## Signal production

Use your first processor to make a [Star Sea Calculation Mainframe](star_sea_calculation_mainframe.md), then install [Star Signal Storage](star_signal_flow.md). See [Simulation Lantern](simulation_lantern.md) for its controls.
