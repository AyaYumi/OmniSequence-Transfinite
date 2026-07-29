# OmniSequence: Transfinite

English | [简体中文](README.zh-CN.md)

An end-game Applied Energistics 2 / ExtendedAE addon for Minecraft 1.20.1 on Forge. It provides massive-scale autocrafting, feedback-driven material dispatch, quantum-linked ME access, and large multiblock systems.

> To preserve compatibility with existing worlds, configurations, and modpack scripts, the technical namespace and Mod ID remain `molecularmanipulator`.

## Versions and Compatibility

| Component | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 or later |
| Applied Energistics 2 | 15.4.10 |
| ExtendedAE | 1.20-1.4.12-forge |
| Glodium | 1.20-1.5-forge |
| Optional integrations | Advanced AE, ExtendedAE Plus, JEI, AE2WTLib |

Current release: `1.3.6-forge`

See the [bilingual 1.3.6-forge release notes](RELEASE_NOTES_1.3.6-forge.md) for the complete change and upgrade details.

> The Forge 1.20.1 build does not register the standalone Molecular Sequence
> Rewrite Array block. Existing worlds remove previously placed copies as missing
> blocks; its pattern and matter-rewriting functions remain available through the
> Sequence Array multiblock.

## Highlights

- Extends AE2's maximum amount for a single autocrafting order into a configurable `long` range.
- Supports AE2 Creative Storage Cells and ExtendedAE Infinite Storage Cells, displaying unlimited amounts as `∞`.
- Prevents integer-overflow crashes in wireless-terminal auto-stock overlays with extremely large or unlimited inventories.
- Adds the Assembler Matrix Sequence Rewrite Core, Omni-Computation Core, and the Sequence Array multiblock managed by the Sequence Array Controller.
- Provides structure projection, automatic construction and dismantling, chunk-aware pause and resume, and dynamic visual effects.
- Supports wired ME access and cross-dimensional entangled quantum links.
- Provides modpack-configurable matter deconstruction, sequence storage, and blueprint reproduction.
- Adds AE2 GuideME pages for all three registered primary blocks; hover an item and press `G` to open its guide.

## Autocrafting and Material Dispatch

The Omni-Computation Core uses `SAFE` aggregation to accelerate deterministic recipe trees. Item-substitution patterns conservatively fall back, while fluid-only substitution remains deterministic and may stay on the fast path. Container remainders, dynamic inputs, cycles, and unknown pattern behavior also fall back to AE2's native calculation path.

Material dispatch uses three execution modes:

- Targets that explicitly implement atomic batch handling retain direct `long`-sized logical batches. Their throughput is limited only by available task inputs, energy, and the target's real acceptance capacity.
- Ordinary AE crafting providers use runtime-scaled patterns only for single-ingredient recipes. Their multiplier probes across server ticks as `1 → 2 → 4 → 8 → …`, growing by at most one level per provider-and-pattern pair per tick. Successful batches keep the doubled multiplier for the next tick; rejection retains the last successful multiplier as the next baseline.
- Multi-ingredient and other non-scalable paths bypass scaled wrappers entirely. AE2 repeatedly sends the original complete `1×` recipe, preventing one-to-many routing from splitting different ingredient waves across different machines.

Provider compatibility and scheduling preserve the surrounding mods' behavior:

- Native AE2 and ExtendedAE providers use precise feedback from complete insertion and internal queues. AE2LT, Advanced AE, and other third-party providers use the public AE acceptance and busy-state contracts. Busy providers wait; rejecting providers reduce their multiplier.
- Single-ingredient scaled patterns retain ExtendedAE Plus wrapper identity, Advanced AE directional-input data, and AE2LT overloaded-provider metadata. Multi-ingredient jobs first unwrap EAP planning-time scaling and then dispatch complete recipes one at a time.
- If a target accepts `1×` but rejects `2×`, that provider-and-pattern pair remains on complete single-recipe dispatch for the rest of the order. AE2 re-extracts and accounts for every successful recipe independently.

All active Omni-Computation Cores share one server-wide compatibility deadline. After it is reached, one rotating lane receives a guaranteed progress attempt per tick. Jobs within the same CPU also rotate their starting point and receive short per-pattern slices, preventing the first massive task from starving later work. Explicitly scalable or atomic `long` batch tasks can continue after the compatibility limit is reached.

Only providers that explicitly implement this project's atomic batch protocol receive a complete `N×` input for multi-ingredient patterns. `Integer.MAX_VALUE` remains AE2's logical per-tick parallel window, not an unconditional real loop count. Ordinary `1×` calls continue over later ticks under a server-load-adaptive time slice, while an explicit final atomic batch is not accidentally blocked by that compatibility budget.

Provider-owned remainder queues that already belong to an in-flight CPU task retain that ownership, preventing reinjection from leaving the CPU waiting forever for outputs that can no longer be produced. AE2's crafting-in-progress accounting uses the actual expected output, and every dispatch is capped by the remaining `waitingFor` output headroom to prevent pending output from overflowing `Long.MAX_VALUE`. The scheduler distributes work fairly across virtual CPUs and patterns, with a work-unit ceiling for extreme jobs.

## Core Machines

### Assembler Matrix Sequence Rewrite Core

- Installs inside an ExtendedAE Assembler Matrix in place of ordinary crafting and speed cores.
- Runs real recipe assembly and remainder logic, preserving tool durability and non-consumed inputs.
- Aggregates outputs by `AEKey` and returns them to the ME Network in batches.

### Omni-Computation Core

- Uses a fixed 31×31×39 structure with `Long.MAX_VALUE`-scale logical crafting storage and parallelism.
- Creates virtual CPU lanes for active requests while reserving idle capacity for new jobs.
- Preserves tasks, internal materials, and progress while the structure is damaged or its chunks are unavailable.
- Supports projection, automatic construction, automatic dismantling, and suppression of natural hostile-mob spawning around the structure.

### Sequence Array Controller (Sequence Array Multiblock)

- Forms a fixed 31×46×31 structure with logical autocrafting parallelism up to `Long.MAX_VALUE`.
- Pattern slots accept encoded AE2 crafting, smithing-table, and stonecutting patterns. Processing, blank, and invalid patterns are rejected.
- Shift-moving a supported pattern fills the current pattern page first, then continues into later pages.
- One-click dismantling uses a timed two-step confirmation. Rapid double-clicks, clicking another control, or waiting for the timeout will not trigger accidental removal.
- Provides independent RGB effects for the energy field, core, rings, and lattice. Crafting accelerates the animation only; visual settings do not change processing speed.
- Does not force-load chunks. Work pauses while any structure chunk is unavailable and resumes after validation.

Use the in-game projection and JEI structure information as the authoritative material list and orientation reference.

## In-Game Guide

AE2 GuideME pages are available for the following blocks. Hover the item in an inventory or JEI and press `G` to view its purpose, structure instructions, network requirements, supported patterns, and controls:

- Assembler Matrix Sequence Rewrite Core
- Omni-Computation Core
- Sequence Array Controller

## Entangled Quantum Link

Large controllers include a quantum endpoint. Place one half of a paired Entangled Singularity in the controller and the other in a powered remote AE2 Quantum Ring to access that ME Network across dimensions.

- An unformed controller can use the remote network to retrieve automatic-construction materials.
- Once formed, patterns, crafting jobs, storage access, energy, and dismantled blocks can all travel through the remote network.
- The link consumes an additional 512 AE/t and one AE channel.
- It disconnects safely when the remote side unloads, loses power, or has a frequency conflict, then reconnects automatically when conditions recover.
- A wired connection and a conflicting remote network cannot operate at the same time.

## Matter Sequence Rewriting

The Sequence Array Controller contains a deconstruction-marker slot, blueprint-sample slot, and rewritten-output slot. It independently stores metal, mineral, crystal, and organic matter sequences.

Rules are loaded from:

```text
config/molecularmanipulator/matter_rewrite_rules.json
```

Exact item rules take priority over tag rules. Items with custom data such as enchantments, custom names, durability, or container contents are not deconstructed or reproduced. With 0–4 AE2 Acceleration Cards installed, the processing time per item is 20, 10, 5, 2, or 1 tick respectively.

## Core Configuration

The server configuration is `omnisequence-transfinite-server.toml`; the client configuration is `omnisequence-transfinite-client.toml`. Legacy `molecularmanipulator-*.toml` files are copied forward automatically when the new file does not yet exist.

| Option | Default | Purpose |
| --- | ---: | --- |
| `pattern_pages` | 20 | Pattern pages available to the Sequence Array Controller; 36 slots per page |
| `build_blocks_per_tick` | 32 | Blocks placed or dismantled per tick |
| `idle_power` | 128 | Sequence Array Controller idle power in AE/t |
| `max_crafting_order_amount` | 1,000,000,000,000 | Maximum amount in one AE2 autocrafting order |
| `omni_max_fast_mode` | `SAFE` | Omni-Computation Core recipe-tree aggregation mode |
| `omni_max_fast_max_nodes` | 8192 | Maximum unique recipe nodes compiled in one aggregation |
| `omni_max_fast_compile_budget_ms` | 100 | Aggregation compile budget before falling back to AE2 |
| `omni_max_fast_diagnostics` | `false` | Logs aggregation timing and fallback reasons |
| `omni_batch_dispatch_enabled` | `true` | Enables batch material dispatch for compatible providers |
| `omni_batch_allow_substitution_patterns` | `false` | Allows item-substitution patterns to use batch dispatch |
| `omni_compat_dispatch_max_calls_per_tick` | 2147483647 | Per-core, per-tick emergency ceiling for complete `1×` calls to ordinary providers |
| `omni_compat_dispatch_max_time_us` | 20000 | Server-wide budget shared by all active Omni-Computation Cores; contracts as average MSPT approaches 45 |
| `omni_dispatch_max_work_units` | 2147483647 | Maximum scheduler work units per core and tick |
| `dynamic_effect_level` | 2 | Client-only visual effects: 0 off, 1 reduced, 2 full |

Ordinary and unknown providers receive adaptive runtime-scaled patterns only for safe single-ingredient recipes. Multi-ingredient and other non-scalable paths send complete original recipes one at a time, preserving machine rotation and back-pressure behavior. A server-wide adaptive time slice replaces the old fixed 32-call limit: dispatch accelerates while the server has headroom and contracts as average MSPT approaches 45. Multiple cores, CPUs, and patterns do not each claim a separate full time budget. Only providers that explicitly declare atomic batch support receive complete multiplied multi-ingredient inputs.

When configuration loading or hot reload detects an option that the current version no longer defines, the mod keeps up to five `.toml.bak` files and atomically rebuilds the configuration with current defaults. If only a new option is missing or a known value is out of range, Forge repairs that value without resetting other valid settings.

## Installation and Build

Install the required dependencies above and place the built JAR in both the client and server `mods` directories. Before upgrading, fully stop the game, use the same version on both sides, and keep exactly one active `omnisequence-transfinite-*.jar` in each `mods` directory to avoid duplicate Mod IDs.

```powershell
./gradlew.bat clean build --no-configuration-cache
```

Build artifact:

```text
build/libs/omnisequence-transfinite-1.3.6-forge.jar
```

See [CHANGELOG.md](CHANGELOG.md) for version history and [RELEASE_NOTES_1.3.6-forge.md](RELEASE_NOTES_1.3.6-forge.md) for installation and upgrade notes. This project is licensed under the [MIT License](LICENSE).
