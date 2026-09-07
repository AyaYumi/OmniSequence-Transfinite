# OmniSequence: Transfinite

English | [简体中文](README.zh-CN.md)

An end-game Applied Energistics 2 / ExtendedAE addon for Minecraft 1.21.1 on NeoForge. It provides massive-scale autocrafting, feedback-driven material dispatch, quantum-linked ME access, and large multiblock systems.

> To preserve compatibility with existing worlds, configurations, and modpack scripts, the technical namespace and Mod ID remain `molecularmanipulator`.

## Versions and Compatibility

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.220 or later in the 21.1 line |
| Applied Energistics 2 | 19.2.17 or later |
| AppliedEnhancements | 1.0.6 or later (required on both sides) |
| ExtendedAE | 1.21-2.2.32-neoforge or later |
| Glodium | 1.21-2.2-neoforge |
| LDLib2 | 2.2.18 or later |
| Optional integrations | Advanced AE, ExtendedAE Plus, JEI, AE2WTLib |

Current release: `2.0.0`

See [CHANGELOG.md](CHANGELOG.md) for the complete change list and upgrade
history.

Third-party pattern-holding machines can opt into atomic material batching
through the [Omni Batch Provider API v1](docs/omni-batch-provider-api.md).
The [API index](docs/README.md) also covers [well research and KubeJS](docs/matter-research-api.md)
and the separate AppliedEnhancements planning API.

> Known incompatibility: the only currently declared conflict is `Expanded AE 2.1.1`
> (`expandedae-2.1.1.jar`, not ExtendedAE). The conflicting code is Expanded AE's
> bundled AppliedFlux compatibility Mixin. AppliedFlux itself, ExtendedAE, and later
> NeoForge 21.1 patch releases are not marked as conflicts.

## Highlights

- Delegates shared AE2 ordering, pattern caching, material summaries, infinite cells and terminal enhancements to the required AppliedEnhancements mod and its configuration.
- Adds the Molecular Sequence Rewrite Array, Assembler Matrix Sequence Rewrite Core, Omni-Computation Core, and the Sequence Array multiblock managed by the Sequence Array Controller.
- Provides structure projection, automatic construction and dismantling, chunk-aware pause and resume, and dynamic visual effects.
- The Matter Fabrication Well uses a 41x41x27 Pearl Genesis Chamber with native 16x16 pearl-white, light-silver and champagne-gold textures; its controller and nine service bays retain their positions.
- All three multiblocks dismantle actual matching blocks from highest to lowest, with serpentine rows inside each layer. Air does not inflate progress, paused or reloaded work retains its queue, and the controller is kept.
- Retains the current structures and only the official 1.3.9 Sequence Array/Omni-Computation legacy layouts. Their controllers provide a projection warning and timed two-click update confirmation.
- Supports wired ME access and cross-dimensional entangled quantum links.
- Provides modpack-configurable matter deconstruction, sequence storage, and blueprint reproduction.
- Adds bilingual AE2 GuideME pages for the main machines, Matter Fabrication Well, research, ports and pattern assembly, with live well recipe displays and research unlock labels.

## Autocrafting and Material Dispatch

A formed Omni-Computation Core on an active AE grid invokes the public `AelisCraftingPlanner` API using AppliedEnhancements budgets. Without an available core, the native path remains. If the prerequisite already enables AELIS globally, it owns the calculation and Omni does not invoke the planner twice. The prerequisite handles cyclic planning, execution order and seed retention; this mod retains machine batching and virtual CPU management.

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

The Molecular Sequence Rewrite Array, Assembler Matrix Sequence Rewrite Core, and Sequence Array use a persistent execution model for reusable inputs. Same-key remainders, including items marked as unbreakable, can be reused across a whole batch. Finite-durability tools are batched only when each craft deterministically adds exactly one point of damage. A single tool input may reserve a pool of multiple tools, including different current damage states, and consume that pool inside one provider-owned batch instead of dispatching every craft separately. Unbreaking-enchanted and other probabilistic or context-dependent transitions fall back to AE2's original one-craft path. Key-changing remainders, such as a water bucket becoming an empty bucket, also stay on that native path. This reusable tool-pool path is exclusive to this mod's three molecular crafting machines and is not part of the public third-party batch-provider API.

Accepted reusable batches remain owned by the provider across saves, chunk unloads, and server restarts. Canceling the AE2 crafting job persistently stops the remaining executions and refunds the exact unconsumed materials together with the reusable item's current state; completed outputs remain valid and canceled work cannot resume after reload. Breaking a molecular crafting machine while it owns a batch or long-count output buffer drops one state-bearing recovery machine instead of materializing an unsafe number of item entities; placing that machine restores the pending state. Batch expansion uses AE2's native pattern-power calculation over the actual combined inputs, preserving the original crafting-energy semantics.

## Core Machines

### Molecular Sequence Rewrite Array

- Provides a fixed 360 pattern slots: 10 pages with 36 slots each.
- Supports virtual high parallelism and recipe processing in as little as one tick.
- Persists deterministic reusable-tool pools and returns their exact current states when an AE2 job is canceled.
- Returns intermediate results and container remainders to the ME Network through a persistent safety buffer.

### Assembler Matrix Sequence Rewrite Core

- Installs inside an ExtendedAE Assembler Matrix in place of ordinary crafting and speed cores.
- Runs real recipe assembly and remainder logic, preserving and batching deterministic tool durability across multiple stored tools.
- Persists reusable-input batches and provides exact cancellation refunds across unloads and restarts.
- Aggregates outputs by `AEKey` and returns them to the ME Network in batches.

### Omni-Computation Core

- Uses a floating 65×65×35 celestial crown with `Long.MAX_VALUE`-scale logical crafting storage and parallelism.
- Creates virtual CPU lanes for active requests while reserving idle capacity for new jobs.
- Preserves tasks, internal materials, and progress while the structure is damaged or its chunks are unavailable.
- Supports projection, automatic construction, automatic dismantling, and suppression of natural hostile-mob spawning around the structure.

### Sequence Array Controller (Sequence Array Multiblock)

- Forms a baseless Frost Feather Crown within a 61×61 footprint and a 29-block height: one horizontal ring, four layered crystal-feather fans, a central controller and a short four-prong amethyst pendant. Logical autocrafting parallelism remains up to `Long.MAX_VALUE`.
- Eight phase-glass window panels and crystal/rune nodes decorate the ring, with four low focusing seats along the inner ribs. The central controller and front ME casing retain clear access.
- Legacy support retains only the official 1.3.9 palace array. Updating recovers its blocks and builds the current structure, moving the controller three blocks down and fifteen blocks behind its old position while retaining its contents. Check the projection and prepare materials and recovery space before confirming.
- Pattern slots accept encoded AE2 crafting, smithing-table, and stonecutting patterns. Processing, blank, and invalid patterns are rejected.
- The Auto Crafting tab provides nine dedicated pattern slots independent of the large AE pattern library. Each slot can be enabled separately, configures an ME reserve for every logical input, and caps the primary output's ME stock. An output limit of `0` keeps crafting until ingredients run out.
- Passive crafts extract directly from the attached ME Network and return primary outputs, byproducts, containers, and reusable inputs exclusively to ME. Adjacent output and acceleration cards are not used.
- Supports deterministic reusable-input and multi-tool durability-pool batches with persistent cancellation and refund state.
- Shift-moving a supported pattern fills the current pattern page first, then continues into later pages.
- One-click dismantling uses a timed two-step confirmation. Rapid double-clicks, clicking another control, or waiting for the timeout will not trigger accidental removal.
- Provides independent RGB effects for the energy field, core, rings, and lattice. Crafting accelerates the animation only; visual settings do not change processing speed.
- Automatically force-loads required chunks while formed or during construction, dismantling and structure updates; structural damage pauses work while preserving progress.

Use the in-game projection and JEI structure information as the authoritative material list and orientation reference.

## In-Game Guide

AE2 GuideME pages are available for the following blocks. Hover the item in an inventory or JEI and press `G` to view its purpose, structure instructions, network requirements, supported patterns, and controls:

- Molecular Sequence Rewrite Array
- Assembler Matrix Sequence Rewrite Core
- Omni-Computation Core
- Sequence Array Controller
- Matter Fabrication Well and structural blocks
- Well item/fluid input and output ports
- Matter Fabrication Pattern Assembly

The well guide includes the 30-second research progression, per-branch deep-research bonuses and KubeJS duration behavior.
GuideME renders the well's processing recipes directly, including quantities, fluids, base time/power and research unlocks.

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

Eligible item tooltips default to a compact Shift-expand prompt. The client option `matter_sequence_tooltip_mode` supports `DISABLED`, `HOLD_SHIFT`, and `ALWAYS_VISIBLE`.

## Core Configuration

The server configuration is `omnisequence-transfinite-server.toml`; the client configuration is `omnisequence-transfinite-client.toml`. Legacy `molecularmanipulator-*.toml` files are copied forward automatically when the new file does not yet exist.

| Option | Default | Purpose |
| --- | ---: | --- |
| `pattern_pages` | 20 | Pattern pages available to the Sequence Array Controller; 36 slots per page |
| `build_blocks_per_tick` | 32 | Blocks placed or dismantled per tick |
| `idle_power` | 128 | Sequence Array Controller idle power in AE/t |
| `omni_batch_dispatch_enabled` | `true` | Enables batch material dispatch for compatible providers |
| `omni_compat_dispatch_max_calls_per_tick` | 2147483647 | Per-core, per-tick emergency ceiling for complete `1×` calls to ordinary providers |
| `omni_compat_dispatch_max_time_us` | 20000 | Server-wide budget shared by all active Omni-Computation Cores; contracts as average MSPT approaches 45 |
| `omni_dispatch_max_work_units` | 2147483647 | Maximum scheduler work units per core and tick |
| `matter_sequence_tooltip_mode` | `HOLD_SHIFT` | Client-only Matter Sequence tooltip mode |
| `dynamic_effect_level` | 2 | Client-only visual effects: 0 off, 1 reduced, 2 full |

Ordinary and unknown providers receive adaptive runtime-scaled patterns only for safe single-ingredient recipes. Multi-ingredient and other non-scalable paths send complete original recipes one at a time, preserving machine rotation and back-pressure behavior. A server-wide adaptive time slice replaces the old fixed 32-call limit: dispatch accelerates while the server has headroom and contracts as average MSPT approaches 45. Multiple cores, CPUs, and patterns do not each claim a separate full time budget. Only providers that explicitly declare atomic batch support receive complete multiplied multi-ingredient inputs.

The retired `omni_batch_allow_substitution_patterns` key is removed from existing server TOML files without resetting other custom values. When configuration loading or hot reload detects another option that the current version no longer defines, the mod keeps up to five `.toml.bak` files and atomically rebuilds the configuration with current defaults. If only a new option is missing or a known value is out of range, NeoForge repairs that value without resetting other valid settings.

## Installation and Build

Install the required dependencies above and place the built JAR in both the client and server `mods` directories. Before upgrading, fully stop the game, use the same version on both sides, and keep exactly one active `omnisequence-transfinite-*.jar` in each `mods` directory to avoid duplicate Mod IDs.

Source maintenance for 2.0.0 continues on `1.21.1-neoforge`.
For a source build, first place the separately built AppliedEnhancements 1.0.6 JAR
in `libs/`, following [the dependency setup](libs/README.md). Its binary is ignored
by Git and is not embedded in this mod. CI builds the prerequisite from its fixed
1.0.6 source commit before compiling OmniSequence.

```powershell
./gradlew.bat clean build --no-configuration-cache
```

Build artifact:

```text
build/libs/omnisequence-transfinite-2.0.0.jar
```

See [CHANGELOG.md](CHANGELOG.md) for version history, installation, and upgrade
notes. This project is licensed under the [MIT License](LICENSE).

`build` includes unit tests. Additional isolated-world upgrade, saved-state and
dismantling checks are documented in [the regression test guide](tools/gametest/README.md).
Runtime textures, shaders, GuideME pages and the two official 1.3.9 blueprints are
kept in `src/main/resources`; design drafts and generated screenshots are not source dependencies.

Shared feature settings belong to `appliedenhancements-common.toml`, including order limits, material summaries, infinite-cell handling and AELIS budgets. Omni does not override existing prerequisite settings or add terminal filtering/cut-and-paste integrations. Retired local planner, cache, precompilation and order-limit options are removed without resetting machine settings. Batch API v1, research APIs and legacy batch interfaces remain unchanged.
