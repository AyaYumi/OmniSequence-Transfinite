---
navigation:
  parent: omnisequence-index.md
  title: "Negative Entropy Engine"
  icon: molecularmanipulator:negative_entropy_engine_core
  position: 1200
item_ids:
- molecularmanipulator:negative_entropy_engine_core
- molecularmanipulator:negative_entropy_engine_casing
- molecularmanipulator:negative_entropy_observation_port
- molecularmanipulator:base_entropy_anchor
- molecularmanipulator:quantum_entropy_anchor
- molecularmanipulator:terminal_entropy_anchor
---

# Negative Entropy Engine

The Negative Entropy Engine is a **3×3×3** multiblock built from a core, casing and observation ports. Once formed, the entire machine switches to a new continuous shell with a lens core, scanning light and animated negative entropy information flow. The formed panels cover the observation ports, so they are no longer transparent. The central core's facing determines the front lens direction. Dismantling the structure restores the individual blocks' unformed appearance.

<Row>
<BlockImage id="molecularmanipulator:negative_entropy_engine_core" scale="4" />

<BlockImage id="molecularmanipulator:negative_entropy_engine_casing" scale="4" />

<BlockImage id="molecularmanipulator:negative_entropy_observation_port" scale="4" />
</Row>

## Building

1. Place the **Negative Entropy Engine Core** in the center of a 3×3×3 volume.
2. Put a **Negative Entropy Observation Port** in each of the four side centers of the middle layer.
3. Fill the other 22 positions with **Negative Entropy Engine Casing**:
   - 9 casing blocks on the top layer
   - 4 corner casing blocks on the middle layer
   - 9 casing blocks on the bottom layer
4. The structure forms automatically. No right-click or power is required **for formation**; network benefits and research actions require a powered, active ME connection with an available channel.

| Part | Amount |
| --- | --- |
| Negative Entropy Engine Core | 1 |
| Negative Entropy Observation Port | 4 |
| Negative Entropy Engine Casing | 22 |

## ME connection and interface

Connect a real ME cable to the **formed casing or observation ports**; attached, formed anchors also expose the same core's ME connection. The whole engine is **one network device**, requiring **1 channel** and **16 AE/t idle power**, not one channel per shell block or anchor. These are real AE2 network connections, not a cosmetic connection indicator.

Right-click **any formed core, casing, observation port or valid attached anchor** to open that engine's live interface. It has exactly **two tabs**:

- **Overview**: structure and ME status, effective entropy and level, a **79×41 effective-anchor card**, the four network benefits, and the network's information balance and actual bit/t consumption. Use the overclock switch or **Recheck structure** here.
- **Recipe Tree**: select a node to see its entropy threshold, prerequisites, one-time information cost and actual recipe outputs. Hover the output icons or contents summary for details, then pay to unlock. Selection alone does not purchase anything; payment and the result are confirmed by the server.

The four-arm hologram is a **schematic**, not a per-direction block survey. There is no separate anchor tab, full cross-section display or structure-projection button. This UI simplification **does not relax the complete 3×3 anchor-column requirement** below.

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

Overclocking continuously extracts **Negentropy Information Flow from the same ME network's storage**. It is separate from the 16 AE/t idle power and from one-time research payments; the interface shows the real available balance in **bit** and the actual consumption in **bit/t**. A non-overclocked engine consumes no information for production benefits.

- The cost is `1 << L` bit/t: **2, 4, 8, 16, 32, 64, 128, 256, 512 bit/t** at L1 through L9 respectively. The selected best engine pays for the network benefit, not once per receiving machine.
- At **L1–L8**, overclocking doubles all four benefits: assembler parallelism, CPU parallelism, CPU crafting storage and Well speed. It does not double entropy or bypass research requirements.
- At **L9**, paid overclocking provides **unlimited assembler/CPU parallelism, unlimited CPU crafting storage and 1-tick Well processing**. Normal L9 remains finite as listed above.
- Enabling overclock requires enough information for at least one tick. Each tick must actually be paid; insufficient information switches overclock off. Formation or network failure also disables overclock. When an engine remains active, it falls back to its normal finite benefits; an offline or broken engine supplies none. Permanent research unlocks are not erased.

## Permanent team research

The **Recipe Tree** has five permanent team nodes. Unlocking requires a formed, active engine at the listed entropy threshold, completed prerequisites and a **one-time payment from ME information storage**. Entropy is only a threshold and is **not spent**. Overclocking changes neither thresholds nor unlocks.

Progress is saved for the engine's bound team (the placing player's identity when no team is available), shared by that team's engines and retained across engine dismantling, chunk unloads and server restarts. To use a gated Fabrication Well recipe, its ME network still needs an active engine exposing the team's unlock; the recipe's original matter-research requirements also remain in force.

| Node | Required entropy | One-time cost | Prerequisite |
| --- | ---: | ---: | --- |
| Entropy Foundation (`foundation`) | 90 | 512 bit | None |
| Quantum Materials (`material`) | 900 | 2,048 bit | Entropy Foundation |
| Quantum Sequencing (`quantum`) | 9,000 | 8,192 bit | Quantum Materials |
| Terminal Materials (`terminal`) | 18,000 | 16,384 bit | Entropy Foundation |
| Transfinite Fabrication (`transfinite`) | 90,000 | 65,536 bit | Quantum Sequencing **and** Terminal Materials |

These nodes gate the following recipes; the interface resolves their actual outputs from the server's current recipes:

- **Entropy Foundation**: Sequence Auto Crafter; Sequence Array Casing, Quantum Crystal, Phase Glass and Stabilizer.
- **Quantum Materials**: **Quantum Entropy Anchor**, Molecular Sequence Rewrite Array, Sequence Rewrite Core, Sequence Array Controller and Assembler Matrix Sequence Rewrite Core.
- **Quantum Sequencing**: **Terminal Entropy Anchor**, Omni-Computation Core, Computation Core Frame, Computation Structure Glass, Computation Crystal Pylon, Data Entangler Node, Computation Energy Stabilizer and Sequence Output Node.
- **Terminal Materials**: Infinite Crafting Storage Matrix, Infinite Parallel Matrix and Omniscient Computation Matrix.
- **Transfinite Fabrication**: Transfinite Compute Nexus.

The anchor progression deliberately avoids a circular requirement: **900 entropy, achievable with Base anchors alone, unlocks Quantum anchors through Quantum Materials; 9,000 entropy, achievable with Quantum anchors alone, unlocks Terminal anchors through Quantum Sequencing.** Neither upgrade requires its own higher-tier anchor first.

## Obtaining

Harvest crystals from negative entropy quartz clusters, then use an Inscriber to make crystal dust and Negentropy Information Processors. Make alloy ingots in the **AdvancedAE Reaction Chamber**. Craft engine cores, casing, observation ports and Base Entropy Anchors at a **crafting table**. Quantum and Terminal upgrades remain in the Fabrication Well and require **Tier 1: AE fabrication** research, **plus their respective permanent engine nodes** described above. This initial material chain needs neither a Flawless parent rock nor Negentropy Information Flow; the engine-node unlocks and optional overclocking do consume information.

### Casing and observation ports

Each recipe yields **4 blocks**.

<RecipeFor id="molecularmanipulator:negative_entropy_engine_casing" />

<RecipeFor id="molecularmanipulator:negative_entropy_observation_port" />

### Engine core

At a crafting table, combine 4 casing, 2 alloy ingots, 1 Negentropy Information Processor, 1 Negentropy Crystal and 1 Energy Acceptor to make **1 core**.

<RecipeFor id="molecularmanipulator:negative_entropy_engine_core" />

### Anchor tiers

At a **crafting table**, combine 4 alloy ingots, 2 casing, 2 Negentropy Crystals and 1 Negentropy Information Processor to make **9 Base Entropy Anchors**, enough for one cross-section. Quantum and Terminal upgrades remain in the **Fabrication Well**; each consumes 9 anchors of the previous tier and produces 9 upgraded anchors. In addition to Tier 1: AE fabrication research, the Quantum recipe requires **Quantum Materials (900 entropy, 2,048 bit)** and the Terminal recipe requires **Quantum Sequencing (9,000 entropy, 8,192 bit)**, with the prerequisites listed in the tree.

<RecipeFor id="molecularmanipulator:base_entropy_anchor" />

<RecipeFor id="molecularmanipulator:quantum_entropy_anchor" />

<RecipeFor id="molecularmanipulator:terminal_entropy_anchor" />
