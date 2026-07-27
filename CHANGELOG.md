# Changelog

## 1.3.5 - 2026-07-27

### Changed

- Added native six-direction connected rendering for the Computation Core Frame,
  including seamless faces and non-overlapping translucent border corners.
- Updated the Universal Pattern Matrix texture and enabled its translucent render layer.
- Limited each provider/pattern pair to one scaled-dispatch growth step per server
  tick, preventing a single lane from probing `1, 2, 4, ...` in one tick while
  retaining the learned multiplier for the next tick.

### Compatibility

- This release targets Minecraft 1.20.1, Forge 47.4.10 and AE2 15.4.10.
- The standalone Molecular Sequence Rewrite Array remains removed from the
  Forge 1.20.1 build; its functionality remains in the Molecular Center
  multiblock.

## 1.3.4-hotfix-forge - 2026-07-27

### Fixed

- Removed the `ForgeConfigSpec` core-class mixin that could run after Forge had
  already loaded its target and abort startup with `MixinTargetAlreadyLoadedException`.
- Kept the Forge-specific saved-world migration hook at the beginning of
  `ServerLifecycleHooks.handleServerAboutToStart`, before Forge selects and loads
  the world's server config. Forge posts `ServerAboutToStartEvent` only after that
  load, so moving migration to the event would ignore legacy world values on the
  first startup.

## 1.3.4-forge - 2026-07-27

### Changed

- Removed the obsolete `omni_provider_max_queued_items` and
  `omni_provider_send_operations` server options. Adaptive dispatch no longer has a
  configured material-window cap; fair remainder draining uses an internal high-throughput
  limit.
- Converted the complete dispatch work-unit accounting path to `long` and changed
  `omni_dispatch_max_work_units` to a long-valued option with a default of
  `2147483647` and a maximum of `Long.MAX_VALUE`.

### Fixed

- Added runtime-scaled processing patterns. One provider call now carries a
  complete `1, 2, 4, 8, ...` recipe batch, forwards every extracted input and
  lets AE2 account the corresponding scaled expected outputs.
- Added explicit provider feedback for rejected, fully inserted, queued and
  unverified pushes. Probes grow by doubling; after congestion the last
  successful batch remains the next tick's baseline before another doubled
  probe, avoiding a probe-only throughput gap.
- Enabled runtime-scaled patterns for every non-explicit AE crafting provider.
  AE2/ExtendedAE providers retain precise full/queued feedback, while AE2LT,
  AdvancedAE and other third-party providers use their public acceptance and
  busy-state signals. Explicit project batch providers keep the direct
  long-limit path.
- Preserved ExtendedAE Plus scaled-pattern identity, AdvancedAE directional
  input metadata and AE2LT overloaded-provider metadata while scaling, so
  compatible third-party providers receive the multiplied pattern directly
  instead of falling back to one recipe per tick.
- Persisted legitimate scaled remainder queues with a versioned format and kept
  fair multi-ingredient draining across reloads without confusing them with
  legacy unsafe queues.
- Added fair provider selection for duplicate patterns so a full or rejecting
  target cannot starve other available providers.
- Added a sticky single-recipe fallback for providers that accept `1x` but
  reject scaled patterns. AE2 performs a fresh extraction and accounting cycle
  for every call, while the new
  `omni_unscaled_dispatch_attempts_per_tick` server option shares a default
  limit of 32 real calls across all active CPU lanes of one Omni core.
  Demand lanes are prioritized on the next tick and can borrow unused
  reservations from lanes that ran earlier, while reaching the cap stops that
  CPU lane's single-recipe path before it can repeatedly extract and reinject
  the same task; already-scalable and explicit long-batch tasks remain eligible.
- Limited every dispatch batch by the remaining positive `waitingFor` headroom
  for all expected outputs and container items, preventing accumulated
  outstanding results from wrapping past `Long.MAX_VALUE` into negative counts.
- Added scaled AE waiting-for accounting as a secondary consistency check and
  pre-adjusted batch task progress before provider calls, allowing EAP virtual
  crafting to finish the final aggregate batch without leaving phantom outputs.
- Added strict config-schema regeneration. If a TOML contains any option that
  no longer exists in the current server or client schema, the previous file is
  backed up and the complete configuration is atomically rebuilt from current
  defaults. Missing current options and invalid values retain Forge's normal
  targeted correction behavior.

### Compatibility

- Ported the release to Minecraft 1.20.1, Forge 47.4.10 and AE2 15.4.10.
- Existing worlds, patterns and configuration files remain compatible.
- The standalone Molecular Sequence Rewrite Array remains removed from the
  Forge 1.20.1 build and was not restored by this port.
- The stable mod ID remains `molecularmanipulator`.
- Dedicated batch-provider and machine whitelist behavior is unchanged.

## 1.3.3-forge - 2026-07-26

### Fixed

- Replaced output-return-driven adaptive dispatch with complete-input acceptance
  feedback, so slow, high-parallel and output-less processing targets no longer
  stall material delivery while waiting for products.
- Repeated standard provider attempts safely within the same server tick. Every
  attempt remains one complete AE2 recipe with independent extraction and
  accounting; rejection or provider-side queuing stops the current tick and
  contracts the dispatch window.
- Removed the exact `PatternProviderLogic` class requirement from the standard
  adaptive path. Third-party providers that correctly implement AE2 15.4.10's
  `ICraftingProvider` success, rejection and busy-state contract can now use the
  same bounded high-throughput dispatch.
- Added full-amount preflight checks and cache-aware initial windows for standard
  AE2 pattern providers, while preserving the existing direct-batch path for
  explicitly compatible machines.

### Compatibility

- Ported the hotfix to Minecraft 1.20.1, Forge 47.4.10 and AE2 15.4.10.
- Existing worlds, patterns and configuration files remain compatible.
- The standalone Molecular Sequence Rewrite Array remains removed from the
  Forge 1.20.1 build and was not restored by this port.
- The stable mod ID remains `molecularmanipulator`.

### Platform

- Ported the release to the clean Minecraft 1.20.1 Forge 47.4.10 MDK baseline.
- Targeted Applied Energistics 2 15.4.10, ExtendedAE 1.20-1.4.12-forge and Glodium 1.20-1.5-forge.
- Replaced NeoForge metadata, networking, registries, capabilities and data paths with their Forge 1.20.1 equivalents.

### Fixed

- Reacquired render buffers after every render-type switch in the Omni
  Computation Core and separated multiblock projection model/outline passes,
  preventing `BufferBuilder: Not building!` client crashes.
- Made missing-material crafting summaries use the `ICraftingPlan` result from
  the same calculation, preventing unstable missing-item lists when external
  storage providers report different results during confirmation-page probes.
- Preserved extension data attached to AE2 crafting-plan summaries, fixing
  confirmation packet encoding with AE2: Crafting Tree installed.
- Removed completed-plan caching, in-flight calculation sharing and cancellation
  shielding from the Omni Computation Core so every request owns an independent,
  cancellable AE2 calculation.
- Restored AE2's native per-calculation inventory snapshot path instead of
  replacing it with an additional Omni snapshot context.
- Restored AE2's cooperative crafting-calculation pause handshake and added
  pause checkpoints inside Omni MAX_FAST compilation and execution, preventing
  live crafting-provider updates from racing long-running plan calculations.
- Saturated AE2WTLib restock-overlay inventory totals before they overflow and
  displayed the infinite sentinel as ∞, preventing wireless-terminal restock
  overlays from crashing on infinite network inventories.
- Accelerated actual crafting dispatch by extracting one recipe first and then
  deriving the safe batch size directly from available inputs and power, avoiding
  repeated whole-pattern searches when intermediate materials are still missing.
- Added bounded high-throughput dispatch for AE2 and ExtendedAE processing
  providers. A standard provider now starts with one queue-bounded probe and uses
  primary-output timing to select 16x, 4x, 2x or unchanged window growth; congestion,
  rejection and interrupted jobs contract the window. Explicit batch providers retain
  their direct long-limit path, and unsupported recipes retain the original path.
- Persisted each queued batch's one-craft ingredient vector and reseed every
  ingredient before bulk draining on every provider send tick. This prevents
  one-tick machines from leaving the first remaining material free to monopolize
  shared input slots after consuming the previous tick's reservation.
- Preserved single-recipe execution when batch expansion is unavailable or rolls
  back, while keeping the explicit provider whitelist.
- Replaced the Omni-Computation Core's unbounded same-tick dispatch with an
  adaptive work-unit scheduler. Virtual CPU lanes share fair per-tick quotas,
  batch-safe pushes retain unlimited logical craft counts, and all controllers
  share a configurable server-wide emergency deadline.
- Renamed client and server configuration files to the OmniSequence Transfinite
  name, with non-overwriting migration for global, default and per-save configs.

### Changed

- Removed the obsolete material-calculation cache-hit statistic from the Omni
  Computation Core interface.
- Removed the standalone Molecular Sequence Rewrite Array block, its block entity,
  menu, recipe and client resources from the Forge 1.20.1 build. Existing placed
  copies are removed as missing blocks when old worlds are loaded.
- Replaced the removed block in the Assembler Matrix Sequence Rewrite Core recipe
  with an AE2 Molecular Assembler.

## 1.3.2 - 2026-07-25

### Changed

- Replaced Quantum Entangled Singularities in OmniSequence recipes with ME
  Quantum Rings.
- Replaced the Quartz Cluster in the Computation Crystal Pylon recipe with a
  256k ME Storage Component.

### Fixed

- Infinite amounts now use source-specific compatibility for AE2 creative cells
  and ExtendedAE infinity cells.
- Removed network-wide simulated extraction probes so storage buses and external
  storage providers retain their native amounts without inventory refresh stalls.
- Displayed exact `Long.MAX_VALUE` storage amounts as `Infinite` instead of `9.2E`
  in AE2 terminals and storage tooltips.

## 1.3.1 - 2026-07-25

### Fixed

- Fixed an Omni Computation Core formation crash by respecting AE2's 16-thread
  per-block limit while preserving transfinite cluster-level parallelism.
- Displayed transfinite crafting storage and parallelism as `Infinite` in AE2's
  crafting CPU list and tooltip instead of abbreviated integer limits.
- Fixed shutdown stalls while unloading quantum-linked multiblocks by avoiding
  block updates and redundant AE2 grid work after their chunks begin unloading.

## 1.3.0 - 2026-07-25

### Highlights

- Renamed the project to **OmniSequence: Transfinite** while retaining the
  `molecularmanipulator` mod ID for world and configuration compatibility.
- Added the Omni Computation Core multiblock with transfinite crafting storage,
  long-range logical parallelism and controlled AE2 crafting calculation acceleration.
- Added quantum-linked AE networking and pre-formation construction access for both
  major multiblock structures.
- Extended AE2 crafting requests and supported infinite item sources to long values.
- Added safe MAX_FAST demand aggregation, duplicate subtree merging, batch topology
  calculation and compatibility fallbacks for substitution, container and dynamic patterns.
- Fluid-only substitution patterns remain eligible for deterministic MAX_FAST calculation
  and long-value batch dispatch; item substitution retains the conservative safety fallback.
- Added completion-feedback adaptive material dispatch: windows grow after full primary-output return and contract on provider congestion, rejection or interrupted jobs.
- Added original black-purple crystal models, textures, effects and redesigned machine UIs.
- Added one-click multiblock dismantling, player-facing placement and hostile-spawn suppression
  around the Omni Computation Core.

### Compatibility

- Minecraft 1.20.1
- Forge 47.4.10 or later
- Applied Energistics 2 15.4.10
- ExtendedAE 1.20-1.4.12-forge
- A compatible Forge 1.20.1 release of Advanced AE is optional
- ExtendedAE Plus and JEI integrations are optional

### Notes

- Existing worlds continue to use the stable `molecularmanipulator` namespace.
- Extremely large crafting requests remain subject to recipe-specific compatibility fallbacks.
