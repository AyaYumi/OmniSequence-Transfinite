# Changelog

## 1.3.6 - 2026-07-29

### Added

- Added AE2 GuideME documentation for the Molecular Sequence Rewrite Array,
  Assembler Matrix Sequence Rewrite Core, Omni-Computation Core, and Sequence
  Array Controller.

### Changed

- Reworked the Sequence Array Controller toolbar to widen its pattern page
  buttons and separate Build from Dismantle.
- Added a timed two-step confirmation to Dismantle so rapid double-clicks and
  unrelated clicks cannot accidentally start structure removal.
- Made Shift-moving supported encoded patterns fill the current pattern page
  first and continue into later pages when needed.
- Restricted the Sequence Array Controller pattern inventory and advertised
  recipes to molecular-assembler-compatible AE2 crafting, smithing, and
  stonecutting patterns.

See [RELEASE_NOTES_1.3.6.md](RELEASE_NOTES_1.3.6.md) for the complete bilingual
release and upgrade notes.

## 1.3.5-fix - 2026-07-28

### Changed

- Replaced the retired fixed 32-call ordinary-provider throttle with an
  `Integer.MAX_VALUE` logical scheduling ceiling and one server-wide,
  load-adaptive time budget. Work rotates between tasks and patterns so a large
  request cannot monopolize a server tick.
- Cached negative explicit-batch-provider topology and classified extracted
  inputs without temporary collection allocation on the ordinary-provider hot
  path.
- Removed the obsolete `omni_unscaled_dispatch_attempts_per_tick` configuration
  option. Existing TOML files remove only that retired key while preserving
  current custom settings.

### Fixed

- Ordinary multi-input processing patterns now dispatch repeated, complete
  original `1x` recipes. Every call keeps all ingredients together, preventing
  different machines from being filled by different ingredient types and
  deadlocking one-to-many processing setups.
- Normalized ExtendedAE Plus planning-time scaled multi-input wrappers before
  dispatch, preventing an already-multiplied recipe from bypassing the atomic
  multi-input guard.
- Kept aggregate dispatch for explicit batch endpoints and adaptive doubling
  for safe single-input patterns, preserving high throughput where the target
  can accept it without breaking compatibility.
- Removed production-unsafe Mixin helper class loading and routed the AE2 long
  amount widget through an application bridge, fixing startup and crafting
  amount screen class-loading crashes.

See `RELEASE_NOTES_1.3.5-fix.md` for installation notes, dispatch behavior and
compatibility limits.

## 1.3.5 - 2026-07-27

### Changed

- Added native six-direction connected rendering for the Computation Core Frame,
  including seamless faces and non-overlapping translucent border corners.
- Updated the Universal Pattern Matrix texture and enabled its translucent render layer.
- Limited each provider/pattern pair to one scaled-dispatch growth step per server
  tick, preventing a single lane from probing `1, 2, 4, ...` in one tick while
  retaining the learned multiplier for the next tick.
- Replaced the fixed 32-call compatibility throttle with an
  `Integer.MAX_VALUE` hard ceiling plus one strict server-wide, load-aware time
  slice. Active Omni cores share up to 20 ms while average MSPT is low and
  automatically contract toward a rotating single-lane progress token as MSPT
  approaches 45. Task iteration also rotates and gives each compatibility
  pattern a short sub-slice so one huge recipe cannot starve later recipes.
- Cached negative explicit-batch-provider topology once per job pattern and
  server tick. Ordinary multi-input compatibility pushes now skip repeated
  waiting-for analysis, pattern validation and provider pre-scans while retaining
  the original complete-recipe `pushPattern` call and shared time budget.
- Removed the obsolete `omni_unscaled_dispatch_attempts_per_tick` option from
  the server config and configuration screen. Existing TOML files migrate by
  deleting only that retired key, preserving every current custom value.

### Fixed

- Routed non-explicit multi-input providers through AE2-style repeated complete
  `1x` recipe calls, bypassing the runtime-scaled-pattern context entirely. This
  prevents one-to-many targets from routing large waves of the first, second and
  later ingredients into different machines and deadlocking their input slots.
  The `Integer.MAX_VALUE` parallelism remains a logical scheduling window; real
  one-recipe calls are time-sliced by the controller-wide per-tick allowance,
  while single-input patterns and explicit batch providers retain adaptive
  scaling.
- Removed helper and anonymous classes from the Mixin package so transformed
  AE2 crafting CPU targets no longer trigger Mixin `IllegalClassLoadError`
  during startup or adaptive provider iteration.
- Normalized ExtendedAE Plus planning-time scaled multi-input tasks before an
  Omni CPU executes them, so an EAP `1x` wrapper cannot conceal an already
  multiplied ingredient batch from the atomic-dispatch guard.
- Replaced the production-unsafe direct reference to the client accessor mixin
  with an application bridge implemented on AE2's number entry widget. Opening
  the long crafting amount screen no longer fails with
  `NoClassDefFoundError: NumberEntryWidgetAccessor`.
## 1.3.4-hotfix - 2026-07-27

### Fixed

- Removed the `ModConfigSpec` core-class mixin that could run after NeoForge had
  already loaded its target and abort startup with `MixinTargetAlreadyLoadedException`.
- Moved saved-world configuration migration to NeoForge's supported
  `ServerAboutToStartEvent`, eliminating the remaining lifecycle core-class mixin.

## 1.3.4 - 2026-07-27

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
  defaults. Missing current options and invalid values retain NeoForge's normal
  targeted correction behavior.

### Compatibility

- Existing worlds, patterns and configuration files remain compatible.
- The stable mod ID remains `molecularmanipulator`.
- Dedicated batch-provider and machine whitelist behavior is unchanged.

## 1.3.3-hotfix - 2026-07-26

### Fixed

- Reacquired render buffers after every render-type switch in the Omni
  Computation Core and separated multiblock projection model/outline passes,
  preventing `BufferBuilder: Not building!` client crashes.
- Replaced output-return-driven adaptive dispatch with complete-input acceptance
  feedback, so slow, high-parallel and output-less processing targets no longer
  stall material delivery while waiting for products.
- Repeated standard provider attempts safely within the same server tick. Every
  attempt remains one complete AE2 recipe with independent extraction and
  accounting; rejection or provider-side queuing stops the current tick and
  contracts the dispatch window.
- Removed the exact `PatternProviderLogic` class requirement from the standard
  adaptive path. Third-party providers that correctly implement AE2's
  `ICraftingProvider` success, rejection and busy-state contract can now use the
  same bounded high-throughput dispatch.
- Added full-amount preflight checks and cache-aware initial windows for standard
  AE2 pattern providers, while preserving the existing direct-batch path for
  explicitly compatible machines.

### Compatibility

- Existing worlds, patterns and configuration files remain compatible.
- The stable mod ID remains `molecularmanipulator`.
- Dedicated batch-provider and machine whitelist behavior is unchanged.

## 1.3.3 - 2026-07-25

### Fixed

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

- Minecraft 1.21.1
- NeoForge 21.1.230 or later
- Applied Energistics 2 19.2.17 or later
- ExtendedAE 1.21-2.2.32-neoforge or later
- Advanced AE 1.6.11 or later is optional
- ExtendedAE Plus and JEI integrations are optional

### Notes

- Existing worlds continue to use the stable `molecularmanipulator` namespace.
- Extremely large crafting requests remain subject to recipe-specific compatibility fallbacks.
