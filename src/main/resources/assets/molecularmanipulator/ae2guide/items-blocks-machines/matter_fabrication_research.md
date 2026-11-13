---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: "Fabrication Research"
  icon: ae2:engineering_processor
  position: 0
---

# Fabrication Research

Research unlocks Fabrication Well recipes. Repeat a branch's research to process more copies of its recipes in each batch. Each well keeps its own research progress.

<Row>
<ItemImage id="ae2:engineering_processor" scale="4" />

<ItemImage id="molecularmanipulator:star_marrow_crystal" scale="4" />

<ItemImage id="molecularmanipulator:star_chart_processor" scale="4" />
</Row>

## Complete your first research

Build a complete [Fabrication Well](matter_fabrication_well.md) and connect powered ME storage. Put research materials in that network; the well cannot take them from your inventory or input ports.

1. Open the controller and select Study.
2. Select Tier I: AE Foundation. Its first round needs 4 Inscribers, 4 Chargers, 256 Certus Quartz Crystals, 128 Fluix Crystals, and 64 each of Logic, Calculation and Engineering Processors. Check Stock / round cost and scroll to see every material and prerequisite.
3. Store the full cost in ME, then select Unlock. The well takes all materials for the round at the start.
4. Keep the structure, network and power available. The first round takes 30 seconds at 256 AE/t by default.
5. At 1 / 9 completions, you can make a [Pattern Assembly](matter_fabrication_pattern_assembly.md) and use the unlocked AE processing recipes. The recipe-unlock list shows what the branch produces.

For another round, select Deep research. Use Pause and Resume for manually started research without losing paid materials or progress. Separate branches can run at the same time if you supply enough power. A second well needs its own research completions.

## Research branches

![Main research route: AE Foundation branches into Star Marrow and Extended Materials; the two Tier III branches join before Horizon, Stellar and Transfinite](../assets/diagrams/research-route-en_us.png)

Both Tier III branches are required for later tiers. Crystallization and Exotic Matter are optional branches outside the main route.

Unless a count is stated, one completion of each prerequisite is enough. The table gives default times and power; check the research panel for your pack's requirements.

| Research | Complete first | What it opens | Time / AE per tick |
| --- | --- | --- | --- |
| I · AE Foundation | Nothing | AE processing, Pattern Assembly, Molecular Auto Crafter | 30 s / 256 |
| II · Star Marrow Materials | AE Foundation | Boards, processors, dust, alloy, signal components and later star anchors | 45 s / 512 |
| II · Extended Materials | AE Foundation | ExtendedAE materials and parts needed for progression | 45 s / 512 |
| II · Quantum Materials | Extended Materials | AdvancedAE materials and parts needed for progression | 60 s / 1024 |
| III · Sequence Engineering | Extended Materials + Star Marrow Materials | Sequence machinery, rewrite devices, Infinite Crafting Storage and Infinite Parallel Matrix | 60 s / 1024 |
| III · Computation Architecture | Quantum Materials + Star Marrow Materials | Computation machinery and Omniscient Computation Matrix | 90 s / 2048 |
| IV · Event Horizon Materials | Sequence Engineering + Computation Architecture | Black/white holes and hub building parts | 90 s / 4096 |
| V · Stellar Cores | Event Horizon Materials | Miniature Supernova, hub controller and White Hole Resource Core | 120 s / 8192 |
| VI · Transfinite Integration | Stellar Cores | Transfinite Compute Nexus | 180 s / 16384 |
| Optional · Signal-Induced Crystallization | Star Marrow Materials | Make crystals from signal and water with a reusable Flawless parent rock | 60 s / 512 |
| Optional · Exotic Matter Studies | AE Foundation | Ghost Matter and Gravity Crystals | 30 s / 256 |

Naturally grown crystals are enough for basic star-material processing. You only need signal and a Flawless parent rock for the optional Signal-Induced Crystallization route.

### Machine integration

To bring other machines' recipes into the well, complete both Tier III branches at least three times: Sequence Engineering 3 / 9 and Computation Architecture 3 / 9. The first round counts toward three.

This requirement applies to all default machine integrations and pack-added machine imports, including their production recipes. Tier II Extended Materials and Quantum Materials provide the startup recipes needed to reach those branches. Integrations appear when their required mods are installed.

### Star Orrery research

The [Star Computation Engine](star_computation_engine.md) is a nine-ring Star Orrery. Its research is separate from well research. The default Recipe Tree contains one team unlock, Single-Block Integration. Reach L3 and click Unlock to obtain it for 0 bit.

The Molecular Sequence Rewrite Array and Transfinite Compute Nexus need this permission as well as their well research. By default, the three matrices, star anchors and Molecular Auto Crafter need no extra star-research unlock.

## Order research materials through ME

The network needs suitable patterns, ingredients and an available crafting CPU.

| Button | What happens |
| --- | --- |
| Order | Prepare the next round, then start research automatically. |
| Hold Shift, then Order to Maximum | Show and prepare the combined cost of every remaining round, then finish them in one research run. |
| Stop Current Research, while preparing | Cancel preparation and unfinished material orders; return reserved materials to ME. |

Once ordered research starts, it cannot be stopped or paused. Ordering to maximum pays for all remaining rounds but takes one research duration at that branch's listed power. Check the total before ordering. Refunds wait safely if ME storage is full.

## Larger batches

Every default research has nine rounds, including Exotic Matter Studies and machine integrations. The first unlocks recipes. Later rounds raise parallelism, the number of recipe executions that can run together. Each execution still takes the same time.

| Completed rounds | Parallel limit |
| --- | --- |
| 1 | 1 |
| 2 | 256 |
| 3 | 65,536 |
| 4 | 16.78M |
| 5 | 4.29G |
| 6 | 1.10T |
| 7 | 281.47T |
| 8 | 72.06P |
| 9 | 9.22E |

Expansion rounds have their own material costs. Read the next round's list before ordering. Batches still need enough ingredients, power and output space.

A Star Computation Engine on the controller's ME network speeds up production, but leaves research timers unchanged. Recipes that need no research use AE Foundation's completed parallel limit, or one execution before any completion.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Materials are in your inventory but stock is zero | Store them in the controller's ME network, not its ports. |
| Unlock / Deep research is unavailable | Check all prerequisite counts, current ME stock, structure and network. |
| Machine integration stays locked | Complete both Tier III branches three times. |
| Material ordering waits | Open Details; check missing patterns, ingredients and CPU availability. |
| Research stops advancing | Restore structure, network and sufficient power. Paid materials and progress remain. |
| Array or nexus recipe is still locked | Check both its well research and the L3 Single-Block Integration unlock in the Star Orrery. |

## Using unlocked recipes

Install a [Pattern Assembly](matter_fabrication_pattern_assembly.md) to order well products through ME. Grow [Star Marrow Crystals](star_marrow_crystals.md) for star materials, or follow [Exotic Matter Studies](nomai_materials.md) for gravity tools.
