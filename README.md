# OmniSequence: Transfinite

[简体中文](README.zh-CN.md) · [API documentation](docs/README.md)

An endgame expansion for AE2 and ExtendedAE, with material research, multiblock processing, large pattern libraries, crafting CPUs, resource collection and item duplication. Mod ID: `molecularmanipulator`.

## Version and dependencies

| Component | Current requirement |
| --- | --- |
| OmniSequence | 2.0.13-forge, branch `1.20.1-forge` |
| Minecraft / Java | 1.20.1 / Java 17 |
| Forge | 47.4.20 or later |
| AppliedEnhancements | 1.1.2-forge or later, client and server; build API 1.1.4-forge |
| AE2 | 15.4.10 |
| ExtendedAE | 1.20-1.4.20-forge or later |
| NeoECOAE | Optional; 20.4.2 or later; native batch/exact integration requires 20.5.0+ |
| ExtendedAE Plus | Optional; Forge integration targets 1.6.2 |
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

## Pattern management and effects

- **Native auto upload:** enable **Auto upload** in the Sequence Array overview to receive newly encoded crafting, stonecutting and smithing patterns from terminals on the same ME network. No ExtendedAE Plus or upload core is required. Processing patterns stay in the terminal; duplicates return a blank pattern. If no enabled, operational array has space, the encoded pattern stays in the terminal.
- **One-click migration:** **Migrate patterns** moves existing supported physical patterns from same-network public pattern slots and online mounted storage cells into the array's main library. It preserves NBT and duplicate physical items without re-encoding or spending blanks. Private slots, read-only recipe rows, storage-bus proxies and processing patterns are excluded. Full-library leftovers remain at the source; completed transfers are retained if interrupted. Large migrations run synchronously on click, not continuously in the background.
- **Well uploads:** JEI's **Encode and upload to fabrication well** uses one blank from the terminal, or its connected ME network if the local slot is empty. Destination selection uses a loaded, formed well and team ownership, not an assembly's display name. Forge EAEP 1.6.2 lacks the newer named-provider API; this does not promise its automatic selection of renamed assemblies.
- **Per-machine effects:** controller screens offer Off / Reduced / Full; the array's button is on its Colors tab. A saved choice affects that controller and all viewers, not production or other machines. Shift-click or Shift-Enter/Space restores each client's global effect setting.

New arrays default to auto upload off. On upgrade, a decodable core in the removed slot is returned through the controller's output buffer and preserves the old enabled behavior; an explicitly saved toggle takes priority. See the [configuration reference](CONFIGURATION.md) for limits.

## Research, collection and duplication

Ordinary research branches each have nine rounds by default. The first unlocks recipes; later rounds increase parallelism and speed for that branch's fabrication recipes. Normal ordering prepares the next round. Shift ordering prepares every remaining round and reaches maximum depth after one research run. Preparation can be canceled; ordered research cannot stop or pause once running.

The hub produces 1000 of each listed resource every 20 ticks by default. Its duplication page holds up to 64 miniature black holes, each generating 20 mB of Sequence Matter every 20 ticks. Default cost is 1000 FE or 256 AE per mB. Copying one item uses 1000 mB and retains the original sample. Matter generation needs ME fluid storage; collected and copied products need ME item storage. The collection page displays the server's effective resource list, per-item batch size and interval, including for clients connected to a dedicated server.

Holes bind to the placer's FTB team, or the individual without FTB Teams. Re-place them after changing teams to update ownership. White holes keep their chunks loaded; black holes need loaded chunks to operate.

## Configuration and development documentation

Global configuration files are `config/omnisequence-transfinite-server.toml` and `config/omnisequence-transfinite-client.toml`. They control power, pattern capacity, collection lists, production and chunk loading. See the [configuration reference](CONFIGURATION.md).

Modpack authors can define fabrication recipes and research through datapacks. Research, batch-provider and large-count processing interfaces for other mods are covered in the [developer documentation index](docs/README.md).

## Building from source

Use Java 17, place `appliedenhancements-1.1.4-forge.jar` in `libs/`, then run:

```sh
./gradlew build apiJavadoc
```

The runtime JAR is `build/libs/omnisequence-transfinite-2.0.13-forge.jar`. Public Java API documentation is generated under `build/docs/api/`. See [libs/README.md](libs/README.md) for local dependency setup.

## Runtime tests

Tests live in `src/gameTest` and are excluded from release JARs. Use a fresh `-PgameTestRunName=<name>` for each run; output goes to `build/<name>`. Default directories are `build/gametest-<suite>` for servers and `build/gametest-client-<suite>` for clients.

| Runner | Suites and declared cases |
| --- | --- |
| Server | `cpu` (1), `performance` (9), `fabrication` (12): CPU, recipe lookup/batching and autocrafting |
| Server | `patterns` (16 base): physical migration and native upload |
| Server | `singularity-quantum` (3), `singularity-menu` (2): quantum links and resource/menu synchronization |
| Server | `fabrication-upload` (4): network blanks, legacy builder migration and team protection |
| Server | `research`, `singularity`, `singularity-unload`, `adaptive` |
| Integrated client | `eco` (27, NeoECOAE 20.5.0 batch/exact integration), `eco-legacy` (2, 20.4.2 ordinary dispatch, ownership, cancellation and reload) |
| Integrated client | `essence`, `native-essence`, `machines`; `patterns` with the cluster flag |

```sh
./gradlew --no-daemon -PgameTestSuite=<suite> -PgameTestRunName=gametest-<suite>-r1 runGameTestServer
./gradlew --no-daemon -I src/gameTest/integrated-client.init.gradle -PgameTestSuite=<suite> -PgameTestRunName=gametest-client-<suite>-r1 runClient
```

The integrated runner creates a disposable world and writes `gametest-result.json`; required-test failures fail the task. NeoECOAE 20.5.0 requires client initialization because its dedicated-server startup references `ClientLevel`. `gameTestEcoVersion` overrides the test runtime without changing the compile-only API.

- `adaptive`: add `-PgameTestRecipeModsDir=/path/to/reference-pack/mods` containing ExtendedAE Plus 1.6.2.
- `essence` / `native-essence`: use the same directory flag with Cucumber, Mystical Agriculture, Mystical Agradditions and MATC; `native-essence` omits ECO. `machines` needs AdvancedAE and GeckoLib. Versions are in `build.gradle`.
- `patterns`: `-PgameTestPatternCluster` adds one case using NeoECOAE 20.5.0 and LDLib 1.0.52.a; use the integrated runner. `-PgameTestPatternStorageJar=/path/to/ae2omnicells-1.20.1-forge-1.1.6.jar` independently adds one cell case without the cluster dependencies. Both flags give 18 cases.

The separate **white-hole/lens GPU regression** checks 3 effects across 9 camera states (27 checks), opening only the title screen. It requires a graphics-capable client:

```sh
./gradlew --no-daemon -I src/gameTest/singularity-lens.init.gradle -PgameTestSuite=cpu -PgameTestRunName=gametest-lens-r1 runClient
```

Here `cpu` is replaced by the GPU fixture. The task requires passing `singularity-lens-result.json` and a matching current-run `LENS_BOBBING_CLIENT_PASS` log marker. Runtime regressions cover the selected behavior, not full-modpack TPS performance.

Licensed under the [MIT License](LICENSE).
