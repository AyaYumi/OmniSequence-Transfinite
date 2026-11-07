# OmniSequence: Transfinite

[简体中文](README.zh-CN.md) · [API documentation](docs/README.md)

An endgame expansion for AE2 and ExtendedAE, with material research, multiblock processing, large pattern libraries, crafting CPUs, resource collection and item duplication. Mod ID: `molecularmanipulator`.

## Version and dependencies

| Component | Current requirement |
| --- | --- |
| OmniSequence | 2.1.0, branch `1.21.1-neoforge` |
| Minecraft / Java | 1.21.1 / Java 21 |
| NeoForge | 21.1.220 or later |
| AppliedEnhancements | 1.1.2 or later, client and server |
| AE2 | 19.2.18 |
| ExtendedAE | 1.21-2.2.32-neoforge or later |
| LDLib2 | 2.2.18 or later |
| JEI / AdvancedAE | Optional; AdvancedAE enables its research/integration |
| Neo ECO AE Extension | Optional; 21.2.10 or later when installed, matching on both sides |
| ExtendedAE Plus | Optional; additional provider-selection/upload hooks target 1.6.4.1 only |
| Data Energistics | Optional; depot capacity-simulation fix targets 3.3.0 and 3.3.5 only |

Install the mod and required dependencies separately in client and server `mods/` folders. Use matching builds on both sides. ExpandedAE 2.1.1 is incompatible with this mod.

## Getting started

1. Build a **Matter Fabrication Well** with basic AE2 materials, connect ME and complete tier 1 research.
2. Install a **Pattern Assembly** to automate fabrication recipes through ME.
3. Research **Tier 2: Sequence Array** and **Tier 2: Omni Computation** to fabricate pattern storage, crafting devices and CPUs.
4. Complete both tier 2 branches once, then research **Tier 3: Event Horizon** and build a hub for collection and duplication.

**Special: Exotic Matter Studies** opens after tier 1. This optional, single-round research unlocks Gravity Crystals and Ghost Matter and does not require tier 3.

Use the AE2 guide hotkey (default **G**) on an item, JEI entry or associated block to open the bilingual in-game guide. Check your pack's JEI and machine interfaces for recipes, research requirements and structure materials.

## Main devices

| Device | Use |
| --- | --- |
| Matter Fabrication Well | Material processing, research and ME autocrafting |
| Molecular Sequence Rewrite Array | 360 pattern slots; internal batch crafting, smithing and stonecutting |
| Assembler Matrix Sequence Rewrite Core | Batch recipe execution inside ExtendedAE's Assembler Matrix |
| Sequence Array | Large pattern library and nine independent restocking positions |
| Molecular Auto Crafter | Maintain stock using product targets and ingredient reserves |
| Omni Computation Core | ME crafting CPU; high-tier star benefits enable concurrent requests |
| Transfinite Compute Nexus | Single-block crafting CPU requiring one channel and sufficient ME power |
| Event Horizon Singularity Hub | Collect resources simultaneously and copy samples using Sequence Matter |
| Compact Singularity Hub | Hub functions in one block; available from creative inventory or commands |
| Miniature Black Hole / White Hole | Transfer dropped items and non-player entities to the nearest same-team exit |
| Gravity Crystal | Walk on walls and ceilings or build repulsion lifts |
| Ghost Matter | Water-suppressed hazard and retained Gravity Crystal catalyst |
| Green Fire Lantern | Portable light; its personal simulation shows flawed parent rock as flawless and yields permanent flawless blocks |

The Sequence Array's **Auto upload** switch receives newly encoded crafting, stonecutting and smithing patterns from native AE2 terminals on the same network. **Migrate patterns** moves existing patterns from public pattern slots and online storage cells into its library. Migration runs only on click, preserves physical items and their components, and leaves full-library leftovers at their source. Processing patterns stay at their source. New Arrays default to auto upload off.

Fabrication Well recipe uploads use one blank pattern from the terminal, or its connected ME network when the local slot is empty. Destination lookup uses the formed well and team rather than an assembly's custom name; the additional EAEP 1.6.4.1 upload hooks follow the same identity rules.

## Folded buildings and relocation

Connect a **Spatial Folding Assembler** to ME to order seven complete building packages: a Matter Fabrication Well, Sequence Array, Omni Computation Core, **Event Horizon Singularity Hub**, or **Star Orrery L1/L2/L3**. Each order consumes its controller and every standard construction material, retaining existing recipes and research requirements.

The Star Computation Engine uses only the **Nine-Ring Star Orrery**. L0 requires both the complete **central 3×3×3 assembly**—1 core, 22 casing and 4 observation ports—and **928 physical foundation blocks** (520 engraved and 408 gilded). Place a port at the center of each horizontal-facing side. Any missing or incorrect central or foundation block prevents formation. Consecutive complete rings from ring 1 raise the level through L9; joining or stacking structures grants no extra benefits.

L1/L2/L3 packages contain the first 1/2/3 rings, with 24/56/96 Reference Star Position Anchors, **all 27 central blocks and the full foundation in every package**: **979/1011/1051 blocks** total. They are complete buildings, not upgrade differences or the Reference/Quantum/Terminal tiers. Creative inventory provides all three, and JEI's **Star Orrery Structure** category shows their materials, package outputs and structure previews.

Once formed, the 22 casing and 4 ports hide their individual meshes while the core renders one **3×3×3 model**. Their block entities, collision and drops remain. Valid central parts open the real core's menu and proxy its ME connection; they do not become independent machines. Existing foundation hiding and effects remain unchanged.

The Singularity package uses the current suspended design, including its controller and all construction materials. Reserve **97×97 blocks and 129 blocks of height**, with controller Y = base Y + 65; required empty light-well cells also participate in site checks. JEI shows its full bill and package output, and the package is available in creative inventory.

Right-click a site with the **Star Chart Calibrator** to select a package, base coordinates and horizontal facing; the orrery core is 37 blocks above the base. The projection checks the real occupied positions, bounds and conflicts. Confirm to unfold in chart, foundation, facade and core order. Esc only hides the panel; right-click reopens the pinned site. Only **Cancel point** allows a new site. There is no persistent HUD or return hotkey. Use **Interrupt and reclaim building** in the panel to withdraw unfinished construction, not sneak-right-click.

Existing whole-machine sealing for wells, arrays, multiblock computation cores and formed orreries remains available through their machine interfaces, including the orrery's 27 central blocks. Finish or cancel and settle external ME CPU jobs first. Archives restore their original contents without free missing parts, refunds or conversion into larger level packages. Restored old cubes still need the complete central assembly and real foundation; loose casing and ports offer no independent menu or ME connection, and old four-direction anchor columns remain unsupported. Player blocks are not deleted automatically.

Full contents live in the world's `data/molecularmanipulator/buildings/`; the item stores an archive reference. Back up the whole world. Copying an item to another save does not copy its archive. The **Building Access Terminal** binds up to 64 buildings and opens their existing controls in the same dimension when target chunks are already loaded; it does not force-load them. See the in-game folded-building and terminal guides for full instructions.

## Research, collection and duplication

Ordinary research branches each have nine rounds by default. The first unlocks recipes; later rounds increase parallelism for that branch's fabrication recipes. Fabrication defaults to 20 seconds (400 ticks); production speed comes from the Star Computation Engine on the well's ME network, with level 9 overclocking forcing one tick. Research rounds retain their own duration. Normal ordering prepares the next round. Shift ordering prepares every remaining round and reaches maximum depth after one research run. Preparation can be canceled; ordered research cannot stop or pause once running.

The hub produces 1000 of each listed resource every 20 ticks by default. Its duplication page holds up to 64 miniature black holes, each generating 20 mB of Sequence Matter every 20 ticks. Default cost is 1000 FE or 256 AE per mB. Copying one item uses 1000 mB and retains the original sample. Matter generation needs ME fluid storage; collected and copied products need ME item storage.

Holes bind to the placer's FTB team, or the individual without FTB Teams. Re-place them after changing teams to update ownership. White holes keep their chunks loaded; black holes need loaded chunks to operate.

## Configuration and development documentation

Global configuration files are `config/omnisequence-transfinite-server.toml` and `config/omnisequence-transfinite-client.toml`. They control power, pattern capacity, collection lists, production and chunk loading. Each multiblock controller also has an **Effects** button (on the Array's Colors page): 0 off, 1 reduced, 2 full. The choice is saved per machine and shared with viewers; Shift-click restores each client's global setting. Resource lists and rates come from the server, and Singularity quantum links recover after normal endpoint initialization. Configuration migration preserves valid custom settings and backs up changes; previously reset values need a backup. See the [configuration reference](CONFIGURATION.md).

Modpack authors can define fabrication recipes and research through datapacks. Research, batch-provider and large-count processing interfaces for other mods are covered in the [developer documentation index](docs/README.md).

ECO integration includes batch dispatch in both directions, reusable tools and containers, native FX exact batches, and ECO exact/phased/cyclic plans on Omni CPUs. ECO 21.2.10 large integrated workstations and the crafting subsystem retain job UUIDs for single copies, batches, final copies and blocking fallback. Directed byproducts and surplus remain in CPU storage without falsely advancing expected-output progress.

Ordinary providers use adaptive complete-recipe dispatch, increasing same-tick calls until the machine is busy and probing again after 20 ticks. Returned tools and deterministic wear states can be reused, final single crafts do not wait for a batch, and blocked cycle phases yield to other runnable work. CPU storage and parallelism remain finite unless the current Star Computation Engine benefits explicitly provide otherwise; materials, power and machine capacity still apply. The Data Energistics 3.3.0/3.3.5 depot fix simulates real slot capacity without double-counting free space or enlarging storage.

## Building from source

Use Java 21, place `appliedenhancements-1.1.4.jar` in `libs/`, then run:

```sh
./gradlew build apiJavadoc
```

The runtime JAR is `build/libs/omnisequence-transfinite-2.1.0.jar`. Public Java API documentation is generated under `build/docs/api/`. See [libs/README.md](libs/README.md) for local dependency setup.

Code is licensed under the [MIT License](LICENSE). Bundled fonts retain their SIL Open Font Licenses, and the adapted AE2 texture retains CC BY-NC-SA 3.0; see [Third-Party Notices](THIRD_PARTY_NOTICES.md).
