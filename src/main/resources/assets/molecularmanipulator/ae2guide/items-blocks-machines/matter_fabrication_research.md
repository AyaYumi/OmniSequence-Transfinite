---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: "Fabrication Research"
  icon: ae2:engineering_processor
  position: 0
---

# Fabrication Research

Research unlocks fabrication recipes and later machines. Progress belongs to the current [Fabrication Well](matter_fabrication_well.md); another well needs its own research.

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_controller" scale="4" />

<BlockImage id="molecularmanipulator:molecular_center_controller" scale="4" />

<BlockImage id="molecularmanipulator:omni_computation_controller" scale="4" />

<BlockImage id="molecularmanipulator:event_horizon_singularity_hub" scale="4" />
</Row>

## Progression

| Research | Prerequisite | First completion unlocks | Default power |
| --- | --- | --- | --- |
| Tier 1: AE materials | None | AE processing, Pattern Assembly and later research materials | 256 AE/t |
| Tier 2: Sequence Array | Tier 1 once | Sequence Array, Rewrite Array and Matrix Rewrite Core | 512 AE/t |
| Tier 2: Omni Computation | Tier 1 once | Omni Computation Core, Compute Nexus and their parts | 1024 AE/t |
| Tier 3: Event Horizon | Both tier 2 branches once | Miniature Black Hole, White Hole, Supernova and hub parts | 2048 AE/t |
| Special: Exotic Matter Studies | Tier 1 once | [Gravity Crystals and Ghost Matter](nomai_materials.md) | 256 AE/t |

Each default research round takes **30 seconds**. Both tier 2 branches can progress together; maximum depth is not needed for tier 3. Exotic Matter Studies is optional, has only one round and does not require tier 3.

## Starting research

1. Form the well and connect a powered ME network with storage.
2. Open **Research**, select a branch and check prerequisites and available ME stock against the round's cost.
3. Store ingredients in that ME network. Your inventory and input ports do not supply research.
4. With all materials available, select **Start** to pay the round's cost, then supply power until completion.

Each branch runs one round at a time; different branches can run independently. Power loss, disconnection and structure damage retain materials and progress until conditions recover.

## Ordering materials

ME autocrafting can prepare missing materials. The network needs the required patterns, ingredients and an available crafting CPU.

| Action | Effect |
| --- | --- |
| Select **Order** | Prepare the next round's materials and start automatically when ready |
| Hold **Shift** and select **Order to maximum** | Prepare every remaining round and reach maximum depth in one research run |
| Select **Stop current research** during preparation | Cancel unfinished orders and return cached materials to ME |

Holding Shift also changes the material list and displayed rewards to the maximum target. Hover counts for exact amounts.

Preparation continues to replenish shortages. Ordered products are reserved for the current research. Refunds wait if ME storage cannot accept them.

**Research started through ordering cannot stop or pause once running.** A maximum order consumes every remaining round's materials, but uses one research duration and that research's power per tick.

## Deep research

The four ordinary research branches each allow **nine completions** by default. The first unlocks recipes; later completions improve parallelism and speed for that branch's fabrication recipes. Later rounds cost 2, 4, 8, 16, 32, 64, 128 and 256 times the base materials.

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

Ingredients, power and output capacity limit actual batches. Processing takes at least one tick. Speed bonuses do not shorten research itself or improve processing in other machines.

## Tier 3 materials

The first Event Horizon round consumes one complete Sequence Array material set and one complete Omni Computation Core material set, including controllers and structure blocks.

| Material set | Count |
| --- | --- |
| Sequence Array | 2,345 blocks |
| Omni Computation Core | 2,289 blocks |
| Combined | 19 material types; 4,634 blocks |

Ingredients come from ME storage. Placed buildings are neither dismantled nor credited toward the cost. See [Black and White Holes](black_and_white_holes.md) and [Singularity Hub](event_horizon_singularity_hub.md) for tier 3 recipes and uses.

Modpacks can adjust prerequisites, depth and rewards. Check the Research page's materials, duration and power before starting.
