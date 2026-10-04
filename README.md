# OmniSequence: Transfinite

[简体中文](README.zh-CN.md) · [API documentation](docs/README.md) · [Changelog](CHANGELOG.md)

An endgame AE2 and ExtendedAE expansion for research, large crafting jobs and
quantum-linked multiblocks. Mod ID: `molecularmanipulator`.

## Version and dependencies

| Component | Current requirement |
| --- | --- |
| OmniSequence | 2.0.8, branch `1.21.1-neoforge` |
| Minecraft / Java | 1.21.1 / Java 21 |
| NeoForge | 21.1.220 or later |
| AppliedEnhancements | 1.1.0 or later, client and server |
| AE2 | 19.2.17 or later; tested with 19.2.17 and 19.2.18 |
| ExtendedAE | 1.21-2.2.32-neoforge or later |
| LDLib2 | 2.2.18 or later |
| ExpandedAE | Version 2.1.1 is incompatible with the pattern-provider Mixin |
| JEI / AdvancedAE | Optional; AdvancedAE enables its research/integration |

Install dependencies separately. OmniSequence does not embed their classes.
AppliedEnhancements 1.1.0 provides the shared cycle transaction
API used by this source; older 1.0.x builds are insufficient.

OmniSequence caches native smart-doubling detection and preserves external pattern ownership.

## Machines and progression

| Machine | Role |
| --- | --- |
| Molecular Sequence Rewrite Array | 360 encoded-pattern slots, long-count crafting and deterministic reusable inputs |
| Assembler Matrix Sequence Rewrite Core | Crafting core for an ExtendedAE Assembler Matrix; persistent inputs, outputs and cancellation refunds |
| Sequence Array | Quantum-connected pattern storage, ME/player-assisted assembly and 9 independent passive crafting slots |
| Omni-Computation Core | Virtual CPU lanes, fair dispatch and exact output accounting |
| Transfinite Compute Nexus | Single-block CPU equivalent with one powered channel |
| Matter Fabrication Well | Data-driven recipes, controller-owned research and pattern assemblies |
| Event Horizon Singularity Hub | Simultaneous tagged resource collection and fluid-backed sample duplication |
| Miniature Black Hole / White Hole | Transfer nearby items and non-player entities to the unique white hole |

The two molecular crafting machines and the Sequence Array retain accepted
reusable-tool work across saves. Deterministic same-key and one-damage-per-craft
tool transitions can batch; probabilistic transitions and key-changing containers
use the safe native path. Material, power and per-key capacity checks still apply.

The Sequence Array no longer exposes Matter Rewrite balances, decomposition,
entropy or the old Shift tooltip. Its passive slots accept bounded batches of up
to 64 crafts per evaluation so disabling/removing a pattern stops new work promptly.
Already-paid output stays owned by the controller until storage accepts it.

Well research has nine configurable rounds. Normal ordering prepares the next
round; Shift prepares the remaining rounds and completes directly to the selected
maximum after one research run. Preparation can stop and refund its cache; an
ordered research run cannot stop or pause after starting. Delivered order output
belongs to that research cache, preventing downstream recipes from stealing base
ingredients. Idle ticks do not enumerate ME stock; active preparations share one
stock/index refresh every 20 ticks.

Tier 3 requires the two stage-2 branches and unlocks miniature holes and Hub
blocks. Miniature Black Hole uses 100,000 AE singularities; Miniature White Hole
uses 1,000,000,000 matter balls. Hub ordinary material costs are 100× their base
recipe amounts, while the controller/core retain one black/white hole input.

## Singularity collection and duplication

The Hub overview contains the quantum-entangled singularity slot and running
state. Its collection page lists every eligible item from configurable tags after
blacklist exclusions. All eligible types are produced simultaneously: default
1,000 items each every 20 ticks. Resource collection controls the running animation.

The duplication page accepts one sample and up to 64 miniature black holes.
Black holes generate Singularity Sequence Matter into connected ME fluid storage
using the configured FE/AE priority. Default output is 20 mB per black hole every
20 ticks. Copying one sample item consumes 1,000 mB; the sample is preserved.
The page shows both possible energy costs and the actual previous-cycle usage.
Generation requires energy and fluid space. The standalone Sequence Matter item
was removed; its source/flowing fluid and bucket remain.

Only one miniature white hole can be placed per dimension; its chunk is forced
while present. Black holes pull items and eligible entities gradually, exclude
players and deliver captured contents to that white hole.

## Networking and crafting safety

Linked multiblocks use matching quantum-entangled singularities. Assembly consumes
player inventory first, then ME storage; dismantling returns to ME first and retains
overflow safely. Structure damage/unloading retains owned work for recovery.

Omni dispatch preserves original AE provider identity. Explicit atomic providers
receive aggregate inputs; ordinary providers use complete single recipes under
fair per-tick budgets. `OmniBatchProviderAdapterRegistry` lets optional integrations
register atomic capabilities without modifying the provider class. BigInteger
adapters use a separate contract. See the [API index](docs/README.md).

Supported deterministic batching does not bypass recipe permissions, storage,
power or output headroom. Same-output recipes with overlapping alternatives may
still select different time/power during a queue split; newly added higher-priority
overlaps can leave queued work waiting. See the well API limitations.

## Guides and configuration

The bilingual in-game guide uses task pages, resource tables and recipe panels.
Multiblock model previews were removed from the large machine pages to reduce
rendering cost. JEI includes the well recipes and Sequence Matter fluid information.

Global files: `config/omnisequence-transfinite-server.toml` and
`config/omnisequence-transfinite-client.toml`. Both names and help text are localized.
See the complete [configuration reference](docs/configuration.md) for paths,
defaults, bounds, energy units and migration behavior. Repeated failures share a
global one-minute log window; optional profiler summaries remain disabled by default.

## Installation and build

Install the dependencies listed above in the client and server `mods/` folders.
Use matching mod builds on both sides. To build from source, use Java 21 and
place `appliedenhancements-1.1.0.jar` in `libs/`.
The prerequisite must provide `AelisBatchExecutionContext` and `AelisExactCraftingPlanApi`.

```sh
./gradlew build apiJavadoc
```

The runtime JAR is `build/libs/omnisequence-transfinite-2.0.8.jar`.
Public Java API documentation is generated under `build/docs/api/`.

Licensed under the [MIT License](LICENSE).
