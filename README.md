# OmniSequence: Transfinite

[简体中文](README.zh-CN.md) · [API documentation](docs/README.md) · [Changelog](CHANGELOG.md)

An endgame expansion for AE2 and ExtendedAE, with material research, multiblock processing, large pattern libraries, crafting CPUs, resource collection and item duplication. Mod ID: `molecularmanipulator`.

## Version and dependencies

| Component | Current requirement |
| --- | --- |
| OmniSequence | 2.0.8-forge, branch `1.20.1-forge` |
| Minecraft / Java | 1.20.1 / Java 17 |
| Forge | 47.4.20 or later |
| AppliedEnhancements | 1.1.0-forge or later, client and server |
| AE2 | 15.4.10 or later; tested with 15.4.10 / UELM 15.5.4 |
| ExtendedAE | 1.20-1.4.19-forge or later |
| JEI / AdvancedAE | Optional; AdvancedAE enables its research/integration |

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
| Omni Computation Core | Manage multiple ME crafting requests concurrently |
| Transfinite Compute Nexus | Single-block crafting CPU requiring one channel and sufficient ME power |
| Event Horizon Singularity Hub | Collect resources simultaneously and copy samples using Sequence Matter |
| Compact Singularity Hub | Hub functions in one block; available from creative inventory or commands |
| Miniature Black Hole / White Hole | Transfer dropped items and non-player entities to the nearest same-team exit |
| Gravity Crystal | Walk on walls and ceilings or build repulsion lifts |
| Ghost Matter | Water-suppressed hazard and retained Gravity Crystal catalyst |

## Research, collection and duplication

Ordinary research branches each have nine rounds by default. The first unlocks recipes; later rounds increase parallelism and speed for that branch's fabrication recipes. Normal ordering prepares the next round. Shift ordering prepares every remaining round and reaches maximum depth after one research run. Preparation can be canceled; ordered research cannot stop or pause once running.

The hub produces 1000 of each listed resource every 20 ticks by default. Its duplication page holds up to 64 miniature black holes, each generating 20 mB of Sequence Matter every 20 ticks. Default cost is 1000 FE or 256 AE per mB. Copying one item uses 1000 mB and retains the original sample. Matter generation needs ME fluid storage; collected and copied products need ME item storage.

Holes bind to the placer's FTB team, or the individual without FTB Teams. Re-place them after changing teams to update ownership. White holes keep their chunks loaded; black holes need loaded chunks to operate.

## Configuration and development documentation

Global configuration files are `config/omnisequence-transfinite-server.toml` and `config/omnisequence-transfinite-client.toml`. They control power, pattern capacity, collection lists, production and chunk loading. See the [configuration reference](CONFIGURATION.md).

Modpack authors can define fabrication recipes and research through datapacks. Research, batch-provider and large-count processing interfaces for other mods are covered in the [developer documentation index](docs/README.md).

## Building from source

Use Java 17, place `appliedenhancements-1.1.0-forge.jar` in `libs/`, then run:

```sh
./gradlew build apiJavadoc
```

The runtime JAR is `build/libs/omnisequence-transfinite-2.0.8-forge.jar`. Public Java API documentation is generated under `build/docs/api/`. See [libs/README.md](libs/README.md) for local dependency setup.

Licensed under the [MIT License](LICENSE).
