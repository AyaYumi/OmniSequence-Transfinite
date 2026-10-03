---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: "Fabrication Research"
  icon: ae2:engineering_processor
  position: 0
---

# Fabrication Research

Research unlocks recipes and machines. Branch completion counts and active jobs belong to this well's controller.

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_controller" scale="4" />

<BlockImage id="molecularmanipulator:molecular_center_controller" scale="4" />

<BlockImage id="molecularmanipulator:omni_computation_controller" scale="4" />

<BlockImage id="molecularmanipulator:event_horizon_singularity_hub" scale="4" />
</Row>

## Default progression

| Stage | First completion unlocks |
| --- | --- |
| Tier 1: AE materials | AE processing, tier 2 materials, Pattern Assembly |
| Tier 2: Sequence Array | Sequence Array, Rewrite Array, Matrix Rewrite Core |
| Tier 2: Omni Computation | Omni Computation Core, Transfinite Compute Nexus, parts |
| Tier 3: Event Horizon | Miniature Black Hole, Miniature White Hole, all 13 Singularity Hub parts |

**Complete tier 1 once → unlock both tier 2 branches.** The tier 2 branches can run together.

| Default round | Duration | Sustained power |
| --- | --- | --- |
| Tier 1 | 30 seconds | 256 AE/t |
| Sequence Array | 30 seconds | 512 AE/t |
| Omni Computation | 30 seconds | 1024 AE/t |
| Event Horizon | 30 seconds | 2048 AE/t |

## Tier 3 materials

Complete **Sequence Array and Omni Computation at least once each** to begin Event Horizon research. Maximum tier 2 depth is not required.

| Structure | First-round cost |
| --- | --- |
| One Sequence Array | All 2,345 blueprint blocks |
| One Omni Computation Core | All 2,289 blueprint blocks |
| Combined | 19 material types; 4,634 blocks |

Both controllers and every required structure block are included; shared materials are combined. Research takes items from ME storage. Placed structures are not dismantled or credited. Later rounds multiply these costs.

<Row>
<ItemImage id="molecularmanipulator:black_hole" scale="4" />

<ItemImage id="molecularmanipulator:white_hole" scale="4" />
</Row>

| Unlocked recipe | Key ingredient |
| --- | --- |
| Miniature Black Hole ×1 | AE2 Singularity ×100K |
| Miniature White Hole ×1 | AE2 Matter Ball ×1G |
| Singularity Hub controller | Consumes Miniature Black Hole ×10K |
| White Hole Resource Core | Consumes Miniature White Hole ×10K |

Miniature Black Hole and Miniature White Hole recipes use the well's **Pattern Assembly** for large AE inputs. Other parts build on both tier 2 branches; structural casings, tracks, stairs, and slabs have batch outputs. Recipe diagrams show full ingredients.

## Start a round

1. Form the [well](matter_fabrication_well.md) and connect an online ME network with storage.
2. Open **Research** and compare available ME stock with the round's requirements.
3. Put ingredients in that network. Player inventory and port caches are not research inputs.
4. Select **Start**. Materials are paid once; supply power until completion.

Pausing, power loss, disconnection, or structure damage retains paid materials and progress. Resuming does not charge again. Each branch runs one round at a time; different branches run independently.

## Continuous orders and maximum research

| Action | Material target | Result |
| --- | --- | --- |
| Select **Order** | All materials for the next round | Starts automatically and adds one completion |
| Hold **Shift** and select **Order to maximum** | Every remaining round through the maximum | Starts automatically and reaches the maximum in one run |
| Select **Stop current research** while preparing | Cancels this research's unfinished orders | Returns cached materials to ME; full storage retains refunds for later |

Holding **Shift** changes the material list and target bonus to the maximum. Counts use K, M, G and larger units; hover for exact amounts.

**Preparation keeps ordering.** Shortages are checked once per second. If another craft consumes a required base material, the order replenishes it. Ordered outputs enter a cache owned by that research so other crafting cannot consume them. Material tooltips show cached amounts.

**Ordered research cannot stop or pause once it starts.** Power loss, disconnection or an incomplete structure retains paid materials and progress until conditions recover. Maximum orders pay every remaining round but use one research duration and its normal power per tick.

## Deep research

Each default stage allows **nine completions**, including the first unlock. Later rounds cost 2, 4, 8, 16, 32, 64, 128, and 256 times the base materials.

| Completions | Parallel limit | Processing time |
| --- | --- | --- |
| 1 | 1 | Original |
| 2 | 256 | 1/2 |
| 3 | 65.54K | 1/4 |
| 4 | 16.78M | 1/8 |
| 5 | 4.29G | 1/16 |
| 6 | 1.10T | 1/32 |
| 7 | 281.47T | 1/64 |
| 8 | 72.06P | 1/128 |
| 9 | 9.22E | 1 tick |

Bonuses affect only **fabrication recipes unlocked by this branch**. Research itself still takes 30 seconds per default round. Materials, power, and output space limit actual batches; processing never takes less than one tick.

## Your pack's rules

The Research page shows next-round or maximum costs, duration, prerequisites, and rewards. Datapacks or KubeJS can change research. Preparing and running tasks retain their target and cost snapshots; new tasks use updated rules. Preparation caches, CPU links and research progress persist with the controller.
