---
navigation:
  parent: omnisequence-index.md
  title: "Star Marrow Crystals, Crystal Core and Seed"
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

# Star Marrow Crystals, Crystal Core and Seed

Craft an **Primordial Crystal Core** first, then crystallize it into a **Star Marrow Seed** in the **Twin Singularity Observation Ring**. The seed converts parent rocks. Those parent rocks grow buds and clusters in the same way as Certus Quartz. The same ring also turns a **Star Marrow Crystal** straight into a **Star Chart Circuit Board**; that is the cradles' second conversion.

<Row>
<ItemImage id="molecularmanipulator:primordial_crystal_core" scale="6" />

<ItemImage id="molecularmanipulator:star_marrow_seed" scale="6" />

<ItemImage id="molecularmanipulator:star_marrow_crystal" scale="6" />

<BlockImage id="molecularmanipulator:star_marrow_crystal_cluster" scale="4" />
</Row>

## Crystal core and seed recipe

Craft shapeless; the ingredients are unchanged:

- 1 Charged Certus Quartz Crystal
- 1 Sky Stone Dust
- 1 Amethyst Shard
- 1 Snowball
- 1 Ender Pearl Dust

This yields **2 Primordial Crystal Cores**. A core cannot be used on a parent rock directly. Take it to the Twin Singularity Observation Ring and crystallize it into a Star Marrow Seed first.

<RecipeFor id="molecularmanipulator:primordial_crystal_core" fallbackText="No recipe is available in this pack. Check JEI." />

## The Twin Singularity Observation Ring: two cradle conversions

The two cradles on the front platform handle two conversions. They work the same way; only the input and output differ. Both appear in JEI: look up recipes for the seed or circuit board, or uses for the core, crystal, power console or cradles.

| Conversion | Input (input cradle) | Output (output cradle) | Batch size | Time | Power |
| --- | --- | --- | --- | --- | --- |
| Core crystallization | Primordial Crystal Cores | Star Marrow Seeds | Any N, up to 64, N:N | 160 ticks (8 seconds) for the whole batch | The existing ring supply, 16 AE/t base for the whole batch; about 2,560 AE per batch at default settings, regardless of N |
| Circuit board conversion | Star Marrow Crystals | Star Chart Circuit Boards | Any N, up to 64, N:N | 160 ticks (8 seconds) for the whole batch | Same, 16 AE/t base for the whole batch, regardless of N |

Rules shared by both conversions:

- **One input type per batch**: cores and crystals cannot be mixed in the same batch. To switch, take back the unstarted stock from the input cradle first.
- **The output must fit**: the output cradle has to hold all N results at once, together with whatever is already there. Items can only merge if they are the same item with the same components. If it cannot fit, or the cradle holds an incompatible item, the batch waits in place.
- **Always manual**: you start every batch yourself and take the results out yourself. A finished batch does not start another automatically.

The steps below use core crystallization as the example. For circuit boards, read "Primordial Crystal Cores" as "Star Marrow Crystals" and "Star Marrow Seeds" as "Star Chart Circuit Boards"; nothing else changes.

1. Right-click the **input cradle** while holding Primordial Crystal Cores. It takes as many as fit, up to 64 total across unstarted stock and the already locked working batch.
2. Right-click the input cradle with an empty hand to start. **The entire stored stack is locked as one batch**: all N cores share one 160-tick (8 second) cycle and turn into N seeds together, as one simultaneous job.
3. The **output cradle must be able to fit all N seeds at once**, together with any seeds already there, before the batch starts or resumes. If it cannot, the batch waits in place and never completes partially. At completion, all N seeds are inserted at once.
4. Right-click the **output cradle** with an empty hand to take the whole stack of Star Marrow Seeds.
5. To take back cores that have not started yet, **Shift + empty-hand right-click** the input cradle. This only takes new, unlocked stock; the locked batch stays intact and resumes as a whole. Start each batch manually; a finished batch does not start another automatically.

Progress pauses in place, with no partial completion and no extra output, if paid power stops, the linked power interface or ring is missing, the output cradle is missing, or its remaining space cannot fit the whole batch. It resumes when the condition is fixed. Unstarted stock, the whole working batch, the output stack and current progress are saved. Breaking the input cradle drops both unstarted stock and the whole working batch; breaking the output cradle drops its seeds. The cradles have no GUI, and no hopper or ME item automation was added. The pausing, saving and dropping rules above apply to both core crystallization and circuit board conversion.

During the 160-tick animation, one representative stack stands in for the whole batch and shows count N. The cores remain on the input cradle to stabilize until tick 72; from tick 72 to 112 the representative core stack flies into the black hole; at tick 112 all N seeds appear on the white-hole side and travel to the output cradle as one stack; at tick 160 all N Star Marrow Seeds are added at once. For circuit board conversion, the representative image is a Star Marrow Crystal instead and the result is a circuit board; the board likewise appears on the white-hole side only after it has been absorbed. Both cradles display their item and count. Before the batch flies into the black hole, the input cradle count includes the locked batch; after it goes in, the batch is no longer counted there, and only newly inserted, unstarted stock is shown.

An existing Twin Singularity Observation Ring is not rebuilt or overwritten automatically. Old block, item, cradle inventory, processing progress and structure records remain readable under the new IDs. An early cradle block without storage gains it the first time it is right-clicked, and then works normally.

## Converting parent rocks

1. Get an **entroized Fluix parent rock** from ExtendedAE: fully, mostly, half or hardly entroized.
2. Right-click it with a Star Marrow Seed. Each use consumes one seed and moves the rock one step:
   - Fully → Mostly → Half → Hardly entroized
   - Hardly entroized → **Heavily Damaged Star Marrow Parent Rock**
   - Heavily Damaged → **Damaged Star Marrow Parent Rock**
   - Damaged → **Flawed Star Marrow Parent Rock**
3. The seed stops at **Flawed**. It cannot create a Flawless parent rock. To obtain a Flawless parent rock, use a lit [Green Fire Lantern](green_fire_lantern.md) instead.

> Without ExtendedAE, the seed can still repair existing star marrow parent rocks, but it cannot convert entroized Fluix rocks.

## Parent rock tiers

| Tier | Notes |
| --- | --- |
| Heavily Damaged Star Marrow Parent Rock | Lowest tier; it never decays further |
| Damaged Star Marrow Parent Rock | Can decay to Heavily Damaged when a bud grows |
| Flawed Star Marrow Parent Rock | Can decay to Damaged; the seed stops here |
| Flawless Star Marrow Parent Rock | Never decays; obtain it through the Green Fire Lantern simulation (see below) |

Each successful bud growth has about a **1 in 12** chance to lower the parent rock by one tier. Heavily Damaged cannot decay further, and Flawless never decays.

## Growth and harvesting

Buds can grow on all six faces of a parent rock:

**Small Bud → Medium Bud → Large Bud → Cluster**

Mine a mature cluster with a pickaxe:

- Without Silk Touch: **3 Star Marrow Crystals**, affected by Fortune
- With Silk Touch: the cluster block itself, for moving or decoration; continued crystal production requires buds growing on a parent rock

## Materials

| Item | Use |
| --- | --- |
| Primordial Crystal Core | Crafting product; crystallizes into a Star Marrow Seed in the observation ring |
| Star Marrow Crystal | Main cluster drop and base material for the star computation series; also converts into Star Chart Circuit Boards in the observation ring |
| Star Marrow Crystal Dust | Grind crystals in an Inscriber for alloy processing |
| Star Marrow Alloy Ingot | Alloyed in the AdvancedAE Reaction Chamber; structural material for the engine and anchors |
| Star Chart Circuit Board | Made in the Twin Singularity Observation Ring from 1 Star Marrow Crystal, no longer pressed in an Inscriber |
| Star Chart Processor | Assemble a circuit board, redstone and Printed Silicon in an Inscriber for the engine and anchors |

### Crystal dust and alloy ingots

Without a press installed, the Inscriber turns **1 Star Marrow Crystal into 1 Crystal Dust**. In the **AdvancedAE Reaction Chamber**, combine 4 crystal dust, 4 iron ingots, 1 Fluix Dust, 1 Sky Stone Dust and **500 mB water**, using **51,200 AE** in total, to produce **4 Star Marrow Alloy Ingots**. Reaction Chamber processing needs no Fabrication Well research. This alloy recipe only loads when AdvancedAE is installed.

<RecipeFor id="molecularmanipulator:star_marrow_crystal_dust" />

<RecipeFor id="molecularmanipulator:star_marrow_alloy_ingot" />

### Circuit board and processor

Star Chart Circuit Boards now come only from the Twin Singularity Observation Ring: put **1 Star Marrow Crystal** into the input cradle and take **1 circuit board** from the output cradle. Batch size, time and power match core crystallization; see "Circuit board conversion" above. The old Inscriber recipe that pressed a circuit board with the Calculation Processor Press has been removed.

Then place the circuit board in the Inscriber's top slot, redstone in the middle slot and Printed Silicon in the bottom slot to assemble the processor. This step does not consume Star Signal Flow.

<RecipeFor id="molecularmanipulator:star_chart_processor" />

## Flawless parent rock from the lantern

JEI's **Green Fire Simulation · Flawless Rock** page shows Flawed rock as the input, Flawless rock as the output, and the lit lantern as a reusable catalyst. Find it through Flawless rock recipes or the uses of Flawed rock and the lantern.

A **Flawed** parent rock can become a **Flawless** one without changing the world block: enter the Green Fire Lantern simulation with a lit lantern, then mine the flawed rock or pick-block it in creative. The server checks who performed the action and returns the Flawless parent rock item to that player. Place it and it stays Flawless permanently; it never decays.

The world still holds the original Flawed block while you are only looking at it. Bud growth and the usual chance to lose a tier still happen in the real world and are shared by everyone. If the rock degrades before you mine it, you get the real lower tier. Ordinary players, fake players and automatic tools without a player actor get the real Flawed result, including when using pick-block. Standard tools that pass a player actor are covered; external tools that edit blocks or inventories directly need their own integration.

The lantern simulation costs no fuel, AE or extra Star Marrow Seeds, and it has no 12-block range requirement. See [Green Fire Lantern](green_fire_lantern.md) for controls and the light radius.
