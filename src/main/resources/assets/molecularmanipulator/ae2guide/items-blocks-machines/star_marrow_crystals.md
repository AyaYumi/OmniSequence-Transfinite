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

Grow Star Marrow Crystals on parent rocks, then process them in the Fabrication Well into the boards, processors and alloys used by star machinery.

<Row>
<ItemImage id="molecularmanipulator:primordial_crystal_core" scale="5" />

<ItemImage id="molecularmanipulator:star_marrow_seed" scale="5" />

<BlockImage id="molecularmanipulator:flawed_star_marrow_parent_rock" scale="4" />

<ItemImage id="molecularmanipulator:star_marrow_crystal" scale="5" />
</Row>

The starting route, left to right: **craft a core → turn it into a seed → convert a parent rock → harvest crystals**.

## Before you start

- For **2 Primordial Crystal Cores**, gather one each of Charged Certus Quartz Crystal, Sky Stone Dust, Amethyst Shard, Snowball and Ender Pearl Dust.
- Find an **entroized Fluix parent rock from ExtendedAE**. Any of its four damage stages can be converted; the least entroized needs the fewest seeds.
- Bring a pickaxe and a way to supply **16 AE/t**, or equivalent FE, to the **Twin Singularity Observation Ring** in the End. Its power console needs no channel.

<RecipeFor id="molecularmanipulator:primordial_crystal_core" />

## 1. Turn cores into seeds

Reach the ring through the coordinate warp pad in the small End ruin south of the main island, near **X 0, Z 224**. Insert an Ender Pearl in the pad's socket. Empty-hand right-click its three coordinate keys to cycle their symbols until they match the ruin's inscriptions, then stand on the pad and empty-hand right-click its center to travel.

At the ring, find the **Power Console**, **Input Cradle** and **Output Cradle**.

1. Supply the power console with AE or FE and keep the ring intact.
2. Hold Primordial Crystal Cores and right-click the input cradle to load **1–64 cores**.
3. Empty-hand right-click the input cradle to start the **whole loaded stack**. The output cradle must have room for every resulting seed at once.
4. Wait **8 seconds** at default power settings. One core becomes one seed; a full stack takes the same time as one core.
5. Empty-hand right-click the output cradle to collect the seeds. **Start each batch yourself**; completion does not automatically start another.

Shift + empty-hand right-click on the input takes back only cores that have not started. Power loss or insufficient output room pauses an active batch without losing it. The cradles use direct right-click controls, not a GUI, hopper or ME item connection.

## 2. Convert a parent rock and harvest crystals

Right-click the parent rock with a **Star Marrow Seed**. Each use spends one seed and moves it one step along this route:

**Fully → Mostly → Half → Hardly entroized Fluix rock → Heavily Damaged Star Marrow rock → Damaged → Flawed**

Stop once you have a star marrow parent rock, or keep repairing it to **Flawed**. Seeds cannot make it Flawless.

1. Leave space around its faces. Buds can grow on all six sides.
2. Let the buds grow through the four stages below. Keep the area loaded.
3. Mine only a **mature cluster** with a pickaxe, leaving the parent rock to grow more.

<Row>
<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_small" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_medium" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_bud_large" scale="4" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_cluster" scale="4" />
</Row>

**Small Bud → Medium Bud → Large Bud → Cluster.** A mature cluster drops **3 crystals**, increased by Fortune. Silk Touch gives the cluster block instead; moving a cluster does not replace the need for a parent rock.

Damaged and Flawed rocks can lose a quality tier as crystals grow. Heavily Damaged rocks cannot degrade further; Flawless rocks never degrade.

## 3. Make star materials in the well

Use naturally grown crystals to complete **Tier II: Star Marrow Materials** in [Fabrication Research](matter_fabrication_research.md). It needs **16 crystals**, plus the other materials shown in the research panel. Store those research materials in ME.

Afterward, these five products are made **in the Fabrication Well**. You can use [ports](matter_fabrication_ports.md) or [processing patterns](matter_fabrication_pattern_assembly.md). Their base processing time is **20 seconds** before star-engine speed bonuses. They need no signal, Flawless parent rock or star-research unlock.

- Start with **Circuit Boards**, then make **Star Chart Processors** from boards, redstone and **Printed Silicon**, not loose Silicon. Boards need no catalyst.
- Make **Crystal Dust**, then **Alloy Ingots** with the fluid input included.
- Make **Star Signal Storage Components** to build [signal storage cells](star_signal_flow.md).

<RecipeFor id="molecularmanipulator:star_chart_circuit_board" />

<RecipeFor id="molecularmanipulator:star_chart_processor" />

<RecipeFor id="molecularmanipulator:star_marrow_crystal_dust" />

<RecipeFor id="molecularmanipulator:star_marrow_alloy_ingot" />

<RecipeFor id="molecularmanipulator:star_signal_storage_component" />

## Optional: grow crystals from signal and water

Natural growth remains a complete starting route. For automated crystal production, first make the materials for a [Star Sea Calculation Mainframe](star_sea_calculation_mainframe.md), [signal storage](star_signal_flow.md) and a [Simulation Lantern](simulation_lantern.md).

### Obtain one Flawless parent rock

1. Repair a parent rock to **Flawed** with seeds.
2. Hold a **lit Simulation Lantern**, preferably in your offhand so you can hold a pickaxe. Follow the [lantern controls](simulation_lantern.md) to enter its simulation.
3. **Mine the Flawed rock yourself while in the simulation.** You receive a permanent Flawless parent rock. Place it or keep it as a catalyst.

This costs no lantern fuel, AE or extra seed. The world rock is still Flawed until harvested and can degrade while growing; if it has already degraded, you receive its real lower tier. Automatic mining does not provide this simulation reward.

### Set up Signal-Induced Crystallization

1. Complete **Tier II: Signal-Induced Crystallization** after Star Marrow Materials.
2. Put **one Flawless Star Marrow Parent Rock** in the **Catalyst** page of any assembly in this well. It is kept, never downgraded and need not be multiplied for batches.
3. Encode the recipe below as a processing pattern, including **1024 bit Star Signal Flow and 8000 mB water**, but not the rock.
4. Request crystals in ME. Each execution produces **64 crystals**, at a base **20 seconds and 512 AE/t**. It consumes no existing crystals.

Signal must arrive through the Pattern Assembly; item/fluid ports alone cannot supply it. Signal, water and power increase with the number of executions.

<RecipeFor id="molecularmanipulator:star_marrow_crystal" />

## What success looks like

The ring delivers seeds to its output cradle. A converted parent rock grows the four visible bud stages and mature clusters drop crystals. In the well, processing produces the materials shown above; the optional signal recipe returns crystals to ME while leaving its rock in the catalyst slot.

## If it does not work

| Problem | Fix |
| --- | --- |
| A core does nothing to a rock | Convert it into a **seed** at the ring first. |
| The cradle refuses an item or waits | It accepts cores only. Check ring power and space for the entire seed batch. Boards are made in the well. |
| A seed stops working | It stops at **Flawed**. Use the lantern route for Flawless. Without ExtendedAE, seeds can repair existing star marrow rocks but cannot create one from Fluix rock. |
| No crystals grow | Clear the parent rock's faces and keep the area loaded; do not harvest immature buds. |
| Signal recipe waits | Check its research, ME signal/water supply and a Flawless rock in this well's catalyst slots. |

## Next steps

Use your first processor to make a [Star Sea Calculation Mainframe](star_sea_calculation_mainframe.md), then install [Star Signal Storage](star_signal_flow.md). See [Simulation Lantern](simulation_lantern.md) for its simulation controls.
