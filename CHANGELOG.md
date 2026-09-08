# OmniSequence: Transfinite 2.0.1-forge

Minecraft 1.20.1 / Forge 47 / Java 17

## English

| Area | Update |
| --- | --- |
| Feature parity | Ported the 1.21.1 branch's 2.0.1 update: generic AEKey inputs, indexed output lookup, cached research definitions, queue lookup reuse and JEI/GuideME input display. |
| AE2 15 compatibility | Added `ForgeRecipeCodecs.GENERIC_STACK` for `ae_inputs`. JSON keeps `#t` and `#`; the bridge translates AE2 15's native NBT and uses `key_nbt` SNBT for lossless key synchronization. Invalid or unavailable generic inputs reject the recipe. |
| Lookup invalidation | Observe RecipeManager's replaced `byName` snapshot instead of its freshly allocated recipe collection. Research changes remain live per controller; data reload rebuilds the index. |
| Runtime resources | Corrected the controller loot function to Forge 1.20.1 `copy_nbt` with `BlockEntityTag` destinations. |
| Documentation and cleanup | Updated bilingual README and Forge API references; consolidated the MIT license while retaining contributor credits. Runtime models, textures and Forge UI remain in place. |
| Verification | Added Forge runtime regressions for custom AEKeys, exact NBT/long serialization, ordinary and API batching, output isolation, persistence, refunds and recipe/research reloads. |

Requires AE2 15.4.10, ExtendedAE 1.20-1.4.18-forge or newer compatible Forge build, Glodium 1.20-1.5-forge, GuideME 20.1.15 and separately installed AppliedEnhancements 1.0.6-forge. Update both client and server. This JAR is for Minecraft 1.20.1 Forge; the NeoForge 1.21.1 artifact remains separate.

Known upstream limitation retained: overlapping alternatives with identical outputs can change the selected processing recipe after batch splitting. An earlier overlapping recipe introduced by reload can leave an existing queue waiting. Recipes with different outputs remain isolated.

## 中文

| 项目 | 更新说明 |
| --- | --- |
| 功能同步 | 移植 1.21.1 分支的 2.0.1 更新：通用 AEKey 输入、产物查询索引、研究定义缓存、队列查询复用，以及 JEI／GuideME 通用输入展示。 |
| AE2 15 适配 | 新增 `ForgeRecipeCodecs.GENERIC_STACK` 解析 `ae_inputs`。JSON 沿用 `#t` 和 `#`，内部转换为 AE2 15 原生 NBT，并用 `key_nbt` SNBT 无损同步完整 Key。非法或不可用的通用输入会拒绝配方。 |
| 缓存失效 | 检测 RecipeManager 被替换的 `byName` 快照，避免每次新建配方集合导致缓存失效；各控制器研究进度实时生效，配方重载重建索引。 |
| 运行资源 | 控制器掉落表改为 1.20.1 支持的 `copy_nbt`，数据写入 `BlockEntityTag`。 |
| 文档与清理 | 更新中英文 README 和 Forge API 文档；合并重复 MIT 许可并保留贡献者版权，继续保留运行模型、纹理及 Forge 原生界面。 |
| 验证 | 新增 Forge 游戏回归，覆盖自定义 AEKey、精确 NBT／long 序列化、单份及批量投料、产物隔离、存档、退款与配方／研究重载。 |

前置：AE2 15.4.10、ExtendedAE 1.20-1.4.18-forge 或更新的兼容 Forge 构建、Glodium 1.20-1.5-forge、GuideME 20.1.15，以及单独安装的 AppliedEnhancements 1.0.6-forge。客户端和服务端同时更新。本包用于 Minecraft 1.20.1 Forge，与 1.21.1 NeoForge 安装包分开。

沿用的上游已知限制：产物相同、可替代原料重叠时，批量拆分可能改选加工配方；重载时新增更靠前的重叠配方，也可能让旧队列等待。产物不同的配方保持隔离。

---

# 2.0.0-forge — Minecraft 1.20.1

- Make matter deconstruction/reproduction highlights follow their synchronized enable switches; stopped operations use ordinary buttons.
- Port the 2.0.0 feature set to Java 17 / Forge 47 with AppliedEnhancements 1.0.6-forge.
- Adapt networking, AE2 15 inventories and recipes, Forge capabilities and persistent chunk tickets.
- Redesign every machine and JEI page as a light sci-fi console with custom frames, buttons, text fields, slots, toggles and clear selected/disabled states; no LDLib2 or XML overlays.
- Unify JEI recipe cards and interactive structure previews with the light console palette and custom input/output slots.
- Deepen light-console surfaces and slot borders; move JEI structure quantities to contrasting badges below their models.
- Use square corners for console frames, panels, buttons, text fields and JEI cards.
- Match AE2 19.2.17's cool-grey panel materials, white slot borders, dark text fields and cyan highlights using native Forge 1.20.1 drawing; retain readable button text and quantity badges.
- Remove default text shadows from buttons and fields while preserving vanilla caret spacing, selection and editing; use a contrasting light caret on dark text fields.
- Synchronize total occupied pattern slots so paging and filtering cannot change the displayed machine-wide usage.
- Refactor all six machine screens and JEI layouts against the 1.21.1 branch; restore the 332x364 Omni Computation structure/telemetry/quantum layout, horizontal actions and centered inventory.
- Share fixed machine geometry and validate it against AE2 slot definitions; retain search, tab restoration, confirmation behavior and high-GUI-scale input handling.
- Show adjacent-block thumbnails beside all six output directions with neighbor/status tooltips; rebuild cached previews only when block states or direction settings change.
- Adapt vertex submission and render bounds; scale large controllers to fit the display.
- Preserve the Forge CPU readiness safeguard and cached/bounded scheduling behavior.
- Adapt ExtendedAE's expatternprovider namespace and gate unavailable dependency-only materials.
- See docs/FORGE_PORT.md and libs/README.md for compatibility details.

# Changelog

## 1.3.9-forge - 2026-08-18

### Changed

- Increased the default Sequence Array pattern capacity from 20 pages (720
  slots) to 200 pages (7,200 slots). Existing explicit configurations are left
  unchanged.
- Removed global network-storage probing and refresh caches. Only AE2 creative
  cells and ExtendedAE infinity cells now expose `Long.MAX_VALUE` (`9.2E`);
  every other storage source keeps its original advertised amount.

### Fixed

- Prevented malformed third-party pattern candidates with no matching output
  from dividing by zero in AE2's crafting tree, including MAX_FAST native
  compatibility boundaries and full native fallback.
- Restored speculative MAX_FAST tree structure before native fallback so a
  failed optimized attempt cannot leak mutable process state into AE2.
- Prevented Omni crafting lanes with residual inventory from being selected,
  reused, or removed until AE2 has returned their items to network storage.
- Fixed Forge startup failures under Mixin 0.8.5 by moving runtime helper
  types and static utility methods out of transformed Mixin classes.
- Restricted both Molecular Center and Omni-Computation AE2 connectivity to
  their controllers. Structure casings and outer parts no longer expose grid
  nodes, preventing persistent self-connection crashes after controller reloads.

## 1.3.8-forge - 2026-08-11

### Added

- Exposed the Molecular Center's large pattern inventory as segmented logical
  containers in AE2's Pattern Access Terminal.
- Added the public Omni Batch Provider API v1, including atomic two-phase
  admission/commit, explicit ownership, rejection, backpressure, and an Omni CPU
  marker for third-party provider integrations.
- Ported transactional MAX_FAST graph planning for deterministic crafting,
  smithing, stonecutting, and supported AdvancedAE processing patterns, with
  reusable-input boundaries and exact native AE2 fallbacks.
- Added persistent reusable-input batches for deterministic unchanged remainders
  and `+1` durability tools, including mixed damage states, cancellation refunds,
  and NBT-backed dismantling recovery.
- Added configurable Matter Sequence capacity, entropy capacity and cooling, plus
  independent throughput, cycle-time, and cooling multipliers for 0–4
  Acceleration Cards.

### Changed

- Moved server options to the global
  `config/omnisequence-transfinite-common.toml` file and grouped every common and
  client option by subsystem. Existing flat/server values migrate with a backup.
- Matter Sequence capacity now defaults to `Long.MAX_VALUE`; infinite storage
  uses source-agnostic boundary detection and displays the numeric `9.22E` long
  limit instead of an infinity symbol.
- Accepted reusable batches settle their complete long-count aggregate in one
  machine tick. Post-commit failures retain provider ownership so AE2 cannot
  duplicate an already accepted batch.
- Reorganized and centered the Molecular Center matter interface, expanded its
  status information, and hid Build once the structure is formed.

### Fixed

- Added cancellation-aware MAX_FAST queueing and task-local transactional state,
  preventing obsolete calculations and speculative state from surviving a
  canceled request or unsafe fallback.
- Added saturating arithmetic to network storage, matter values, output recovery,
  and AE2 key-counter aggregation without allowing signed wraparound.
- Plans whose required counts, recipe totals, or byte usage exceed the signed-long
  limit are now marked incomplete and cannot be submitted, preventing apparently
  craftable jobs from stalling forever.
- Added precise reusable-batch cancellation lifetimes, safe late-rejection
  fallback, invalid-NBT quarantine, and recovery drops for active batches and
  long-count output buffers.
- Added the production refmap declaration required by Forge's obfuscated runtime,
  fixing the storage-cell tooltip Mixin startup crash.
- Completed and verified matching English and Chinese labels/tooltips for every
  grouped configuration entry.

## 1.3.7-forge-fix - 2026-07-30

### Added

- Added runtime compatibility with AE2-UELM 15.5.0 while retaining official
  Applied Energistics 2 15.4.10 support. UELM uses its native long-amount
  confirmation path and skips the incompatible `int` field Mixin.

### Changed

- Added effect-cleared layouts for both fixed multiblocks while retaining
  strict compatibility with their complete legacy layouts.
- Controllers now identify a complete legacy layout and show an optional
  structure-update notice. Legacy structures remain formed and operational
  until a player explicitly starts the safe relocation.
- Updated projection, automatic construction, dismantling, JEI previews, and
  GuideME documentation for the current layouts and optional upgrade flow.
- Moved all OmniSequence GuideME pages into a dedicated top-level
  `OmniSequence: Transfinite` section while preserving item-page links.
- Controller screens now scale down only when their native size would exceed
  the current GUI area. Slots, buttons, tooltips, and JEI ghost targets use the
  same transformed coordinates.
- Replaced per-frame ghost-block model tessellation with reusable 16x16x16
  section VBOs for both multiblock projections. Changed sections rebuild
  incrementally, at most two per frame, while unchanged geometry is reused.
- Item-substitution crafting patterns are now permanently eligible for compatible
  batch dispatch. The retired `omni_batch_allow_substitution_patterns` option is
  removed from existing TOML files without resetting other custom values.
- Matter Sequence item tooltips now default to a compact Shift-expand prompt.
  A client setting can disable them, keep the `HOLD_SHIFT` behavior, or make
  them `ALWAYS_VISIBLE`.
- The Sequence Array Controller now supports debounced, cross-page
  input/output pattern search while retaining AE2's detailed encoded-pattern
  hover tooltip. Just Enough Characters remains optional and enables Pinyin
  matching when installed.

### Fixed

- Restored the vanilla translucent world backdrop behind both controller
  screens on Forge 1.20.1, drawing it before responsive GUI scaling so it
  covers the complete viewport.
- Sequence Array construction now honors Creative mode like Omni-Computation
  construction: materials are not consumed, and a failed placement cannot
  create a refunded block.
- Added compact English controller labels for narrow buttons and verified that
  every English and Chinese static/dynamic translation key has a matching
  entry.
- Reworked controller labels and values into width-aware columns and wrapped
  rows, preventing English text overlap. The Omni-Computation fixed-capability
  notice now renders at the normal readable font size.
- The AE2 crafting CPU selector now renders a compact localized Omni lane name
  inside its narrow row while retaining the complete name in the tooltip.

## 1.3.6-forge - 2026-07-29

### Added

- Added AE2 GuideME documentation for the Assembler Matrix Sequence Rewrite
  Core, Omni-Computation Core, and Sequence Array Controller. The standalone
  Molecular Sequence Rewrite Array remains absent from the Forge 1.20.1 build.

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
- Kept the Minecraft 1.21.1 NeoForge-only Expanded AE 2.1.1 conflict declaration
  out of Forge metadata because it targets a different loader and AE2 line.

## 1.3.5-fix-forge - 2026-07-28

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

## 1.3.5-forge - 2026-07-27

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
