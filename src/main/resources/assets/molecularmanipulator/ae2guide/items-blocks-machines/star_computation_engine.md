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
- molecularmanipulator:base_entropy_anchor
- molecularmanipulator:quantum_entropy_anchor
- molecularmanipulator:terminal_entropy_anchor
---

# Star Computation Engine

The Star Computation Engine is a **3×3×3** multiblock built from a core, casing and observation ports. Once formed, the entire machine switches to a new continuous shell with a lens core, scanning light and animated star signal flow. The formed panels cover the observation ports, so they are no longer transparent. The central core's facing determines the front lens direction. Dismantling the structure restores the individual blocks' unformed appearance.

<Row>
<BlockImage id="molecularmanipulator:star_computation_engine_core" scale="4" />

<BlockImage id="molecularmanipulator:star_computation_engine_casing" scale="4" />

<BlockImage id="molecularmanipulator:star_computation_observation_port" scale="4" />
</Row>

## Building

1. Place the **Star Computation Engine Core** in the center of a 3×3×3 volume.
2. Put a **Star Computation Observation Port** in each of the four side centers of the middle layer.
3. Fill the other 22 positions with **Star Computation Engine Casing**:
   - 9 casing blocks on the top layer
   - 4 corner casing blocks on the middle layer
   - 9 casing blocks on the bottom layer
4. The structure forms automatically. No right-click or power is required **for formation**; network benefits and research actions require a powered, active ME connection with an available channel.

| Part | Amount |
| --- | --- |
| Star Computation Engine Core | 1 |
| Star Computation Observation Port | 4 |
| Star Computation Engine Casing | 22 |

### Nine-ring armillary preview mode

Ordinary cores use the structure above and need nothing extra. The nine-ring preview is **opt-in**: cores placed from the `entropy_armillary_l0` through `entropy_armillary_l9` templates also need a **physical open ring-frame foundation** underneath. Legacy anchor-column engines do not.

**Building the foundation**

- 520 **Armillary Carved Plinth** blocks and 408 **Armillary Gilded Trim** blocks make **928 real blocks** in total. Gold blocks and engine casings are not substitutes.
- Measure from the core: horizontal offsets `x`, `z`, radius `r = sqrt(x² + z²)`. The foundation is **37 blocks across** (radius 18) and only fills Y=-37, -36, -35 and -34. The central `r <= 5` shaft stays open through all four layers.

| Y relative to core | Physical block layout | Block count |
| --- | --- | ---: |
| -37 | Antigravity ring `7 < r <= 10`, outer ring `15 < r <= 18`, plus four three-block-wide bridges where `5 < r <= 18` and either `abs(x) <= 1` or `abs(z) <= 1` | 552 |
| -36 | Middle ring `12 < r <= 14` and well collar `5 < r <= 7` | 240 |
| -35 | Well collar `5 < r <= 7` only | 68 |
| -34 | Well collar `5 < r <= 7` only | 68 |

- Trim covers the outer one-block edge of every ring and the well collar, plus each bridge centerline; carved plinth fills the rest. The templates contain the exact layout if you would rather copy it.
- **One missing or wrong block stops formation and its benefits.** Putting it right restores the machine automatically; right-click an incomplete core to be told which block is expected and where.

**What formation looks like**

- Unformed foundation blocks keep their cool gray stone and blue-gray, fine gold-trimmed textures. Formation hides only the meshes of the **928 foundation blocks**, showing the floating metal rings, front floating gem and energy well. Those blocks keep their collision and drops; breaking the structure or the core brings the plain appearance back without consuming materials. **The gaps between rings are genuinely empty**, so there is no old solid terrace to bump into. Leftover blocks from an older layout, or anything you placed yourself, still collide and must be cleared by hand.
- Rings appear one at a time with the server-verified contiguous lit-ring count: L0 shows none, L3 the first three, L9 all nine. Active ring planes tumble and precess around the core, and the crystals set into them travel with the ring. Base, Quantum and Terminal tiers keep their teal, violet and gold materials.
- The **120-division dial** floats below the core in a slightly tilted plane and turns slowly on its own; the rest of the pedestal stays still.
- Functional anchors stay at their real build positions for selection, cabling and upgrades, with small datum markers that do not follow the rings.

**Dynamic effects**

The client's **dynamic effects** setting (0 off, 1 reduced, 2 full) controls animation and detail. Light **at least one ring** to enable the rotating dial, inward nebula flow, ink well and code rain:

- **Level 0**: keeps the machine, nebula and pedestal visible and static.
- **Level 1**: enables these effects with reduced detail.
- **Level 2**: adds finer detail, belt crystals and close-range anchor glow.

Flow speed follows the engine's real state: **0.35×** while disconnected or idle, **1×** while it is the selected, channel-active engine on the ME network, **2×** while an overclock is actually being paid. Running out of information or losing the grid slows it back down. Effects fade out with distance and world fog.

**Existing worlds**

Upgrading the JAR alone changes nothing in a save: no old blocks are removed and no new ones appear. A preview built on the previous solid terraces has to be rebuilt by hand to the layout above, with the extra blocks taken down. Old foundation blocks saved in the hidden, formed state become visible again when their chunk loads so you can dismantle them; nothing is deleted automatically and the materials are kept.

**Template replay is for disposable test worlds only.** All 10 templates are **75×76×75**, with the core at local **(37,38,37)**, holding 27 engine blocks, 504 candidate anchor slots and 928 foundation blocks. Replaying one at the **same absolute template origin** as an old preview also writes air over the **10156 retired positions** of the old 11084-block terraces and over unused anchor slots; it does not clear the whole bounding box. **Those writes replace whatever stands there, not just old foundation blocks. Never replay a template over a built area in your main save.**

## ME connection and interface

Connect a real ME cable to the **formed casing or observation ports**; attached, formed anchors also expose the same core's ME connection. The whole engine is **one network device**, requiring **1 channel** and **16 AE/t idle power**, not one channel per shell block or anchor. These are real AE2 network connections, not a cosmetic connection indicator.

Right-click **any formed core, casing, observation port or valid attached anchor** to open that engine's live interface. It has exactly **two tabs**:

- **Overview**: structure and ME status, effective entropy and level, valid counts for all three anchor tiers, four benefit icons, and the network's information balance and actual bit/t consumption. Use the overclock switch or **Recheck structure** here. Armillary mode shows the real lit-ring count and nine-ring slot list; L9 has **96 Base / 168 Quantum / 240 Terminal** anchors.
- **Recipe Tree**: select a node to see its entropy threshold, prerequisites, one-time information cost and actual recipe outputs. Hover the output icons or contents summary for details, then pay to unlock. Selection alone does not purchase anything; payment and the result are confirmed by the server.

Overview and Recipe Tree colors follow the server-synchronized level live: **L0–L3 teal, L4–L6 blue-violet, L7–L9 gold**, without reopening the screen. Ordinary and armillary cores share the **nine-ring Entropy Armillary diagram, ring list and three anchor-tier counts**, including when opened through the Building Access Terminal; the old four-arm schematic is no longer displayed. Armillary cores use the server-validated contiguous lit-ring count, with unfinished rings locked. On ordinary cores, schematic rings represent capability levels and the current ring's dots represent entropy progress toward the next level, **not physically placed ring anchors**. There is no separate anchor tab, full cross-section display or structure-projection button. This visual change does not alter construction rules: **legacy columns still require complete, contiguous 3×3 cross-sections**.

Only the **best formed, powered, channel-active engine on the same ME network** supplies production benefits: higher level wins; at the same level an overclocked engine wins. Multiple engines do **not** add their entropy, parallelism, storage or speed together. The displayed values describe the opened engine; a weaker engine does not add another bonus. Formation or an open screen alone does not mean the benefits are active.

## Entropy Anchors

**Base Entropy Anchor**, **Quantum Entropy Anchor** and **Terminal Entropy Anchor** can form optional columns with a **3×3 cross-section**, extending outward from any of the engine's four horizontal faces. Anchors are not required to form the engine's 3×3×3 body.

- Place the first cross-section immediately outside and flush against the engine shell.
- Each cross-section needs **9 anchors**. Keep successive cross-sections complete and contiguous, with no gaps.
- Tiers may be mixed, including within a cross-section; each anchor keeps its own tier's appearance.
- The outermost complete cross-section displays an end cap. Horizontal extensions are checked only in already-loaded chunks.

Dismantling the engine or breaking a column restores affected anchors to their original unformed appearance. Only complete, contiguous cross-sections connected to the engine count toward its effective anchors and entropy; a gap or missing block prevents that cross-section and the portion beyond it from contributing. The effective-anchor count is a **block count**, not the capped entropy value.

### Effective entropy and tier caps

Each valid anchor contributes according to its tier, but the tiers use **progressive caps**: higher tiers replace the lower-tier contribution within the next cap, rather than adding three independent maximum bonuses.

| Anchor | Entropy per block | Cap after including this tier |
| --- | ---: | ---: |
| Base Entropy Anchor | 10 | 900 |
| Quantum Entropy Anchor | 100 | 9,000 |
| Terminal Entropy Anchor | 1,000 | 90,000 |

With valid Base, Quantum and Terminal block counts `b`, `q` and `t`, calculate in order:

```text
B = min(900, 10 × b)
Q = min(9,000, B + 100 × q)
E = min(90,000, Q + 1,000 × t)
```

`E` is the engine's effective entropy. For example, one complete layer of 9 Base anchors gives 90 (L1), and 90 Base anchors reach 900 (L3). With 90 Quantum anchors the total is capped at 9,000 even if Base anchors remain; with 90 Terminal anchors it is capped at 90,000, not 99,900. Valid anchors beyond a cap may still appear in the block count without increasing the level.

## Network benefits and overclocking

The table shows **normal, non-overclocked** capabilities. Assembler parallelism applies per supported sequence assembler; CPU parallelism and crafting storage apply per Omni-Computation CPU. Crafting storage is **not** general-purpose ME item storage. The Fabrication Well uses the speed multiplier for its recipe duration.

| Level | Required entropy | Assembler parallelism | CPU parallelism | CPU crafting storage | Well speed |
| --- | ---: | ---: | ---: | ---: | ---: |
| L1 | 90 | 4,096 | 512 | 1 GiB | ×2 |
| L2 | 270 | 16,384 | 1,024 | 4 GiB | ×3 |
| L3 | 900 | 65,536 | 2,048 | 16 GiB | ×4 |
| L4 | 1,800 | 262,144 | 4,096 | 64 GiB | ×8 |
| L5 | 4,500 | 1,048,576 | 8,192 | 256 GiB | ×12 |
| L6 | 9,000 | 4,194,304 | 16,384 | 1 TiB | ×16 |
| L7 | 18,000 | 16,777,216 | 32,768 | 4 TiB | ×32 |
| L8 | 45,000 | 67,108,864 | 65,536 | 16 TiB | ×48 |
| L9 | 90,000 | 268,435,456 | 131,072 | 64 TiB | ×64 |

For `L = 1..9`, the formulas are `4,096 × 4^(L−1)` assembler parallelism, `512 × 2^(L−1)` CPU parallelism and `1 GiB × 4^(L−1)` CPU crafting storage. **L1–L6 use physical CPUs; L7 and above use virtual CPUs.** Below 90 entropy (L0), or without an active engine on the network, machines retain their unassisted baseline.

**Deep research improves Fabrication Well parallelism only; it does not shorten recipes.** The default base duration is **400 ticks (20 seconds)**. With an active engine, duration is `ceil(base duration / speed multiplier)`, at least 1 tick; a recipe with its own duration uses that value instead of 400. For example, a default recipe takes 200 ticks at normal L1 and 7 ticks at normal L9.

### Overclocking

Overclocking continuously extracts **Star Signal Flow from the same ME network's storage**. It is separate from the 16 AE/t idle power and from one-time research payments; the interface shows the real available balance in **bit** and the actual consumption in **bit/t**. A non-overclocked engine consumes no information for production benefits.

- The cost is `1 << L` bit/t: **2, 4, 8, 16, 32, 64, 128, 256, 512 bit/t** at L1 through L9 respectively. The selected best engine pays for the network benefit, not once per receiving machine.
- At **L1–L8**, overclocking doubles all four benefits: assembler parallelism, CPU parallelism, CPU crafting storage and Well speed. It does not double entropy or bypass research requirements.
- At **L9**, paid overclocking provides **unlimited assembler/CPU parallelism, unlimited CPU crafting storage and 1-tick Well processing**. Normal L9 remains finite as listed above.
- Enabling overclock requires enough information for at least one tick. Each tick must actually be paid; insufficient information switches overclock off. Formation or network failure also disables overclock. When an engine remains active, it falls back to its normal finite benefits; an offline or broken engine supplies none. Permanent research unlocks are not erased.

## Permanent team research

The **Recipe Tree** has five permanent team nodes. Unlocking requires a formed, active engine at the listed entropy threshold, completed prerequisites and a **one-time payment from ME information storage**. Entropy is only a threshold and is **not spent**. Overclocking changes neither thresholds nor unlocks.

Progress is saved for the engine's bound team (the placing player's identity when no team is available), shared by that team's engines and retained across engine dismantling, chunk unloads and server restarts. To use a gated Fabrication Well recipe, its ME network still needs an active engine exposing the team's unlock; the recipe's original matter-research requirements also remain in force.

| Node | Required entropy | One-time cost | Prerequisite |
| --- | ---: | ---: | --- |
| Star Computation Foundation (`foundation`) | 90 | 512 bit | None |
| Quantum Materials (`material`) | 900 | 2,048 bit | Star Computation Foundation |
| Quantum Sequencing (`quantum`) | 9,000 | 8,192 bit | Quantum Materials |
| Terminal Materials (`terminal`) | 18,000 | 16,384 bit | Star Computation Foundation |
| Transfinite Fabrication (`transfinite`) | 90,000 | 65,536 bit | Quantum Sequencing **and** Terminal Materials |

These nodes gate the following recipes; the interface resolves their actual outputs from the server's current recipes:

- **Star Computation Foundation**: Sequence Auto Crafter; Sequence Array Casing, Quantum Crystal, Phase Glass and Stabilizer.
- **Quantum Materials**: **Quantum Entropy Anchor**, Molecular Sequence Rewrite Array, Sequence Rewrite Core, Sequence Array Controller and Assembler Matrix Sequence Rewrite Core.
- **Quantum Sequencing**: **Terminal Entropy Anchor**, Omni-Computation Core, Computation Core Frame, Computation Structure Glass, Computation Crystal Pylon, Data Entangler Node, Computation Energy Stabilizer and Sequence Output Node.
- **Terminal Materials**: Infinite Crafting Storage Matrix, Infinite Parallel Matrix and Omniscient Computation Matrix.
- **Transfinite Fabrication**: Transfinite Compute Nexus.

The anchor progression deliberately avoids a circular requirement: **900 entropy, achievable with Base anchors alone, unlocks Quantum anchors through Quantum Materials; 9,000 entropy, achievable with Quantum anchors alone, unlocks Terminal anchors through Quantum Sequencing.** Neither upgrade requires its own higher-tier anchor first.

## Obtaining

Harvest crystals from star marrow crystal clusters, then use an Inscriber to make crystal dust and Star Chart Processors. Make alloy ingots in the **AdvancedAE Reaction Chamber**. Craft engine cores, casing, observation ports and Base Entropy Anchors at a **crafting table**. Quantum and Terminal upgrades remain in the Fabrication Well and require **Tier 1: AE fabrication** research, **plus their respective permanent engine nodes** described above. This initial material chain needs neither a Flawless parent rock nor Star Signal Flow; the engine-node unlocks and optional overclocking do consume information.

### Casing and observation ports

Each recipe yields **4 blocks**.

<RecipeFor id="molecularmanipulator:star_computation_engine_casing" />

<RecipeFor id="molecularmanipulator:star_computation_observation_port" />

### Engine core

At a crafting table, combine 4 casing, 2 alloy ingots, 1 Star Chart Processor, 1 Star Marrow Crystal and 1 Energy Acceptor to make **1 core**.

<RecipeFor id="molecularmanipulator:star_computation_engine_core" />

### Anchor tiers

At a **crafting table**, combine 4 alloy ingots, 2 casing, 2 Star Marrow Crystals and 1 Star Chart Processor to make **9 Base Entropy Anchors**, enough for one cross-section. Quantum and Terminal upgrades remain in the **Fabrication Well**; each consumes 9 anchors of the previous tier and produces 9 upgraded anchors. In addition to Tier 1: AE fabrication research, the Quantum recipe requires **Quantum Materials (900 entropy, 2,048 bit)** and the Terminal recipe requires **Quantum Sequencing (9,000 entropy, 8,192 bit)**, with the prerequisites listed in the tree.

<RecipeFor id="molecularmanipulator:base_entropy_anchor" />

<RecipeFor id="molecularmanipulator:quantum_entropy_anchor" />

<RecipeFor id="molecularmanipulator:terminal_entropy_anchor" />
