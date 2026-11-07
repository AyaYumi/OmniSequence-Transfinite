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

The Star Computation Engine now uses only the **Nine-Ring Star Orrery** structure. An L0 machine requires both a **complete central 3×3×3 assembly**—1 core, 22 casing and 4 observation ports—and **928 physical foundation blocks**. Any missing central part prevents formation, even with a complete foundation. Complete successive rings from the inside out to advance through L9. It supplies production benefits to the same ME network and unlocks permanent team recipes through its Recipe Tree. The old cube cannot form independently, and four-direction anchor columns are no longer supported.

<Row>
<BlockImage id="molecularmanipulator:star_computation_engine_core" scale="4" />

<BlockImage id="molecularmanipulator:star_orrery_engraved_plinth" scale="4" />

<BlockImage id="molecularmanipulator:star_orrery_gilded_plinth" scale="4" />
</Row>

## Building

1. Open JEI's **Star Orrery Structure** category to inspect the L1/L2/L3 layouts and full material lists. Build by hand or order a complete building package as described below.
2. Build the physical foundation and place the **Star Computation Engine Core** **37 blocks above its lowest layer**. Complete the central **3×3×3** around it with **22 casing and 4 observation ports**, following the layout below. Only the complete central assembly together with the complete foundation forms automatically at L0.
3. Install anchors in the fixed slots for the core's facing, starting with the first ring. A ring counts only when all its slots use the required tier and every earlier ring is complete. Rings cannot be skipped.
4. No right-click or power is required **for formation**; network benefits and research actions require a powered, active ME connection with an available channel.

### Required central 3×3×3 assembly

- Put **1 Star Computation Engine Core at the exact center**. Place **1 observation port at the center of each of the four horizontal-facing sides** (north, south, east and west), and fill the remaining **22 positions with engine casing**. All 27 positions contain real blocks; the ports are not empty openings.
- With the core as `(0, 0, 0)`, the assembly fills offsets -1 through +1 on all three axes. The ports are at `(±1, 0, 0)` and `(0, 0, ±1)`; all positions other than these four and the core use casing.
- This is the center of the orrery, **not a separate small-cube machine**. A missing or incorrect central part prevents L0 formation and all engine benefits, just as a missing foundation block does. Complete both structures before adding rings.

### Physical open ring-frame foundation

- 520 **Star Orrery Engraved Plinth** blocks and 408 **Star Orrery Gilded Plinth** blocks make **928 real blocks** in total. Gold blocks and engine casings are not substitutes.
- Measure from the core: horizontal offsets `x`, `z`, radius `r = sqrt(x² + z²)`. The foundation is **37 blocks across** (radius 18) and only fills Y=-37, -36, -35 and -34. The central `r <= 5` shaft stays open through all four layers.

| Y relative to core | Physical block layout | Block count |
| --- | --- | ---: |
| -37 | Antigravity ring `7 < r <= 10`, outer ring `15 < r <= 18`, plus four three-block-wide bridges where `5 < r <= 18` and either `abs(x) <= 1` or `abs(z) <= 1` | 552 |
| -36 | Middle ring `12 < r <= 14` and well collar `5 < r <= 7` | 240 |
| -35 | Well collar `5 < r <= 7` only | 68 |
| -34 | Well collar `5 < r <= 7` only | 68 |

- Gilded plinth covers the outer one-block edge of every ring and the well collar, plus each bridge centerline; engraved plinth fills the rest. Use JEI's layer controls to inspect the exact layout.
- **One missing or wrong block stops formation and its benefits.** Putting it right restores the machine automatically; right-click an incomplete core to be told which block is expected and where.

**What formation looks like**

- The **central 3×3×3 assembly remains physically present**. Formation hides the original meshes of its **26 surrounding blocks (22 casing and 4 observation ports)**, and the core renders a single **3×3×3 model**. Their blocks, block entities, collision and drops remain; they are not removed or consumed. Losing formation restores their ordinary meshes.
- Unformed foundation blocks keep their cool gray stone and blue-gray, fine gold-trimmed textures. Formation also hides the meshes of the **928 foundation blocks**, showing the existing floating metal rings, front floating gem and energy well. Those blocks keep their collision and drops; breaking the structure or the core brings the plain appearance back without consuming materials. **The gaps between rings are genuinely empty**, so there is no old solid terrace to bump into. Leftover blocks from an older layout, or anything you placed yourself, still collide and must be cleared by hand.
- Rings appear one at a time with the server-verified contiguous lit-ring count: L0 shows none, L3 the first three, L9 all nine. Active ring planes tumble and precess around the core, and the crystals set into them travel with the ring. Reference, Quantum and Terminal tiers keep their teal, violet and gold materials.
- The **120-division dial** floats below the core in a slightly tilted plane and turns slowly on its own; the rest of the pedestal stays still.
- Functional anchors stay at their real build positions for selection, cabling and upgrades, with small datum markers that do not follow the rings.

**Dynamic effects**

The client's **dynamic effects** setting (0 off, 1 reduced, 2 full) controls animation and detail. Light **at least one ring** to enable the rotating dial, inward nebula flow, ink well and code rain:

- **Level 0**: keeps the machine, nebula and pedestal visible and static.
- **Level 1**: enables these effects with reduced detail.
- **Level 2**: adds finer detail, belt crystals and close-range anchor glow.

Flow speed follows the engine's real state: **0.35×** while disconnected or idle, **1×** while it is the selected, channel-active engine on the ME network, **2×** while an overclock is actually being paid. Running out of information or losing the grid slows it back down. Effects fade out with distance and world fog.

**Existing worlds**

Old cores switch to the orrery rules during a server-side structure refresh. The old cube **cannot form independently**, and the old four-direction anchor columns are unsupported. Check the central 27-block assembly, manually complete any missing parts and the real foundation, then arrange anchors in rings; permanent team unlocks are retained.

Existing casing and observation ports remain **required central structure parts** as well as ingredients in their original recipes. When verified as members of a formed orrery's central assembly, they provide menu and ME access to its actual core. Loose parts do not connect to ME or proxy a menu. Updating does not automatically delete player blocks; unwanted legacy blocks still have collision and must be cleared by hand.

Already sealed machine archives restore their original contents without rewriting them, supplying missing parts, refunding materials or exchanging them for a larger level package for free. A restored old cube must still meet both the complete central 3×3×3 and real foundation requirements; add missing blocks by hand. Old unsealed small-template packages can no longer deploy as new buildings and are not exchanged for the more expensive L1/L2/L3 packages for free.

`star_orrery_l0` through `star_orrery_l9` are development structure templates, not the survival package acquisition flow. **Template replay is for disposable test worlds only**: block and air writes can overwrite existing buildings. Never replay a template over a built area in your main save.

## ME connection and interface

Connect a real ME cable to the **core, a valid central casing or observation port, or a valid ring anchor**. Central parts and ring anchors must belong to that formed orrery and have their membership verified by the server; each exposes the **actual core's ME connection**, not an independent engine. Foundation blocks and loose casing or observation ports do not provide this connection. The whole engine is **one network device**, requiring **1 channel** and **16 AE/t idle power**, not one channel per access point. These are real AE2 network connections, not a cosmetic connection indicator.

Right-click the **formed core, a valid central casing or observation port, or a valid ring anchor** to open that same core's live interface. It has exactly **two tabs**:

- **Overview**: structure and ME status, effective computing power and level, valid counts for all three anchor tiers, four benefit icons, and the network's information balance and actual bit/t consumption. Use the overclock switch or **Recheck structure** here. It shows the real lit-ring count and nine-ring slot list; L9 has **96 Reference / 168 Quantum / 240 Terminal** anchors.
- **Recipe Tree**: select a node to see its computing power threshold, prerequisites, one-time information cost and actual recipe outputs. Hover the output icons or contents summary for details, then pay to unlock. Selection alone does not purchase anything; payment and the result are confirmed by the server.

Overview and Recipe Tree colors follow the server-synchronized level live: **L0–L3 teal, L4–L6 blue-violet, L7–L9 gold**, without reopening the screen. The core and Building Access Terminal share the **Nine-Ring Star Orrery diagram, ring list and three anchor-tier counts**. They show the server-verified contiguous lit-ring count, with unfinished rings locked. There is no separate anchor tab or structure-projection button; use JEI's structure preview before building and the Star Chart Calibrator when deploying a package.

Only the **best formed, powered, channel-active engine on the same ME network** supplies production benefits: higher level wins; at the same level an overclocked engine wins. Multiple engines do **not** add their computing power, parallelism, storage or speed together; joining or stacking structures grants no extra benefits. The displayed values describe the opened engine; a weaker engine does not add another bonus. Formation or an open screen alone does not mean the benefits are active.

Whole-machine sealing includes **all 27 central blocks: the actual core, 22 casing and 4 observation ports**. Opening the interface through a valid central part still addresses that same core; sealing does not leave these parts behind. See [Folded Buildings and Relocation](folded_buildings.md).

## Star Position Anchors

**Reference**, **Quantum** and **Terminal Star Position Anchors** occupy fixed ring slots around the core instead of columns extending in four directions. A complete central 3×3×3 assembly and foundation with no lit rings is L0. Each successive complete ring adds one level, up to L9. The ring rules below apply only while the central assembly and foundation remain complete.

| Ring | Required anchor tier | Blocks in this ring |
| --- | --- | ---: |
| 1 | Reference | 24 |
| 2 | Reference | 32 |
| 3 | Reference | 40 |
| 4 | Quantum | 48 |
| 5 | Quantum | 56 |
| 6 | Quantum | 64 |
| 7 | Terminal | 72 |
| 8 | Terminal | 80 |
| 9 | Terminal | 88 |

Fill every slot with that ring's required tier; tiers cannot be mixed or substituted. Positions rotate with the core's horizontal facing. Functional anchors stay at their build coordinates, so do not move blocks to follow the animated ring planes. A missing block, wrong tier or ownership conflict prevents that ring and all outer rings from counting; verified complete inner rings can still count. Unloaded rings, or saved rings not yet revalidated after loading, do not grant benefits from their saved state alone.

### Lit rings and effective computing power

The level is the **number of consecutive complete rings starting at ring 1**. Effective computing power uses that level's fixed value in the table below, not the retired columns' progressive-cap formula. Loose anchors, columns or completed outer rings alone cannot skip levels. The valid-anchor count in the interface remains a **block count**, not a computing power value.

## Network benefits and overclocking

The table shows **normal, non-overclocked** capabilities. Assembler parallelism applies per supported sequence assembler; CPU parallelism and crafting storage apply per Omni-Computation CPU. Crafting storage is **not** general-purpose ME item storage. The Fabrication Well uses the speed multiplier for its recipe duration.

| Level | Required computing power | Assembler parallelism | CPU parallelism | CPU crafting storage | Well speed |
| --- | ---: | ---: | ---: | ---: | ---: |
| L1 | 240 | 4,096 | 512 | 1 GiB | ×2 |
| L2 | 560 | 16,384 | 1,024 | 4 GiB | ×3 |
| L3 | 960 | 65,536 | 2,048 | 16 GiB | ×4 |
| L4 | 5,760 | 262,144 | 4,096 | 64 GiB | ×8 |
| L5 | 11,360 | 1,048,576 | 8,192 | 256 GiB | ×12 |
| L6 | 17,760 | 4,194,304 | 16,384 | 1 TiB | ×16 |
| L7 | 89,760 | 16,777,216 | 32,768 | 4 TiB | ×32 |
| L8 | 169,760 | 67,108,864 | 65,536 | 16 TiB | ×48 |
| L9 | 257,760 | 268,435,456 | 131,072 | 64 TiB | ×64 |

For `L = 1..9`, the formulas are `4,096 × 4^(L−1)` assembler parallelism, `512 × 2^(L−1)` CPU parallelism and `1 GiB × 4^(L−1)` CPU crafting storage. **L1–L6 use physical CPUs; L7 and above use virtual CPUs.** With no complete lit ring (L0), or without an active engine on the network, machines retain their unassisted baseline.

**Deep research improves Fabrication Well parallelism only; it does not shorten recipes.** The default base duration is **400 ticks (20 seconds)**. With an active engine, duration is `ceil(base duration / speed multiplier)`, at least 1 tick; a recipe with its own duration uses that value instead of 400. For example, a default recipe takes 200 ticks at normal L1 and 7 ticks at normal L9.

### Overclocking

Overclocking continuously extracts **Star Signal Flow from the same ME network's storage**. It is separate from the 16 AE/t idle power and from one-time research payments; the interface shows the real available balance in **bit** and the actual consumption in **bit/t**. A non-overclocked engine consumes no information for production benefits.

- The cost is `1 << L` bit/t: **2, 4, 8, 16, 32, 64, 128, 256, 512 bit/t** at L1 through L9 respectively. The selected best engine pays for the network benefit, not once per receiving machine.
- At **L1–L8**, overclocking doubles all four benefits: assembler parallelism, CPU parallelism, CPU crafting storage and Well speed. It does not double computing power or bypass research requirements.
- At **L9**, paid overclocking provides **unlimited assembler/CPU parallelism, unlimited CPU crafting storage and 1-tick Well processing**. Normal L9 remains finite as listed above.
- Enabling overclock requires enough information for at least one tick. Each tick must actually be paid; insufficient information switches overclock off. Formation or network failure also disables overclock. When an engine remains active, it falls back to its normal finite benefits; an offline or broken engine supplies none. Permanent research unlocks are not erased.

## Permanent team research

The **Recipe Tree** has five permanent team nodes. Unlocking requires a formed, active engine at the listed computing power threshold, completed prerequisites and a **one-time payment from ME information storage**. Computing power is only a threshold and is **not spent**. Overclocking changes neither thresholds nor unlocks.

Progress is saved for the engine's bound team (the placing player's identity when no team is available), shared by that team's engines and retained across engine dismantling, chunk unloads and server restarts. To use a gated Fabrication Well recipe, its ME network still needs an active engine exposing the team's unlock; the recipe's original matter-research requirements also remain in force.

| Node | Required level / computing power | One-time cost | Prerequisite |
| --- | --- | ---: | --- |
| Star Computation Foundation (`foundation`) | L1 / 240 | 512 bit | None |
| Quantum Materials (`material`) | L3 / 960 | 2,048 bit | Star Computation Foundation |
| Quantum Sequencing (`quantum`) | L6 / 17,760 | 8,192 bit | Quantum Materials |
| Terminal Materials (`terminal`) | L7 / 89,760 | 16,384 bit | Star Computation Foundation |
| Transfinite Fabrication (`transfinite`) | L9 / 257,760 | 65,536 bit | Quantum Sequencing **and** Terminal Materials |

These nodes gate the following recipes; the interface resolves their actual outputs from the server's current recipes:

- **Star Computation Foundation**: Sequence Auto Crafter; Sequence Array Casing, Quantum Crystal, Phase Glass and Stabilizer.
- **Quantum Materials**: **Quantum Star Position Anchor**, Molecular Sequence Rewrite Array, Sequence Rewrite Core, Sequence Array Controller and Assembler Matrix Sequence Rewrite Core.
- **Quantum Sequencing**: **Terminal Star Position Anchor**, Omni-Computation Core, Computation Core Frame, Computation Structure Glass, Computation Crystal Pylon, Data Entangler Node, Computation Energy Stabilizer and Sequence Output Node.
- **Terminal Materials**: Infinite Crafting Storage Matrix, Infinite Parallel Matrix and Omniscient Computation Matrix.
- **Transfinite Fabrication**: Transfinite Compute Nexus.

The anchor progression deliberately avoids a circular requirement: **complete the first three rings with 96 Reference anchors to reach L3 and unlock Quantum anchors through Quantum Materials. Keep those rings and complete rings 4–6 to reach L6 and unlock Terminal anchors through Quantum Sequencing.** Neither upgrade requires its own higher-tier anchor first.

## Obtaining

Harvest crystals from star marrow crystal clusters, then use an Inscriber to make crystal dust and Star Chart Processors. Make alloy ingots in the **AdvancedAE Reaction Chamber**. Craft engine cores, casing, observation ports and Reference Star Position Anchors at a **crafting table**, and prepare both foundation materials from their recipes. Quantum and Terminal upgrades remain in the Fabrication Well and require **Tier 1: AE fabrication** research, **plus their respective permanent engine nodes** described above. This initial material chain needs neither a Flawless parent rock nor Star Signal Flow; the engine-node unlocks and optional overclocking do consume information.

### Complete L1/L2/L3 packages and JEI previews

1. Select L1, L2 or L3 in JEI's **Star Orrery Structure** category. Its three structure recipes show **every construction material and the matching package output**, including all 27 central blocks and the complete physical foundation, not just the next ring.
2. Controls match the other building previews: **left-drag to rotate, right-drag to pan and scroll to zoom**. Use the layer buttons to inspect individual layers or reset the view. The negative-Y foundation layers are included.
3. Connect a **Spatial Folding Assembler** to ME and order the desired package. It advertises seven packages: Matter Fabrication Well, Sequence Array, Omni Computation Core, Event Horizon Singularity Hub and Star Orrery L1/L2/L3. Each order consumes the full material list below. The creative inventory also offers all three orrery packages.

| Package | Included rings | Core | Casing | Observation ports | Engraved plinth | Gilded plinth | Reference anchors | Total blocks |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Star Orrery L1 | Ring 1 | 1 | 22 | 4 | 520 | 408 | 24 | 979 |
| Star Orrery L2 | Rings 1–2 | 1 | 22 | 4 | 520 | 408 | 56 | 1011 |
| Star Orrery L3 | Rings 1–3 | 1 | 22 | 4 | 520 | 408 | 96 | 1051 |

**L1/L2/L3 are the first three lit-ring levels, not the Reference/Quantum/Terminal tiers.** All three packages use only Reference anchors. Each is a complete machine for an empty site, not an incremental upgrade, and the engine still advances through all nine levels.

Keep the package in your inventory and **right-click with the Star Chart Calibrator** to select a site. Choose the package, facing and base coordinates, then confirm deployment. Base Y is the lowest physical foundation layer: **core Y = base Y + 37**. Esc only closes the panel; right-click again to reopen the pinned site. Only **Cancel point** releases it for a new location. See [Folded Buildings and Relocation](folded_buildings.md) for the full workflow.

### Casing and observation ports

The central assembly requires **22 casing and 4 observation ports** in addition to the core. Valid central members of a formed orrery provide menu and ME access to its real core; loose parts do not. Their existing recipes and ingredient uses remain unchanged. Each recipe yields **4 blocks**.

<RecipeFor id="molecularmanipulator:star_computation_engine_casing" />

<RecipeFor id="molecularmanipulator:star_computation_observation_port" />

### Engine core

At a crafting table, combine 4 casing, 2 alloy ingots, 1 Star Chart Processor, 1 Star Marrow Crystal and 1 Energy Acceptor to make **1 core**.

<RecipeFor id="molecularmanipulator:star_computation_engine_core" />

### Physical foundation

<RecipeFor id="molecularmanipulator:star_orrery_engraved_plinth" />

<RecipeFor id="molecularmanipulator:star_orrery_gilded_plinth" />

### Anchor tiers

At a **crafting table**, combine 4 alloy ingots, 2 casing, 2 Star Marrow Crystals and 1 Star Chart Processor to make **9 Reference Star Position Anchors**. This is the recipe batch size, not a ring's slot count. Quantum and Terminal upgrades remain in the **Fabrication Well**; each consumes 9 anchors of the previous tier and produces 9 upgraded anchors. In addition to Tier 1: AE fabrication research, the Quantum recipe requires **Quantum Materials (L3 / 960 computing power, 2,048 bit)** and the Terminal recipe requires **Quantum Sequencing (L6 / 17,760 computing power, 8,192 bit)**, with the prerequisites listed in the tree.

<RecipeFor id="molecularmanipulator:reference_star_position_anchor" />

<RecipeFor id="molecularmanipulator:quantum_star_position_anchor" />

<RecipeFor id="molecularmanipulator:terminal_star_position_anchor" />
