# Matter Fabrication Well: Recipes and Research API

For modpack and mod authors: add well recipes, choose which research unlocks them,
or import another machine's supported recipes. For building and operating a well,
see the [player guide](../README.md) or press G on its controller in game.

Available since OmniSequence: Transfinite 2.0.0; current for 21.3.0.1.
Target: Minecraft 1.21.1 / NeoForge, Java 21, AE2 19.2.18, and the
required Applied Enhancements 1.1.2+ (compile API 1.1.4). The Mod ID stays
`molecularmanipulator`.

Other languages: [中文版](matter-research-api.zh-CN.md).
See also the [API index](README.md) and the separate
[Omni Batch Provider API v1](omni-batch-provider-api.md).

The well uses three recipe types:

| Recipe type | Purpose |
| --- | --- |
| `molecularmanipulator:matter_fabrication` | What a Matter Fabrication Well can produce. |
| `molecularmanipulator:matter_research` | Research that unlocks those recipes and adds production bonuses. |
| `molecularmanipulator:matter_machine_import` | An entire machine's recipe import, field mappings, and research branch. |

All three are ordinary data-pack recipes under `data/<namespace>/recipe/`. KubeJS adds
or replaces them with `ServerEvents.recipes` and `event.custom`; no extra plugin
is required. Research progress belongs to each well controller, not to the player
and not to a global network.

## 1. Quick start: custom well recipes behind research

With KubeJS installed, save this complete script as
`kubejs/server_scripts/well_research.js`, then run `/reload`. It adds a cobblestone-to-stone
process and the research that opens it. In a formed, powered well, complete AE Foundation,
then the new research to use the process. The example uses its own three-round depth table.

```javascript
ServerEvents.recipes(event => {
  // 1) A research that unlocks the custom recipe.
  event.custom({
    type: 'molecularmanipulator:matter_research',
    title: 'Stone deep research',
    stage: 3,
    sort_order: 100,
    prerequisites: ['molecularmanipulator:research/ae_foundation'],
    ingredients: [{ingredient: {item: 'ae2:fluix_crystal'}, count: '32'}],
    duration: 1200,
    ae_per_tick: 128,
    unlocks: ['kubejs:stone_processing'],
    depths: [
      {parallel: '4'},
      {material_multiplier: '3', parallel: '1024'},
      {
        ingredients: [
          {ingredient: {item: 'ae2:fluix_crystal'}, count: '512'},
          {ingredient: {item: 'minecraft:diamond'}, count: '64'}
        ],
        parallel: '9223372036854775807'
      }
    ]
  }).id('kubejs:stone_research');

  // 2) The well recipe itself.
  event.custom({
    type: 'molecularmanipulator:matter_fabrication',
    ingredients: [{ingredient: {item: 'minecraft:cobblestone'}, count: 1}],
    results: [{id: 'minecraft:stone', count: 1}],
    processing_time: 200,
    ae_per_tick: 64,
    requires_research: true
  }).id('kubejs:stone_processing');
});
```

With `requires_research: true`, the recipe is locked until any research that
lists it in `unlocks` has at least one completion (here `kubejs:stone_research`).

### 1.1 Locking a recipe behind a deeper research level

Recipe permission is binary: one completion opens the recipe, and there is no
per-recipe level field. To require a specific depth, such as 9/9 on
Omni-Computation, add a gate research with that prerequisite level and use it to
unlock the recipe instead of the built-in research:

```json
{
  "type": "molecularmanipulator:matter_research",
  "title": "kubejs.research.late_stone",
  "prerequisites": ["molecularmanipulator:research/omni_computation"],
  "prerequisite_levels": {
    "molecularmanipulator:research/omni_computation": 9
  },
  "ingredients": [{"ingredient": {"item": "ae2:fluix_crystal"}, "count": "64"}],
  "duration": 2400,
  "ae_per_tick": 512,
  "stage": 3,
  "sort_order": 200,
  "unlocks": ["kubejs:stone_processing"]
}
```

`kubejs:stone_processing` can then be crafted only after the gate research
completes once, and the gate itself cannot start until Omni-Computation has nine
completions. Use the string `"max"` instead of `9` to require whatever the
prerequisite's own `depths` length currently is.

Notes:

- Levels count completions, not the display-only `stage` number on each research.
- A numeric level is never lowered automatically. If it exceeds the prerequisite's
  `depths` length, the gate can never be started; keep it within range.
- `prerequisite_levels` keys are merged into `prerequisites`, so writing only the
  map still establishes the dependency; prerequisites not listed in the map
  default to one completion.

## 1.2 Machine imports and field mappings: `molecularmanipulator:matter_machine_import`

Declare imports in `kubejs/server_scripts` through `ServerEvents.recipes`; no extra
KubeJS plugin is required. This example imports furnace recipes into a research branch:

```javascript
ServerEvents.recipes(event => {
  event.custom({
    type: 'molecularmanipulator:matter_machine_import',
    machine: 'minecraft:furnace',
    serializers: ['minecraft:smelting'],
    inputs: {items: ['/ingredient']},
    outputs: {items: ['/result']},
    processing_time: 200,
    ae_per_tick: 256,
    research: {
      title: 'Furnace research',
      stage: 2,
      sort_order: 1100,
      prerequisites: ['molecularmanipulator:research/ae_foundation'],
      ingredients: [{ingredient: {item: 'minecraft:furnace'}, count: '1'}],
      duration: 600,
      ae_per_tick: 512
    }
  }).id('kubejs:furnace_import');
});
```

Every machine import, including custom declarations, automatically requires Sequence≥3 AND Computation≥3, with an effective display stage of at least 4. Custom stricter prerequisites are preserved; this minimum cannot be lowered.

The generated research ID is `molecularmanipulator:research/machine/kubejs/furnace_import`.
Imported recipe IDs start with `molecularmanipulator:fabrication/import/machine/kubejs/furnace_import/`.
Keep declaration IDs stable: changing the ID creates another research branch and does
not migrate completion counts stored on existing controllers.

| Declaration field | Meaning |
| --- | --- |
| `machine` | Required machine item ID. An absent item disables the import. KubeJS-registered items are supported. |
| `serializers` | Required nonempty list of source recipe JSON `type` IDs: recipe serializer IDs, not JEI category IDs. |
| `research` | Required; accepts all existing `matter_research` fields, including costs, prerequisites, `depths`, and `required_mods`. Imported recipe IDs are appended to `unlocks`; explicit additional unlocks are retained. |
| `inputs` / `outputs` | Optional; each replaces automatic parsing for that direction. Omit it to use existing parsers. When specified, include every resource in that direction. |
| `processing_time` | Imported recipes' base duration, default 400 ticks (20 seconds). Explicit custom values are preserved. |
| `ae_per_tick` | Imported recipes' base power, default 256 AE/t. Separate from `research.ae_per_tick`. |

### Field paths

`inputs` and `outputs` share this mapping format:

| Mapping field | Contents |
| --- | --- |
| `items` | Item field paths. Inputs accept `{item: ...}`, `{tag: ...}`, or `{ingredient: ..., count: ...}`. Outputs accept `{id: ..., count: ..., components: ...}`, also allowing `item` instead of `id`. |
| `fluids` | Fluid field paths accepting `{id: ..., amount: ..., components: ...}`, also allowing `fluid` instead of `id`. Amounts are in mB. |
| `resources` | Field paths containing serialized AE stacks: a `GenericStack` with `#t` and `#`, or `{key_type: ..., resource: ..., amount: ...}`. |
| `ae_keys` | Rules constructing registered AE resources from ordinary recipe fields; see below. |

Paths follow JSON Pointer: `/inputs/items` selects a nested field, `/inputs/0` selects
the first array entry, and the empty string selects the whole recipe. Escape `/` in
field names as `~1` and `~` as `~0`. Paths refer to JSON re-encoded by the source
recipe serializer; serializers may omit defaults, so this can differ from the
original KubeJS JSON. Arrays are read entry by entry. Alternative item ingredients
belong inside `ingredient`, for example
`{ingredient: [{item: 'minecraft:iron_ingot'}, {item: 'minecraft:gold_ingot'}], count: 2}`.
Explicit paths must be readable, arrays nonempty, and amounts positive integers.
An unreadable entry skips the whole recipe rather than importing only part of its
inputs or outputs. Import warnings report the total skipped count and at most eight
example machine/recipe IDs.

### Lightning and other special AE resources

This declaration maps `lightningTier` and `lightningCost` to Lightning Tech's native
AE lightning type. It replaces the built-in lightning assembly chamber import and
generates its research from this declaration:

```javascript
ServerEvents.recipes(event => {
  event.custom({
    type: 'molecularmanipulator:matter_machine_import',
    machine: 'ae2lt:lightning_assembly_chamber',
    serializers: ['ae2lt:lightning_assembly'],
    inputs: {
      items: ['/inputs'],
      ae_keys: [{
        key_type: 'ae2lt:lightning',
        amount_path: '/lightningCost',
        amount_default: 4,
        fields: {tier: 'high_voltage'},
        field_paths: {tier: '/lightningTier'}
      }]
    },
    outputs: {items: ['/result']},
    research: {
      title: 'Lightning assembly research',
      stage: 2,
      required_mods: ['ae2lt'],
      prerequisites: ['molecularmanipulator:research/ae_foundation'],
      ingredients: [{ingredient: {item: 'ae2lt:lightning_assembly_chamber'}, count: '1'}],
      duration: 600,
      ae_per_tick: 512
    }
  }).id('kubejs:lightning_assembly_import');
});
```

`key_type` must be an AEKey type registered by a mod. `amount_path` reads the amount;
if absent, `amount_default` is used (default 0, so supply a valid default or source).
`fields` supplies JSON constants, including nested objects. `field_paths` copies
source JSON values into named AEKey fields, falling back to the corresponding `fields`
constant when absent. A missing field without a default skips the recipe. System
fields `#t` and `#` cannot be overridden. `extreme_high_voltage` retains its native
lightning identity; no wrapped generic stack item is created.

For data resources, use `key_type: 'data_energistics:digitalization'`,
`fields: {resource: 'data_energistics:data_flow'}`, and the appropriate `amount_path`.
These rules decode existing AE types; new AEKey and JEI ingredient types must still
be registered by the resource's mod. Without a native JEI converter, the well does
not disguise a resource as a wrapped item.

### Reload and precedence

- Imports and research rebuild from effective recipes after reload and client recipe sync, including KubeJS changes.
- A serializer belongs to one import declaration. Custom declarations override built-in entries. If custom declarations overlap, they are sorted by declaration ID and the last wins; avoid overlaps.
- A missing machine, unmet `research.required_mods`, or no successfully imported recipes hides the branch.
- Recipes merge when the same machine has identical complete outputs and input amounts. Distinct inputs remain branches counted by the research panel.
- A recipe supports up to 12 item/AE inputs plus one fluid input, and up to 6 item outputs, 6 AE outputs, plus one fluid output. Extra fluids become AE fluid stacks and count against AE slots. Recipes exceeding these limits are skipped.
- Fuel, chance outputs, nonconsumed catalysts, temperature, and similar machine behavior are not automatically simulated by field mappings. Import only recipes whose consumed inputs and deterministic outputs can be represented accurately.

## 2. Research definition: `molecularmanipulator:matter_research`

Location: `data/<namespace>/recipe/<path>.json`.

| Field | Meaning |
| --- | --- |
| `title` | Display name or translation key. Required, must not be blank. |
| `stage` | Non-negative display tier, default `1`; `0` omits the numeric tier prefix for special research. List order uses `sort_order`; gating uses prerequisites. |
| `sort_order` | Navigation order, default `0`. Definitions are sorted by `sort_order`, then by ID. |
| `prerequisites` | List of research IDs; each defaults to one required completion. Self-references and cycles are invalid. |
| `prerequisite_levels` | Optional map `{research ID: required completions or "max"}` overriding individual thresholds. Keys are added to `prerequisites`. |
| `ingredients` | Base round cost. Required. Each entry is `{ingredient: {item: ...} or {tag: ...}, count: positive long}`. Item ingredients only; research cannot charge fluids or generic AE keys. |
| `duration` | Ticks per round, at least `1`, default `1200`. |
| `ae_per_tick` | Power drawn while researching, finite and non-negative, default `256`. |
| `required_mods` | Mod IDs that must all be loaded for the definition to appear and run. |
| `unlocks` | Full recipe IDs (not item IDs) that this research unlocks and boosts. |
| `depths` | Per-round cost and total benefit after each completion. The array length is the number of rounds; omitting it uses the default nine-round table in §3. |

### 2.1 Depth entries

Each `depths` element:

| Field | Meaning |
| --- | --- |
| `material_multiplier` | Round cost = base `ingredients` × this, default `1`. |
| `ingredients` | Optional. When present it fully replaces the round's materials and the multiplier is ignored; `[]` means a free round. |
| `parallel` | Total parallel limit after this round. Required positive long. |
| `speed_multiplier` | Legacy positive long, default `1`; accepted for compatibility and ignored. |
| `processing_ticks` | Legacy nonnegative tick count, default `0`; accepted for compatibility and ignored, including `1`. |

### 2.2 Long values and `2^53 - 1`

Every research long field (`count`, `material_multiplier`, `parallel`, `speed_multiplier`)
accepts a decimal string, and quoting is recommended. This does not apply to fabrication
item counts, generic-stack `#` amounts or star research's `information_cost`; those have
their own numeric codecs. Above JavaScript's exact integer limit `2^53 - 1`, a string
is mandatory. Write `'9223372036854775807'`, never a float or `'9.22E18'`.
JavaScript precision loss is not detected; an imprecise number is silently accepted
with the wrong value. Costs whose multiplication exceeds the long range refuse to
start instead of going negative or becoming free.

## 3. Default nine-round progression

Used when `depths` is omitted. Built-in research follows these parallel limits but
can replace each round's material cost with explicit `ingredients` (§6). Costs are
charged per round and are not the difference from the previous round. `parallel`
is how many complete recipe executions a single craft may run.

| Completions | Round cost / base cost | Parallel limit after |
| --- | --- | --- |
| 1 | ×1 | 1 |
| 2 | ×2 | 256 |
| 3 | ×4 | 65,536 |
| 4 | ×8 | 16,777,216 |
| 5 | ×16 | 4,294,967,296 |
| 6 | ×32 | 1,099,511,627,776 |
| 7 | ×64 | 281,474,976,710,656 |
| 8 | ×128 | 72,057,594,037,927,936 |
| 9 | ×256 | 9,223,372,036,854,775,807 (≈9.22E18) |

Deep research increases recipe parallelism only, including round nine. Several
researches owning the same recipe contribute their highest parallel limit.
Production speed comes from the Star Computation Engine on the controller's ME
grid: ordinary levels use `max(1, ceil(baseTicks / effectiveSpeedMultiplier))`,
where the effective multiplier includes overclocking; level 9 overclocking forces
one tick. Without an engine the multiplier is one. All built-in fabrication
recipes and generated bridge defaults use 400 base ticks (20 seconds); explicit
custom `processing_time` values are preserved. Research's own `duration` is
independent of production speed and this default.

A recipe with no research owner and `requires_research: false` remains usable
without research, but inherits the completed parallel limit of the tier-1
foundation research, `molecularmanipulator:research/ae_foundation`. Before that
progress (or if that definition is absent), its parallel limit stays `1`.
Explicitly owned recipes use only their own research parallel limits. Production
speed still comes from the grid's Star Computation Engine in either case; this
fallback does not unlock research-gated recipes.

## 4. Well recipe: `molecularmanipulator:matter_fabrication`

| Field | Meaning |
| --- | --- |
| `ingredients` | Counted item inputs, each `{ingredient: {item: ...} or {tag: ...}, count: ...}`. `count` is a numeric integer in `1..2147483647`, default `1`; the list defaults to empty. This is not the research long-string format. |
| `catalysts` | Optional prerequisite stacks, in the same format as `ingredients`. Defaults to empty. All assemblies in one formed well contribute their catalyst inventories; catalysts are never consumed or multiplied by batch count/research parallelism and are excluded from encoded pattern inputs. |
| `results` | Up to 6 item outputs as strict stacks `{id: ..., count: ...}`. |
| `fluid_input` | Optional fluid input `{id: ..., amount: ...}` in mB. Does not count toward the twelve-input limit. |
| `fluid_result` | Optional single fluid output, same format. |
| `ae_inputs` | Up to 12 generic AEKey inputs; see §4.1. |
| `ae_outputs` | Up to 6 generic AEKey outputs, using the same stack codec as `ae_inputs`. |
| `processing_time` | Base ticks, default `400` (20 seconds); values below `1` are raised to `1`. |
| `ae_per_tick` | Base power, default `64.0`; negative values are raised to `0`. |
| `requires_research` | Default `false`. See §4.2. |

Load-time error conditions: `ingredients` + `catalysts` + `ae_inputs` may not exceed 12;
at least one of `ingredients`, `fluid_input` or `ae_inputs` must be present; every
`ae_inputs` / `ae_outputs` amount must be positive; and at least one item, fluid, or AE result is
required. Unlike research definitions, a malformed well recipe fails to load
instead of being rejected at craft time.

### 4.1 Generic inputs (`ae_inputs`)

`ae_inputs` accepts every registered AEKey type, which is how gases, chemicals
and other addon resources are supplied. Entries use AE2's `GenericStack.CODEC`:
`#t` is the registered AEKey type ID and `#` is the raw amount per craft.

| Field | Meaning |
| --- | --- |
| `#t` | AEKey type ID. AE2 items use `ae2:i`, AE2 fluids use `ae2:f`; addons register their own. |
| `#` | Raw amount per craft, from 1 through 9223372036854775807. Item types count items, NeoForge fluid types count mB. |
| `id`, `components`, other fields | Decoded by the selected key type; use that addon's codec instead of assuming the item/fluid layout. |

```json
{
  "type": "molecularmanipulator:matter_fabrication",
  "ingredients": [{"ingredient": {"item": "minecraft:diamond"}, "count": 2}],
  "ae_inputs": [
    {"#t": "ae2:i", "id": "minecraft:diamond", "#": 2},
    {"#t": "ae2:f", "id": "minecraft:water", "#": 1000}
  ],
  "fluid_input": {"id": "minecraft:water", "amount": 250},
  "results": [{"id": "minecraft:obsidian", "count": 1}],
  "processing_time": 200,
  "ae_per_tick": 64
}
```

`#` uses AE2's numeric `Codec.LONG`, not the decimal-string codec used by
research fields, so a quoted `"3000000000"` is rejected. For values beyond
JavaScript's exact integer range, write an integer literal in a data-pack JSON
file or build the stack in Java with `new GenericStack(key, longAmount)`; do not
pass it through a JavaScript number first.

Generic keys match exactly, including components, while ordinary `ingredients`
keep their Ingredient/tag matching. Repeated or overlapping requirements are
additive; the same stock is never counted for two different requirements.

Recipes containing `ae_inputs` or `ae_outputs` require a pattern assembly. Manual item
and fluid ports cannot supply generic AE buffers and reject such recipes.

Java constructor for recipes with generic inputs:

```java
new MatterFabricationRecipe(ingredients, results, fluidInput, fluidResult,
        aeInputs, processingTime, aePerTick, requiresResearch); // aeInputs is List<GenericStack>
```

The older 6-, 7-, and 8-argument constructors remain available; omitted generic
input/output lists default to empty. The 9-argument constructor accepts both lists:

```java
new MatterFabricationRecipe(ingredients, results, fluidInput, fluidResult,
        aeInputs, aeOutputs, processingTime, aePerTick, requiresResearch);
```

To provide retained catalysts, use the 10-argument overload:

```java
new MatterFabricationRecipe(ingredients, catalysts, results, fluidInput, fluidResult,
        aeInputs, aeOutputs, processingTime, aePerTick, requiresResearch);
```

The canonical record also has a final nullable `ResourceLocation sourceRecipeId`.
Runtime machine imports attach it with `withSourceRecipeId(...)` to preserve source
recipe-lock checks. It is not a JSON field; normal native recipes leave it null.

### 4.2 Research permission

A well recipe is locked when a research lists it in `unlocks`, or when
`requires_research` is `true`. It becomes usable once any owning research has at
least one completion.
`requires_research: true` stays locked when no research grants it, when the
granting research is removed, or when its required mod is missing. A recipe also
stays locked if its granting research is currently unavailable (for example
AdvancedAE is absent).

This per-well research permission does not globally block other machines, and
already-built multiblocks keep working. Independent [star-research recipe locks](recipe-permissions.zh-CN.md)
also apply at covered execution paths, including the well.

### 4.3 Retained catalyst and encoding contract

This custom recipe consumes two iron ingots per execution, while requiring one
amethyst shard held in the formed well's catalyst inventories:

```json
{
  "type": "molecularmanipulator:matter_fabrication",
  "ingredients": [{"ingredient": {"item": "minecraft:iron_ingot"}, "count": 2}],
  "catalysts": [{"ingredient": {"item": "minecraft:amethyst_shard"}, "count": 1}],
  "results": [{"id": "minecraft:iron_block", "count": 1}],
  "processing_time": 200,
  "ae_per_tick": 64,
  "requires_research": false
}
```

Each assembly has 36 catalyst slots. Loaded assemblies in the same formed well
share these prerequisites. Batch/research parallelism multiplies consumed inputs
and outputs, but not catalyst counts. Catalysts stay in their inventories and
are checked when admitting/starting work. Missing catalysts prevent execution.

JEI encoding uses the server's recipe definition and selected matching item
alternatives. It combines every consumed item, fluid and generic AE input, and
every output including byproducts. Catalysts are omitted from the encoded inputs.
Upload requires an active encoding terminal, one extractable blank pattern and
an available assembly in a loaded, formed well belonging to the player's team.
The blank comes from the terminal first, then its connected ME network if the
local slot is empty; one network blank is sufficient. Assembly names are display
labels, not destination identity. Additional ExtendedAE Plus automatic-provider
selection/upload hooks target 1.6.4.1 only and recognize renamed assemblies by
machine type and team. The server validates the current recipe, selected inputs
and target; client quantities cannot redefine a recipe. Upload helpers/payloads
are internal and are not public Java API.

## 5. Materials, admission and persistence

- Progress syncs every 5 ticks; selected research stock is sampled every 20 ticks.
  Preparation and admission share the current stock snapshot within one pass,
  then validate and extract the actual whole-round cost. Displayed stock is not
  a reservation. Only items in the controller's AE network are research supplies;
  player inventories and port buffers are excluded.
- Normal orders continuously prepare the next round. Shift orders sum the actual costs of every remaining round, including replacement ingredients, then reach the maximum in one run. Preparation snapshots its definition; maximum tasks use one research duration and its normal power per tick.
- Every 20 ticks, exact joint allocation compares stock and owned reserves. Desired quantities replace the queue and subtract remaining CPU deliveries. Multiple preparations share one stock scan and item index; ordinary items/tags visit candidate keys, while custom ingredients retain their predicate.
- Ordered output goes directly into the research cache. Other crafts consuming previously stocked base materials trigger automatic replenishment. Stopping preparation cancels only its own jobs and persists refunds. Ordered tasks reject stopping and pausing after admission.
- Native AE CPU links and remaining deliveries persist and reconnect on world reload. Portable controllers queue unfinished needs and cancel old links before being placed again.
- All materials must be present before a round starts. Overlapping item/tag
  requirements are solved as one joint allocation, so a single stock is never
  counted twice and declaration order does not matter.
- If the provider changes between validation and extraction, the round does not
  start and extracted materials are refunded. Refunds the network cannot accept
  yet are kept by the controller and retried; no new research is accepted until
  they are settled.
- The full cost is charged once at the start. Pausing, disconnecting, losing
  power, breaking the structure or building/dismantling then preserves both
  materials and progress, and resuming never charges the same round twice.
- Every prerequisite must reach its required completion count (default 1). If an
  in-progress follow-up research stops meeting its prerequisites after a command
  or config change, it keeps its materials and progress and waits.
- One round per research runs at a time; different branches use independent
  timers and power and can run alongside normal production on the same network.
- Started work stores a snapshot of materials, round, depth table, duration and
  power. Reloading definitions does not re-charge it. Completion counts are saved
  per research ID, while bonuses and unlock lists are computed from the current
  definitions. Shortening a depth table keeps the stored count and recomputes
  bonuses from the new final entry.
- Legacy saves migrate a "completed" flag to one completion; partially supplied
  legacy tasks are paused and top up the unpaid part on resume, keeping progress
  and round.
- Controller NBT, normal drops and the AE dismantle item all preserve completion
  counts, active tasks and pending refunds. A memory card does not copy them.
  Tasks or refunds that cannot be decoded stay stored and recover after a
  compatible reload.

## 6. Built-in research and unlock chain

The default tree has six tiers and ten main researches, plus Exotic Matter and machine integrations.
AdvancedAE is optional to load the mod, but the default Quantum Materials, Computation,
Event Horizon, Stellar and Transfinite branches require it. Without that mod, those
research definitions are unavailable, rather than an alternative route to the same devices.

| ID (prefix `molecularmanipulator:research/`) | Tier | Parents (one completion unless specified) | Time / power | Unlocks |
| --- | --- | --- | --- | --- |
| `ae_foundation` | I | None | 600tick / 256AE/t | AE processing, assembly, auto crafter |
| `star_marrow_materials` | II | Foundation | 900 / 512 | Five materials and both advanced anchors |
| `star_marrow_crystallization` | II | Star Marrow | 1200 / 512 | Signal/water proliferation with retained rock |
| `eae_materials` | II | Foundation | 900 / 512 | 14 curated EAE material processes |
| `quantum_materials` | II | EAE | 1200 / 1024 | 13 curated AAE material processes |
| `sequence_array` | III | EAE, Star Marrow | 1200 / 1024 | Sequence/rewrite parts, storage/parallel matrices |
| `omni_computation` | III | Quantum, Star Marrow | 1800 / 2048 | Computation structure, Omniscient matrix |
| `event_horizon` | IV | Sequence, Computation | 1800 / 4096 | Black/white holes, nine hub structures |
| `stellar_fabrication` | V | Horizon | 2400 / 8192 | Supernova, hub controller, resource core |
| `transfinite_fabrication` | VI | Stellar | 3600 / 16384 | Transfinite Compute Nexus |
| `nomai_materials` | Special 0 | Foundation | 600 / 256 | Ghost Matter, Gravity Crystal |
| `machine/<key>` | Post-III branch | Sequence≥3 AND Computation≥3 | Default600 / 512 | Imported machine processes |

There is no separate `terminal_fabrication` well research. The auto crafter belongs to Foundation.
Star research is a separate team-permanent permission: the only default node is Single-Block Integration / 单方块集成
(`molecularmanipulator:material`, legacy bit 1). It requires the core's actual L3,
`information_cost: 0` and `prerequisites: []`, but still needs a manual unlock at a formed,
online engine bound to the player's team.

The sole bundled lock, `molecularmanipulator:recipe_locks/material`, targets only
`molecularmanipulator:molecular_manipulator` and `molecularmanipulator:transfinite_compute_nexus`.
Their well research remains required, and the nexus recipe still requires AdvancedAE.
Matrices, advanced anchors, the auto crafter and the Well, Sequence Array and Omni Computation
multiblock controllers have no default star-research gate; their well research and other recipe
conditions remain unchanged. See the [star node guide](star-research-nodes.zh-CN.md) and
[recipe-permission guide](recipe-permissions.zh-CN.md) (Chinese) for configuration and coverage.

All static research has nine rounds. Later rounds use explicit replacement costs in sustainable expansion materials, so original machines are not charged repeatedly. Research grants parallelism only; the engine supplies speed. Custom durations and depth lists remain configurable.

All built-in and custom machine imports automatically add `sequence_array: 3` and `omni_computation: 3`, preserving stricter numeric or `max` requirements. Three means total completions including the first. Admission, orders, preparation and active tasks read current prerequisites. Imported-recipe use also rechecks this minimum, including legacy completions, administrative completion assignments and alternative research owners.

Tier II curated recipes under `fabrication/research_materials/<modid>/<item>` are startup material processes, not whole-machine imports, so they remain available before tier III. Whole-machine imports retain `fabrication/import/machine/<key>/` IDs and individual research; reload rebuilds their indexes and gates.

The five Star Marrow materials use native well recipes, preserving dust/processor/alloy/component recipe IDs. Old cradle board conversion and machine/workbench production routes are removed. The crystallization recipe `fabrication/internal/star_marrow_crystal` consumes `ae_inputs: [{"#t":"molecularmanipulator:star_signal_flow","#":1024}]` and 8000mB water, with one retained Flawless rock, to produce 64 crystals at 400tick/512AE/t. It requires a Pattern Assembly and does not generate signal. Survival Star Signal Flow
is produced separately by the Star Sea Calculation Mainframe; it is not a well-research reward.

`star_signal_storage_component` is a researched well process yielding eight
components; `star_signal_storage_cell` is the finished storage cell. The cell remains
an ordered `minecraft:crafting_shaped` recipe with rows `GDG / DCD / IPI`:
`G = ae2:quartz_vibrant_glass`, `D = star_marrow_crystal_dust`,
`C = star_signal_storage_component`, `I = star_marrow_alloy_ingot`,
`P = star_chart_processor` (unqualified ingredient IDs use `molecularmanipulator`).

Event Horizon's first round costs 64 Sequence crystals, 64 Computation pylons, 32 of each matrix and 1024 AE Singularities. Supernova and hub cores belong to Stellar; the nexus belongs to Transfinite. Black/white hole recipes retain their material costs and 400-tick fabrication times.

Legacy progress maps once: Foundation→Star Marrow/EAE/Quantum, Computation→Transfinite, Horizon→Stellar. It does not grant Sequence completions or crystallization. Paid tasks retain terms and wait for current parents; completing old tasks also migrates their split permissions. Retired board batches return original crystals. Existing inventory and structures remain valid; matrix production uses current owners.

## 7. Batching, pattern lookup and known limits

Manual port crafting and pattern assemblies share the same research bonuses.
Manual item buffers compute the round's batch size from live stock, output space
and power; long amounts are never packed into ordinary ItemStacks.

Pattern assemblies implement the public `OmniBatchCraftingProvider` API and
support the long batches of this mod's computation system. Inputs accept every
registered AEKey type, and extra resources are declared through `ae_inputs`.
Inputs and outputs are stored persistently as AE keys with long amounts, and the
work is proportional to the number of distinct materials, not to the craft count.
Ordinary single-craft AE delivery uses the same persistent batch and can merge
same-recipe delivery before processing starts.

Assemblies own their input, output and refund buffers. Products and pending
refunds are written back to the assembly's ME network; when the network cannot
accept them they stay in the assembly rather than being pushed to a manual output
port. Blocked outputs or a save/reload never discard accepted materials, and
removing one block does not collect other assemblies' inventories.

Pattern lookup indexes the complete output map including amounts, and reuses
the cached research definitions and recipe-ownership index. Candidates with the
same output keep RecipeManager order, and every input is validated again per
attempt. The index rebuilds automatically when the recipe manager's snapshot is
replaced, which covers data-pack reloads and `replaceRecipes`. Completion counts
are read live from each controller, so unlocks, revocations and save restores
never inherit another controller's or a stale state.

Actual batch size is bounded by parallelism, materials, power, output acceptance
and each AEKey's long capacity. For example,
when one craft outputs 16 of the same item, a single batch is capped at
`Long.MAX_VALUE / 16`. Power is charged as the recipe's per-tick draw times the
batch's craft count.

Batch admission rejects multiplication or per-key buffer overflow without consuming
inputs. Persistent batch buffers retain long-valued amounts across NBT saves.

Started work keeps its materials, outputs and power snapshot. Its duration is
recomputed from the current recipe and the controller's grid benefits, including
engine level and overclocking changes, while completed progress is rescaled to
keep the same proportion (rounded down to whole ticks). Old batches saved with
`duration = 1` are recalculated too. Manual work also saves its duration for
rescaling after reload; legacy manual saves without that field keep their elapsed
ticks, bounded below completion. Queued work stores material ownership and the
recipe ID and rechecks the current recipe, permission and profile before starting.
Work that cannot proceed retains its materials inside the assembly.

### 7.1 Known limitations in 21.3.0.1

- Overlapping alternatives with identical outputs can match a different recipe
  while a batch is split, changing its time and power.
- A reload that introduces an earlier matching recipe can leave an existing queue
  waiting even though its original recipe still exists.
- A queue without a pattern is refunded when the pattern definition disappears,
  not when the matched recipe changes.

Output indexing and API admission leave these limitations unresolved.

## 8. JEI and ports

In JEI's recipe view, each well recipe shows the research tier and name that
grants it at the top. Hover to see the full research conditions; a recipe granted
by several researches lists all of them. Recipes without research show
"Basic recipe · No research", and recipes whose research is not configured show
"Research not configured". The time and power shown at the bottom are base
values; research parallelism and engine speed are applied by the controller.

| Port | Behaviour |
| --- | --- |
| Item input port, fluid input port | With a bound controller and an online AE grid, "Return all to AE" sends this port's buffer back to that network; what cannot be accepted stays. |
| Item output port, fluid output port | "Auto output" is off by default; when enabled it moves contents to adjacent containers in the selected directions every 5 ticks, deducting only what was actually accepted. |
| Output directions | Up, down, north, south, west and east toggle independently, and all six start off. Enabled sides use AE2's highlighted (light-blue) button sprite and are drawn with brackets; hovering tints the button mint, and several sides can be active at once. Each button shows the adjacent block's icon and its name on hover, updates after the neighbour changes, uses world directions, and connects to the neighbouring container's face that points at this port. |
| Pattern assembly | Shows AE connection state and occupied pattern count; supports naming, and an unsaved name survives window resizing. |

Buffer capacity is 16 item slots (4×4) and four independent fluid tanks of
2,147,483,647 mB each; incoming fluid merges into matching tanks before using
empty ones.

All four fluid buffer slots on the fluid input and output ports allow manual
transfer in both directions: right-click a slot with a fluid container in hand to
pour in, or with an empty container to fill it, one container per action. A full
bucket becomes an empty bucket and vice versa, and this is a real exchange even
in creative mode. Drawing water from a stack of empty buckets puts the filled
bucket into your inventory and decrements the held stack; if the result cannot fit
in the inventory, nothing happens. A full slot, an incompatible fluid or too
little fluid to fill a bucket never consumes the container or the fluid. The
original left-click behaviour (fill at an input port, take from an output port)
is unchanged.

Automatic output transfer does not require the interface to stay open. With no
direction selected it stops, and unloaded neighbouring chunks are not force
loaded. Items and fluids are only sent to adjacent containers, never dropped into
the world. Saved direction settings are not reset when defaults change, and the
output switch and directions are saved with the world, the AE dismantle settings
and the port items packed by a controller batch dismantle. Breaking a port
normally only preserves them while it still holds cached materials; breaking an
empty port drops a plain block and the auto-output and direction settings are
lost.

## 9. Administration commands

Requires cheats or permission level 2. Without coordinates, look at a Matter
Fabrication Well controller within 16 blocks; alternatively append `x y z`,
including `~` relative coordinates. The server console must specify coordinates,
and the target chunk must be loaded. Commands act only on that controller, do not
require a formed structure or an online AE grid, and never charge materials.

| Command | Effect |
| --- | --- |
| `/matter_research unlock_all` | Sets every available research on the target controller to its maximum depth, including KubeJS-added ones. |
| `/matter_research complete <research ID> true` | Sets one research to its maximum depth. |
| `/matter_research complete <research ID> false` | Clears one research to zero completions. |
| `/matter_research set <research ID> <count>` | Sets one research's completion count; `0` clears it and values above the maximum are clamped. |

```mcfunction
/matter_research unlock_all
/matter_research set molecularmanipulator:research/ae_foundation 5
/matter_research complete molecularmanipulator:research/sequence_array true
/matter_research complete molecularmanipulator:research/sequence_array false
/matter_research set molecularmanipulator:research/omni_computation 999 100 64 200
```

Research IDs have tab completion. The count argument accepts 0 through
9223372036854775807 and the stored value is clamped to that research's current
`depths` length. Without AdvancedAE, Omni-Computation is absent from completion
and from `unlock_all`, and naming it directly reports it as unavailable.

Changing a research's state or count ends that research's active attempt. Already
spent materials are not refunded, and nothing extra is charged; `unlock_all` ends
the active attempts of everything it maxes out. Setting one research leaves other
branches' counts, tasks and invested materials alone. Revoking a prerequisite pauses
unfinished dependent research until its current requirements are met again; existing
completion records remain. Ordinary recipes retain their granted well permission,
but machine-import recipes continuously recheck the minimum of three completions
in each Tier III branch. Results are written to the controller's NBT and pushed to open menus.

## 10. Java API

```java
// Mutations must run on the owning server thread.
MatterResearchApi.start(controller, "molecularmanipulator:research/ae_foundation");
MatterResearchApi.setPaused(controller, "molecularmanipulator:research/sequence_array", true);
int rounds = MatterResearchApi.completionCount(controller, "molecularmanipulator:research/ae_foundation");
int effective = MatterResearchApi.setCompletionCount(controller, "molecularmanipulator:research/ae_foundation", 999L);
MatterResearchApi.setCompleted(controller, "molecularmanipulator:research/sequence_array", true);
MatterResearchApi.unlockAll(controller);
boolean unlocked = MatterResearchApi.isRecipeUnlocked(controller, "molecularmanipulator:molecular_center_controller");
var profile = MatterResearchApi.productionProfile(controller, fabricationRecipeHolder);
long parallel = profile.parallel();
int ticks = profile.ticks();
var definitions = MatterResearchApi.definitions(serverLevel);
```

The class is `com.atir.molecularmanipulator.research.MatterResearchApi`. Use
`compileOnly` against the mod JAR, install the mod separately at runtime, and do
not embed these classes. There is no numeric ABI negotiation for the research
API; the separate batch API stays at v1.

| Entry point | Contract |
| --- | --- |
| `definitions(Level)` | Available definitions sorted by `sort_order`, then ID; definitions whose `required_mods` are missing are filtered out. |
| `orderMissing(controller, resourceId, toMaximum)`, `stopPreparation(controller, resourceId)` | Server owner-thread operations returning whether the request was accepted. Ordering continuously prepares materials; `true` targets all remaining rounds. Stop accepts preparation only and returns `false` once research starts. |
| `start(controller, id)`, `setPaused(controller, id, paused)` | Return whether the operation was accepted. `start` resumes an existing task instead of failing, and returns `false` for an unknown, unavailable, maxed-out, refund-blocked or prerequisite-blocked research. Ordered tasks reject `setPaused`; for ordinary manual tasks, `setPaused(..., false)` re-checks the structure, grid, refunds and prerequisites and pays any unpaid part, so it can return `false`. |
| `completionCount`, `isCompleted` | Query completions; `isCompleted` means at least one completion, not maximum depth. |
| `canUseRecipe`, `isRecipeUnlocked` | Check this well's research plus global recipe locks and, for imports, the source recipe ID and dual-Tier-III minimum. Other executors must call the hook to enforce these rules. `canUseRecipe` takes a `RecipeHolder<MatterFabricationRecipe>`. |
| `productionProfile` | `(parallel, ticks)`: highest parallel limit among completed owner branches; unowned, ungated recipes inherit completed `ae_foundation` parallelism, otherwise `1`. Ticks come from the current recipe base duration and `StarComputationNetwork.forGrid(controller.getMainNode().getGrid()).processingTicks(baseTicks)`, independently of research depth. |
| `setCompletionCount`, `setCompleted`, `unlockAll` | Administrative mutations that clamp to the current maximum depth and end affected active attempts; reserved preparation materials are returned, while started research costs remain spent. Negatives throw; the count is `int` and `unlockAll` returns the number of definitions it processed. These methods do not check permissions; the caller must. |
| `prerequisitesMet`, `requiredPrerequisiteLevel` | `prerequisitesMet` returns `false` if a prerequisite is missing from the supplied `available` list; pass `definitions(level)`. `requiredPrerequisiteLevel` returns an integer, defaulting to one completion. A Java map value of `0` requests the parent's maximum depth, matching the data-pack `"max"`. |

Mutations throw `IllegalStateException` when called off the owning server thread;
live queries should also run on the owning server thread unless the caller holds an immutable snapshot. Complete progress can be read on the server thread with
`controller.getResearch().save(serverLevel.registryAccess())`.

Java mods can build modified definitions through the copy API:

```java
var parent = ResourceLocation.parse("molecularmanipulator:research/ae_foundation");
var three = existingResearch.withPrerequisiteLevels(Map.of(parent, 3));
var full = existingResearch.withPrerequisiteLevels(Map.of(parent, 0)); // Java 0 == "max" in data
int required = MatterResearchApi.requiredPrerequisiteLevel(three, parentRecipeHolder);
boolean eligible = MatterResearchApi.prerequisitesMet(three,
        MatterResearchApi.definitions(serverLevel), controller.getResearch()::completionCount);
```

`withPrerequisiteLevels` returns a new definition and leaves the old one and all
existing research progress untouched. It replaces the level map without merging it. The
returned definition must go through the normal recipe registration or replacement
path. The 9- and 10-argument constructors remain available and default to one
required completion per prerequisite; the full canonical constructor takes
`Map<ResourceLocation, Integer> prerequisiteLevels` as its last parameter. The
Java map accepts positive counts or `0` for maximum, while JSON and KubeJS accept
a positive integer or the string `"max"` and reject `0` and negatives.

Other recipe executors must call the permission and production-parameter hooks
themselves. `productionProfile` computes batch and timing limits; it is not a permission
check, material reservation or guarantee that the network can supply the batch.
