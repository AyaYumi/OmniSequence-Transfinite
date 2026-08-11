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
| ExtendedAE | 1.21-2.2.32-neoforge or later |
| Glodium | 1.21-2.2-neoforge |
| LDLib2 | 2.2.18 or later |
| Optional integrations | Advanced AE, ExtendedAE Plus, JEI, AE2WTLib |

Current release: `1.3.9-hotfix`

See the [1.3.9-hotfix changelog](CHANGELOG.md#139-hotfix---2026-08-11) for the
complete change list.

Third-party pattern-holding machines can opt into atomic material batching
through the [Omni Batch Provider API v1](docs/omni-batch-provider-api.md).

> Known incompatibility: the only currently declared conflict is `Expanded AE 2.1.1`
> (`expandedae-2.1.1.jar`, not ExtendedAE). The conflicting code is Expanded AE's
> bundled AppliedFlux compatibility Mixin. AppliedFlux itself, ExtendedAE, and later
> NeoForge 21.1 patch releases are not marked as conflicts.

## Highlights

- Extends AE2's maximum amount for a single autocrafting order into a configurable `long` range.
- Detects infinite storage cells at the AE2 network boundary without hard-coding a specific provider, and keeps their `Long.MAX_VALUE` amounts visible regardless of mount order.
- Prevents integer-overflow crashes in wireless-terminal auto-stock overlays with extremely large or unlimited inventories.
- Splits large pattern inventories into multiple logical Pattern Access Terminal containers for both single-block molecular machines and the multiblock Sequence Array.
- Adds the Molecular Sequence Rewrite Array, Assembler Matrix Sequence Rewrite Core, Omni-Computation Core, and the Sequence Array multiblock managed by the Sequence Array Controller.
- Provides structure projection, automatic construction and dismantling, chunk-aware pause and resume, and dynamic visual effects.
- Keeps both fixed multiblocks compatible with their legacy and current layouts; controllers offer an optional, player-confirmed update for complete legacy structures.
- Supports wired ME access and cross-dimensional entangled quantum links.
- Provides modpack-configurable matter deconstruction, `Long.MAX_VALUE` sequence storage, entropy cooling, speed-card tiers, and blueprint reproduction.
- Adds AE2 GuideME pages for all four primary blocks; hover an item and press `G` to open its guide.

## Autocrafting and Material Dispatch

The Omni-Computation Core uses `SAFE` aggregation to compile deterministic AE2
recipe graphs and merge repeated subtree demand. Multi-candidate plans preserve
AE2 priority order transactionally, while stable same-key catalysts and
deterministic `+1` durability tools remain batchable even when nested below the
requested product. Exact terminal shortages fail the real attempt immediately
and are aggregated into the simulated missing-material plan in one pass instead
of re-entering AE2's per-craft traversal. Whenever a speculative fast-path
attempt is rejected, fails, or is interrupted, its staged missing-item entries
and candidate-availability changes are rolled back before AE2 retries or the
calculation exits.

AdvancedAE 1.6.11 processing patterns can use the same verified graph path.
Only the exact `AdvProcessingPattern` implementation is admitted, and it still
has to pass every deterministic input, output, remainder, overflow, and runtime
template check; unknown implementations continue through native AE2. If a real
multi-candidate attempt needs AE2 to try a later recipe, the final simulated
missing-material pass can still aggregate the verified first candidate instead
of repeating AE2's per-craft traversal.

Item-substitution patterns still conservatively fall back from this planner,
except when AE2 has already selected a substitute whose deterministic `+1`
durability transition passes the complete reusable-boundary verification.
Fluid-only substitution also remains deterministic and may stay on the fast
path. This planning fallback does not block compatible runtime batch dispatch:
item-substitution patterns remain eligible there, and AE2 still chooses the
actual substituted input. Random or context-dependent remainders, key-changing
containers, cycles, and unknown pattern behavior retain AE2's native calculation
path.

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
- Exposes its pattern inventory as logical containers in the Pattern Access Terminal instead of one oversized entry.
- Supports virtual high parallelism and recipe processing in as little as one tick.
- Persists deterministic reusable-tool pools and returns their exact current states when an AE2 job is canceled.
- Returns intermediate results and container remainders to the ME Network through a persistent safety buffer.

### Assembler Matrix Sequence Rewrite Core

- Installs inside an ExtendedAE Assembler Matrix in place of ordinary crafting and speed cores.
- Runs real recipe assembly and remainder logic, preserving and batching deterministic tool durability across multiple stored tools.
- Persists reusable-input batches and provides exact cancellation refunds across unloads and restarts.
- Aggregates outputs by `AEKey` and returns them to the ME Network in batches.

### Omni-Computation Core

- Uses a fixed 31×31×39 structure with `Long.MAX_VALUE`-scale logical crafting storage and parallelism.
- Creates virtual CPU lanes for active requests while reserving idle capacity for new jobs.
- Preserves tasks, internal materials, and progress while the structure is damaged or its chunks are unavailable.
- Supports projection, automatic construction, automatic dismantling, and suppression of natural hostile-mob spawning around the structure.

### Sequence Array Controller (Sequence Array Multiblock)

- Forms a fixed 31×46×31 structure with logical autocrafting parallelism up to `Long.MAX_VALUE`.
- Pattern slots accept encoded AE2 crafting, smithing-table, and stonecutting patterns. Processing, blank, and invalid patterns are rejected.
- Supports deterministic reusable-input and multi-tool durability-pool batches with persistent cancellation and refund state.
- Shift-moving a supported pattern fills the current pattern page first, then continues into later pages.
- Exposes configured pattern pages as multiple logical Pattern Access Terminal containers while preserving one physical controller inventory.
- One-click dismantling uses a timed two-step confirmation. Rapid double-clicks, clicking another control, or waiting for the timeout will not trigger accidental removal.
- Provides independent RGB effects for the energy field, core, rings, and lattice. Crafting accelerates the animation only; visual settings do not change processing speed.
- Does not force-load chunks. Work pauses while any structure chunk is unavailable and resumes after validation.

Use the in-game projection and JEI structure information as the authoritative material list and orientation reference.

## In-Game Guide

AE2 GuideME pages are available for the following blocks. Hover the item in an inventory or JEI and press `G` to view its purpose, structure instructions, network requirements, supported patterns, and controls:

- Molecular Sequence Rewrite Array
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

Exact item rules take priority over tag rules. Items with custom data such as enchantments, custom names, durability, or container contents are not deconstructed or reproduced. Each of the four sequence types has a configurable capacity up to `Long.MAX_VALUE`, with `Long.MAX_VALUE` as the default.

Entropy uses saturating `long` arithmetic:

```text
deconstruction entropy/item = max(1, saturated sum of four outputs / 64)
rewrite entropy/item        = max(1, saturated sum of four costs / 16)
effective cooling/second    = base cooling × current speed-card cooling multiplier
```

The entropy capacity defaults to `1000000`; base cooling defaults to `25` per second. The controller shows entropy per item, effective cooling, and the estimated wait before each operation can resume. An item whose entropy cost alone exceeds the configured capacity reports a configuration-limit error instead of cooling forever.

The default speed-card tiers are fully configurable:

| Installed cards | Parallel operations | Batch time | Cooling multiplier |
| ---: | ---: | ---: | ---: |
| 0 | 1 | 20 ticks | 1× |
| 1 | 2 | 10 ticks | 2× |
| 2 | 4 | 5 ticks | 4× |
| 3 | 16 | 2 ticks | 16× |
| 4 | 64 | 1 tick | 64× |

The JSON file documents these formulas and the corresponding categorized TOML paths. Existing rule files are upgraded to documentation format 4 without replacing configured rules.

Eligible item tooltips default to a compact Shift-expand prompt. The client option `matter_sequence_tooltip_mode` supports `DISABLED`, `HOLD_SHIFT`, and `ALWAYS_VISIBLE`.

## Core Configuration

The server configuration is `omnisequence-transfinite-server.toml`; the client configuration is `omnisequence-transfinite-client.toml`. Legacy `molecularmanipulator-*.toml` files are copied forward automatically when the new file does not yet exist.

Server options are grouped by subsystem:

| Category | Contents |
| --- | --- |
| `sequence_array` | Pattern pages, construction speed, and idle power |
| `sequence_array.matter_rewrite` | Sequence capacity, entropy capacity, and base cooling |
| `sequence_array.matter_rewrite.speed_cards` | Parallel operations, batch ticks, and cooling multiplier for each 0–4 card tier |
| `ae2_crafting` | General AE2 crafting-order limit |
| `omni_computation.optimizer` | Optimizer mode, graph limits, compile budget, and diagnostics |
| `omni_computation.cache` | Compiled graph cache enablement, size, and expiry |
| `omni_computation.execution` | Parallel execution, candidate selection, and precompilation |
| `omni_computation.dispatch` | Batch dispatch and main-thread work budgets |

Client options are grouped under `tooltips` and `visual`.

| Option | Default | Purpose |
| --- | ---: | --- |
| `sequence_array.pattern_pages` | 20 | Pattern pages available to the Sequence Array Controller; 36 slots per page |
| `sequence_array.build_blocks_per_tick` | 32 | Blocks placed or dismantled per tick |
| `sequence_array.idle_power` | 128 | Sequence Array Controller idle power in AE/t |
| `sequence_array.matter_rewrite.matter_sequence_capacity` | `Long.MAX_VALUE` | Independent capacity of each Matter Sequence type |
| `sequence_array.matter_rewrite.matter_entropy_capacity` | 1,000,000 | Maximum stored entropy |
| `sequence_array.matter_rewrite.matter_entropy_cooling_per_second` | 25 | Base entropy removed per second before the card-tier multiplier |
| `ae2_crafting.max_crafting_order_amount` | 1,000,000,000,000 | Maximum amount in one AE2 autocrafting order |
| `omni_computation.optimizer.omni_max_fast_mode` | `SAFE` | Omni-Computation Core recipe-tree aggregation mode |
| `omni_computation.optimizer.omni_max_fast_max_nodes` | 8192 | Maximum unique recipe nodes compiled in one aggregation |
| `omni_computation.optimizer.omni_max_fast_compile_budget_ms` | 100 | Aggregation compile budget before falling back to AE2 |
| `omni_computation.optimizer.omni_max_fast_diagnostics` | `false` | Logs aggregation timing and fallback reasons |
| `omni_computation.dispatch.omni_batch_dispatch_enabled` | `true` | Enables batch material dispatch for compatible providers |
| `omni_computation.dispatch.omni_compat_dispatch_max_calls_per_tick` | 2147483647 | Per-core emergency ceiling for complete `1×` provider calls |
| `omni_computation.dispatch.omni_compat_dispatch_max_time_us` | 20000 | Server-wide adaptive compatibility-dispatch budget |
| `omni_computation.dispatch.omni_dispatch_max_work_units` | 2147483647 | Maximum scheduler work units per core and tick |
| `tooltips.matter_sequence_tooltip_mode` | `HOLD_SHIFT` | Client-only Matter Sequence tooltip mode |
| `visual.dynamic_effect_level` | 2 | Client-only visual effects: 0 off, 1 reduced, 2 full |

Ordinary and unknown providers receive adaptive runtime-scaled patterns only for safe single-ingredient recipes. Multi-ingredient and other non-scalable paths send complete original recipes one at a time, preserving machine rotation and back-pressure behavior. A server-wide adaptive time slice replaces the old fixed 32-call limit: dispatch accelerates while the server has headroom and contracts as average MSPT approaches 45. Multiple cores, CPUs, and patterns do not each claim a separate full time budget. Only providers that explicitly declare atomic batch support receive complete multiplied multi-ingredient inputs.

Existing flat options and the previous `matter_speed_cards` section are moved into the categorized paths while preserving their values. The mod creates a `.toml.bak` backup before migration. The retired `omni_batch_allow_substitution_patterns` key is removed without resetting other custom values. If another unsupported option is found, the mod keeps up to five backups and atomically rebuilds the file from the current schema; missing or out-of-range known values are repaired without resetting other valid settings.

## Installation and Build

Install the required dependencies above and place the built JAR in both the client and server `mods` directories. Before upgrading, fully stop the game, use the same version on both sides, and keep exactly one active `omnisequence-transfinite-*.jar` in each `mods` directory to avoid duplicate Mod IDs.

```powershell
./gradlew.bat clean build --no-configuration-cache
```

Build artifact:

```text
build/libs/omnisequence-transfinite-1.3.9-hotfix.jar
```

See [CHANGELOG.md](CHANGELOG.md) for version history. This project is licensed under the [MIT License](LICENSE).
