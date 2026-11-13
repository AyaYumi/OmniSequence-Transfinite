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

The Nine-Ring Star Orrery boosts supported machines on its ME network. Wells process faster, sequence machines craft larger batches and crafting CPUs gain capacity for larger orders. Build the center, foundation and first ring, then add rings as you need more capacity.

<Row>
<BlockImage id="molecularmanipulator:star_computation_engine_core" scale="4" />

<BlockImage id="molecularmanipulator:reference_star_position_anchor" scale="4" />

<BlockImage id="molecularmanipulator:quantum_star_position_anchor" scale="4" />

<BlockImage id="molecularmanipulator:terminal_star_position_anchor" scale="4" />
</Row>

Left to right: central core, Reference anchors for rings 1 to 3, Quantum anchors for rings 4 to 6 and Terminal anchors for rings 7 to 9.

## Requirements

- Complete Tier II: Star Marrow Materials in the well and make the [processed materials](star_marrow_crystals.md). Craft the core, casing, ports and Reference anchors at a crafting table. Upgrade Quantum and Terminal anchors in the well.
- Check JEI's Star Orrery Structure preview for the full materials and required space. Left-drag rotates, right-drag pans, scrolling zooms, and layer controls reveal the foundation.
- Prepare a powered ME network with one free channel. The whole engine uses one channel and 16 AE/t at idle.

Even L0, with no rings, needs all 27 central blocks and 928 foundation blocks below them.

## Unfold a complete L1 package

1. Connect a [Spatial Folding Assembler](folded_buildings.md) to ME and order Star Orrery L1 from the terminal. The order consumes the center, foundation and first ring's 24 anchors.
2. Keep the package in your inventory. Right-click the site with a Star Chart Calibrator, choose the package and facing, inspect the preview and confirm deployment. Base Y is the lowest foundation layer; the core belongs 37 blocks above it.
3. Connect ME cable to the formed orrery's central casing, observation port, core or valid ring anchor. Foundation blocks cannot connect, and loose parts need a formed machine. For wireless access, use Remote connection in the right sidebar; follow [Sequence Wireless Hub setup](sequence_array_controller.md).
4. Open Overview by right-clicking a valid part. Check formation, network status and an effective level of L1. Wells on that network now get 2× processing speed by default. No Star Signal Flow is needed for normal bonuses.

You can also order complete L2 or L3 packages, including their first two or three rings. All three packages use Reference anchors:

| Package | Core | Casing | Ports | Engraved plinth | Gilded plinth | Reference anchors |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| L1 | 1 | 22 | 4 | 520 | 408 | 24 |
| L2 | 1 | 22 | 4 | 520 | 408 | 56 |
| L3 | 1 | 22 | 4 | 520 | 408 | 96 |

## Build the center and foundation by hand

### Center

Stack three 3×3 layers to form the 3×3×3 center. The diagram separates them for visibility: bottom on the left, middle in the center and top on the right.

<GameScene zoom="3" background="transparent" interactive={true}>
  <ImportStructure src="../assets/structures/orrery-core-layers.snbt" />
  <IsometricCamera yaw="180" pitch="65" />
</GameScene>

Fill the bottom and top layers with nine casing blocks each. In the middle layer, place the core at the center, observation ports at the four edge centers and casing in the corners. This uses 1 core, 22 casing and 4 observation ports, with no gaps.

### Foundation

Build the 37-block-wide, four-layer open ring frame shown below. Leave the central shaft and gaps between rings open. Ordinary gold blocks cannot replace Gilded Plinth.

<GameScene zoom="0.7" background="transparent" interactive={true}>
  <ImportStructure src="../assets/structures/orrery-foundation.snbt" />
  <IsometricCamera yaw="225" pitch="45" />
</GameScene>

The foundation uses 520 Star Orrery Engraved Plinth and 408 Star Orrery Gilded Plinth. Follow JEI's layer views for exact positions and keep the core 37 blocks above the lowest layer. The complete center and foundation form automatically as L0.

After formation, the building looks like a floating ring frame, but its original blocks retain collision. The glowing rings move while the real anchor positions stay fixed. Leave anchors at their construction positions.

## Add rings

1. Open Upgrade, select the next level and inspect missing anchors and available quantities. Previewing spends nothing.
2. Gather the anchors listed below. Correct anchors already in place count toward the ring. Construction takes only missing materials, from your inventory first, then the engine's ME network.
3. Click Auto-build L… rings or Upgrade to L…. Keep anchor positions clear and chunks loaded. Resolve any reported shortage or obstruction.
4. Return to Overview and check the effective level. From L4 onward, confirm each upgrade in order; the next one does not start automatically.

| New ring | Anchor tier | Anchors for this ring |
| --- | --- | ---: |
| 1 / 2 / 3 | Reference | 24 / 32 / 40 |
| 4 / 5 / 6 | Quantum | 48 / 56 / 64 |
| 7 / 8 / 9 | Terminal | 72 / 80 / 88 |

All nine rings use 96 Reference, 168 Quantum and 240 Terminal anchors. Hand-building L1 to L3 activates those levels automatically. L4 to L9 require sequential confirmation on Upgrade.

Default upgrades need no extra energy, research or quest payment. Idle power and overclock costs still apply. Pack-added conditions appear on Upgrade; energy calibration shows paid progress and pause/resume controls.

A damaged inner ring disables itself and all outer rings. Complete each ring in order and use the effective level in Overview to check available bonuses.

## Unlock Single-Block Integration at L3

Single-Block Integration is the only default star research. It grants team permission for two fabrication recipes:

1. Keep your team's orrery formed and ME-online, with an effective level of at least L3.
2. Open Recipe Tree and select Single-Block Integration.
3. Click Unlock. It costs 0 bit and needs no prerequisite research or signal balance.

| Machine | Fabrication Well research still needed |
| --- | --- |
| [Molecular Sequence Rewrite Array](molecular_sequence_rewrite_array.md) | Tier III: Sequence Engineering |
| [Transfinite Compute Nexus](transfinite_compute_nexus.md) | Tier VI: Transfinite Integration; its recipe also needs AdvancedAE installed |

Only these two default recipes need the additional star permission. The well, Sequence Array and multiblock Omni Computation controller need no such unlock.

The unlock is permanent team progress and survives dismantling or restarting. An engine can also lend crafting permission to other teams on the same live ME network. Borrowed permission ends on disconnection and cannot become permanent progress or satisfy a permanent prerequisite for custom research. Pack-added research appears in the same Recipe Tree; scroll to inspect longer lists.

## Network bonuses

Assembler parallelism is the number of recipe copies a supported sequence machine can make together. CPU parallelism controls how many crafting steps an Omni CPU can advance together. CPU crafting storage provides workspace for orders and intermediate ingredients, separate from ME item storage.

Well speed shortens production time. Research timers stay the same; nine-round deep research raises the well's batch size separately.

The following values apply without overclocking. Overview and Upgrade show current capabilities.

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

Each network uses its strongest active engine. Bonuses do not stack, and production still needs power, channels, ingredients and output space. From L7, computation machines can use virtual CPUs for multiple orders; see the [Omni Computation Core](omni_computation_core.md).

### Overclocking

Produce Star Signal Flow with a [Star Sea Calculation Mainframe](star_sea_calculation_mainframe.md), store it in a [Star Signal Storage Cell](star_signal_flow.md), then enable overclock on Overview. Signal is separate from AE power and is only needed for overclocked bonuses.

| Level | Overclock cost per tick |
| --- | ---: |
| L1 | 2 bit |
| L2 | 4 bit |
| L3 | 8 bit |
| L4 | 16 bit |
| L5 | 32 bit |
| L6 | 64 bit |
| L7 | 128 bit |
| L8 | 256 bit |
| L9 | 512 bit |

At normal speed, there are about 20 ticks per second. L1 to L8 overclocking doubles all four bonuses in the main table. Level and research requirements still apply.

Continuously paid L9 overclock provides unlimited parallelism, unlimited CPU crafting storage and 1-tick well production. Without overclocking, L9 has the finite limits shown above. Insufficient signal switches overclock off and restores normal bonuses. Disconnection or broken structure removes bonuses; permanent research remains unlocked.

## Troubleshooting

| What you see | What to check |
| --- | --- |
| The small cube will not form | Complete the four-layer foundation and all 27 central blocks. |
| It looks complete but gives no boost | Check effective level, power and channel in Overview. Machines receiving bonuses must share the ME network. |
| Many anchors, low level | Check each ring from the first outward; confirm upgrades from L4 onward. |
| Upgrade does not complete | Check anchor quantities, obstructing blocks, loaded chunks and permission to place. |
| L3 but the recipe is locked | Unlock Single-Block Integration and finish the machine's well research. |
| Overclock turns itself off | Check signal stock, production rate and ME status. |

Before moving the engine, settle external ME crafting orders. Click Reclaim package on Overview, wait briefly and confirm within three seconds. Redeploy with the Calibrator in the same world. Package grade follows the consecutive complete physical rings at reclaim; see [Relocation](folded_buildings.md). Normal recovery and packaging retain the wireless binding. Rebind if the wireless hub was moved or replaced.

Cubes in older worlds also need the complete center and real foundation. Supply any missing materials.

## Recipes

<RecipeFor id="molecularmanipulator:star_computation_engine_core" />

<RecipeFor id="molecularmanipulator:star_computation_engine_casing" />

<RecipeFor id="molecularmanipulator:star_computation_observation_port" />

<RecipeFor id="molecularmanipulator:star_orrery_engraved_plinth" />

<RecipeFor id="molecularmanipulator:star_orrery_gilded_plinth" />

<RecipeFor id="molecularmanipulator:reference_star_position_anchor" />

<RecipeFor id="molecularmanipulator:quantum_star_position_anchor" />

<RecipeFor id="molecularmanipulator:terminal_star_position_anchor" />
