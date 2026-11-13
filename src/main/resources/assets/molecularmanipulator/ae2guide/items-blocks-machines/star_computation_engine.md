---
navigation:
  parent: omnisequence-index.md
  title: "Star Computation Engine"
  icon: molecularmanipulator:star_computation_engine_core
  position: 1200
item_ids:
- molecularmanipulator:star_computation_engine_core
- molecularmanipulator:star_computation_engine_casing
- molecularmanipulator:star_computation_observation_port
- molecularmanipulator:star_orrery_engraved_plinth
- molecularmanipulator:star_orrery_gilded_plinth
- molecularmanipulator:reference_star_position_anchor
- molecularmanipulator:quantum_star_position_anchor
- molecularmanipulator:terminal_star_position_anchor
---

# Star Computation Engine

The Nine-Ring Star Orrery boosts supported machines on its ME network. Wells process faster, sequence machines craft larger batches and crafting CPUs gain capacity for larger orders. Start with one ring, then expand as needed.

<Row>
<BlockImage id="molecularmanipulator:star_computation_engine_core" scale="4" />

<BlockImage id="molecularmanipulator:reference_star_position_anchor" scale="4" />

<BlockImage id="molecularmanipulator:quantum_star_position_anchor" scale="4" />

<BlockImage id="molecularmanipulator:terminal_star_position_anchor" scale="4" />
</Row>

Left to right: central core, Reference anchors for rings 1 to 3, Quantum anchors for rings 4 to 6 and Terminal anchors for rings 7 to 9.

## Prepare

- Complete **Tier II: Star Marrow Materials** in the well and make the [processed materials](star_marrow_crystals.md). Craft the core, casing, ports and Reference anchors at a crafting table. Upgrade Quantum and Terminal anchors in the well.
- Check JEI's **Star Orrery Structure** preview for the full material list and required space. Left-drag rotates, right-drag pans, scrolling zooms, and layer controls reveal the foundation.
- A powered ME network with **one free channel**. A channel is a device connection allowance. The whole engine uses one channel and **16 AE/t** idle power.

Even L0, with no rings, requires all **27 central blocks** and **928 real foundation blocks** below them.

## Recommended start: order a complete L1 package

1. Connect a [Spatial Folding Assembler](folded_buildings.md) to ME and order Star Orrery L1 from the terminal. The package consumes the whole building's materials, including the center, foundation and first ring of 24 anchors.
2. Keep the package in your inventory. Right-click the site with a **Star Chart Calibrator**, choose the package and facing, inspect the preview and confirm deployment. The selected base is the lowest foundation layer; the **core is 37 blocks above it**.
3. Connect ME cable to the formed orrery's central casing, observation port, core or valid ring anchor. Foundation blocks cannot connect, and loose parts need a formed machine. For wireless access, use Remote connection in the right sidebar; follow [Sequence Wireless Hub setup](sequence_array_controller.md).
4. Right-click a valid part to open **Overview**. Check formation, ME online status and effective **L1**. Wells on that network now get **2× processing speed** by default, without producing Star Signal Flow first.

You can instead order a complete L2 or L3 package. These include the first two or three rings, still using only Reference anchors. Each package contains:

| Package | Core | Casing | Ports | Engraved plinth | Gilded plinth | Reference anchors |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| L1 | 1 | 22 | 4 | 520 | 408 | 24 |
| L2 | 1 | 22 | 4 | 520 | 408 | 56 |
| L3 | 1 | 22 | 4 | 520 | 408 | 96 |

## Building by hand: center and foundation

### Center: three layers of nine blocks

The three layers are shown side by side so you can see inside: bottom on the left, center in the middle and top on the right. Stack them directly above each other to build the 3×3×3 center.

<GameScene zoom="3" background="transparent" interactive={true}>
  <ImportStructure src="../assets/structures/orrery-core-layers.snbt" />
  <IsometricCamera yaw="180" pitch="65" />
</GameScene>

- Fill the bottom and top layers with nine casing each.
- Center layer: core in the middle, an observation port at each edge center, casing in the four corners.
- Total: **1 core + 22 casing + 4 observation ports**, with no empty spaces.

### Foundation: a four-layer open ring frame

This is the actual **37-block-wide, four-layer** foundation. Drag to rotate it. Leave the central shaft and gaps between rings open; ordinary gold blocks cannot replace Gilded Plinth.

<GameScene zoom="0.7" background="transparent" interactive={true}>
  <ImportStructure src="../assets/structures/orrery-foundation.snbt" />
  <IsometricCamera yaw="225" pitch="45" />
</GameScene>

Use **520 Star Orrery Engraved Plinth + 408 Star Orrery Gilded Plinth**. The core belongs 37 blocks above the lowest layer. Follow JEI's layer views for exact positions. The complete center and foundation form automatically as **L0**; rings then add levels and benefits.

Formation changes the appearance to a floating ring frame, but the original blocks retain collision. The glowing rings move; **the real anchor positions do not**. Do not move anchors to follow the animation.

## Expanding: add one ring on Upgrade

1. Open **Upgrade**, select the next level and inspect missing anchors and available quantities. Previewing spends nothing.
2. Prepare the anchors below. Correct anchors already in place count, so only missing blocks are charged. Materials come from **your inventory first, then this engine's ME network**.
3. Click **Auto-build L… rings** or **Upgrade to L…**. Keep anchor positions clear and chunks loaded; resolve any reported shortage or obstruction.
4. Return to Overview and check that the effective level rose. From L4 onward, click each upgrade in order; it will not start the next one automatically.

| New ring | Anchor tier | Anchors for this ring |
| --- | --- | ---: |
| 1 / 2 / 3 | Reference | 24 / 32 / 40 |
| 4 / 5 / 6 | Quantum | 48 / 56 / 64 |
| 7 / 8 / 9 | Terminal | 72 / 80 / 88 |

Totals are **96 Reference**, **168 Quantum** and **240 Terminal** anchors. Hand-building L1 to L3 activates those levels automatically; L4 to L9 require sequential confirmation on Upgrade. Default upgrades have no additional energy, research or quest payment. Normal idle power and overclock costs remain separate.

Check the effective level in Overview. A damaged inner ring disables itself and outer rings, so complete each ring in order. Pack-added upgrade conditions appear on Upgrade. Energy calibration shows paid progress and pause/resume controls.

## L3: unlock two single-block machines for free

There is one default team research: **Single-Block Integration**.

1. Use your team's orrery. Keep it formed and ME-online, and confirm its **effective level is at least L3**.
2. Open **Recipe Tree** and select **Single-Block Integration**.
3. Click Unlock to activate the research for **0 bit**. It has no prerequisite and needs no signal balance.

| Machine | Fabrication Well research still needed |
| --- | --- |
| [Molecular Sequence Rewrite Array](molecular_sequence_rewrite_array.md) | Tier III: Sequence Engineering |
| [Transfinite Compute Nexus](transfinite_compute_nexus.md) | Tier VI: Transfinite Integration; its recipe also needs AdvancedAE installed |

Only these two default recipes have this additional star permission. The well, Sequence Array and multiblock Omni Computation controller do not need this purchase.

The unlock is **permanent team progress** and survives dismantling or restarting. Engines can lend crafting permission across teams on the same live ME network. **Disconnecting ends borrowed permission; it never becomes your permanent progress** or supplies a permanent prerequisite for custom research. Pack-added research appears in the same Recipe Tree; scroll longer lists to inspect requirements.

## What do the benefits mean?

- Assembler parallelism is the number of recipe copies a supported sequence machine can make together.
- CPU parallelism is the number of crafting steps an Omni CPU can advance together.
- CPU crafting storage is workspace for orders and intermediate ingredients. ME item storage is separate.
- Well speed reduces production time. Research time stays the same, and nine-round deep research increases batch size separately.

These values apply without overclocking. Check Overview and Upgrade for live capabilities.

| Level | Assembler parallelism | CPU parallelism | CPU crafting storage | Well speed |
| --- | ---: | ---: | ---: | ---: |
| L1 | 4,096 | 512 | 1 GiB | ×2 |
| L2 | 16,384 | 1,024 | 4 GiB | ×3 |
| L3 | 65,536 | 2,048 | 16 GiB | ×4 |
| L4 | 262,144 | 4,096 | 64 GiB | ×8 |
| L5 | 1,048,576 | 8,192 | 256 GiB | ×12 |
| L6 | 4,194,304 | 16,384 | 1 TiB | ×16 |
| L7 | 16,777,216 | 32,768 | 4 TiB | ×32 |
| L8 | 67,108,864 | 65,536 | 16 TiB | ×48 |
| L9 | 268,435,456 | 131,072 | 64 TiB | ×64 |

A network uses **one best active engine**; engines do not stack. Power, channels, ingredients and output space still limit real production. From L7, computation machines can use virtual CPUs for multiple orders; see the [Omni Computation Core](omni_computation_core.md).

### Optional: overclocking

Produce Star Signal Flow with a [Star Sea Calculation Mainframe](star_sea_calculation_mainframe.md), store it in a [Star Signal Storage Cell](star_signal_flow.md), then enable overclock on Overview. Star Signal Flow is not AE power; ordinary benefits do not consume it.

- L1 to L9 consume **2 / 4 / 8 / 16 / 32 / 64 / 128 / 256 / 512 bit per tick**. At normal speed, a second is about 20 ticks.
- L1 to L8 overclocking doubles the four benefits above. Level and research requirements still apply.
- Only continuously paid **L9 overclock** provides unlimited parallelism, unlimited CPU crafting storage and **1-tick** well production. Normal L9 remains finite as listed.
- Insufficient signal switches overclock off, returning to normal benefits. Disconnection or broken structure removes benefits. Permanent research is retained.

## Troubleshooting

| What you see | What to check |
| --- | --- |
| The small cube will not form | Complete the four-layer foundation and all 27 central blocks. |
| It looks complete but gives no boost | Effective level, power and channel in Overview; benefiting machines must share the ME network. |
| Many anchors, low level | Check each ring from the first outward; confirm upgrades from L4 onward. |
| Upgrade does not complete | Anchor quantities, obstructing blocks, loaded chunks and permission to place. |
| L3 but the recipe is locked | Explicitly unlock Single-Block Integration and finish the machine's well research. |
| Overclock turns itself off | Check signal stock, production rate and ME status. |

Before moving the engine, settle external ME crafting orders. Click Reclaim package on Overview, wait briefly and confirm within three seconds. Redeploy with the Calibrator in the same world. Package grade follows the consecutive complete physical rings at reclaim; see [Relocation](folded_buildings.md). Normal recovery and packaging retain the wireless binding. Rebind if the wireless hub was moved or replaced.

Old-world cubes also need the complete center and real foundation. Supply any missing materials.

## Recipes

<RecipeFor id="molecularmanipulator:star_computation_engine_core" />

<RecipeFor id="molecularmanipulator:star_computation_engine_casing" />

<RecipeFor id="molecularmanipulator:star_computation_observation_port" />

<RecipeFor id="molecularmanipulator:star_orrery_engraved_plinth" />

<RecipeFor id="molecularmanipulator:star_orrery_gilded_plinth" />

<RecipeFor id="molecularmanipulator:reference_star_position_anchor" />

<RecipeFor id="molecularmanipulator:quantum_star_position_anchor" />

<RecipeFor id="molecularmanipulator:terminal_star_position_anchor" />
