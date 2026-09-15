# Matter Fabrication Well: Recipes and Research API (Forge)

Current for **2.0.3-forge**.
Target: Minecraft **1.20.1** / Forge **47.4.20+**, Java **17**, AE2 **15.4.10+**, and
the required prerequisite AppliedEnhancements **1.0.6-forge**. The Mod ID stays
`molecularmanipulator`.

Other languages: [中文版](matter-research-api.zh-CN.md).
See also the [API index](README.md) and the separate
[Omni Batch Provider API v1](omni-batch-provider-api.md).

This page covers the two recipe types that make up the well's content:

| Recipe type | Purpose |
| --- | --- |
| `molecularmanipulator:matter_fabrication` | What a Matter Fabrication Well can produce. |
| `molecularmanipulator:matter_research` | Research that unlocks those recipes and adds production bonuses. |

Both are ordinary data-pack recipes under `data/<namespace>/recipes/`. KubeJS adds
or replaces them with `ServerEvents.recipes` and `event.custom`; no extra plugin
is required. Research progress belongs to each well controller, not to the player
and not to a global network.

### 1.20.1 platform rules

This is the Forge port, not the NeoForge 1.21.1 build. When writing data packs,
KubeJS scripts or Java integrations for this branch:

- Recipe directories are `recipes/` (plural); the NeoForge build uses `recipe/`.
- Optional-mod gates use Forge `conditions`, for example
  `"conditions": [{"type": "forge:mod_loaded", "modid": "advanced_ae"}]`; recipes
  whose items a given build may not ship also use `{"type": "forge:item_exists", "item": ...}`.
- Item data uses NBT (`nbt`) rather than 1.21.1 data components.
- Generic AEKey inputs are serialised by this mod's `ForgeRecipeCodecs.GENERIC_STACK`
  bridge, because AE2 15 has no `GenericStack.CODEC` (see §4.1).
- The research and fabrication recipe classes are passed **directly**; there is no
  `RecipeHolder` wrapper. Create resource IDs with `new ResourceLocation("namespace:path")`.
- Recompile Java integrations against this branch's JAR instead of loading a
  NeoForge 1.21.1 integration.

---

## 1. Quick start: custom well recipes behind research

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
      {material_multiplier: '3', parallel: '1024', speed_multiplier: '8'},
      {
        ingredients: [
          {ingredient: {item: 'ae2:fluix_crystal'}, count: '512'},
          {ingredient: {item: 'minecraft:diamond'}, count: '64'}
        ],
        parallel: '9223372036854775807',
        processing_ticks: 1
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

With `requires_research: true` the recipe is locked until **any** research that
lists it in `unlocks` has at least one completion — here `kubejs:stone_research`.

### 1.1 Locking a recipe behind a deeper research level

Recipe permission is binary: one completion opens the recipe, and there is no
per-recipe level field. To require a specific depth — for example **9/9 on
Omni-Computation** — put the requirement on a *gate* research and unlock the
recipe from that gate instead of from the built-in research:

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

- Levels count **completions**, not the display-only `stage` number on each research.
- A numeric level is never lowered automatically. If it exceeds the prerequisite's
  `depths` length, the gate can never be started; keep it within range.
- `prerequisite_levels` keys are merged into `prerequisites`, so writing only the
  map still establishes the dependency; prerequisites not listed in the map
  default to one completion.

---

## 2. Research definition: `molecularmanipulator:matter_research`

Location: `data/<namespace>/recipes/<path>.json`.

| Field | Meaning |
| --- | --- |
| `title` | Display name or translation key. Required, must not be blank. |
| `stage` | Displayed tier, default `1`. Ordering and gating come from the prerequisite list, not from this number. |
| `sort_order` | Navigation order, default `0`. Definitions are sorted by `sort_order`, then by ID. |
| `prerequisites` | List of research IDs; each defaults to one required completion. Self-references and cycles are invalid. |
| `prerequisite_levels` | Optional map `{research ID: required completions or "max"}` overriding individual thresholds. Keys are added to `prerequisites`. |
| `ingredients` | Base round cost. Required. Each entry is `{ingredient: {item: ...} or {tag: ...}, count: positive long}`. Item ingredients only — research cannot charge fluids or generic AE keys. |
| `duration` | Ticks per round, at least `1`, default `1200`. |
| `ae_per_tick` | Power drawn while researching, finite and non-negative, default `256`. |
| `required_mods` | Mod IDs that must all be loaded for the definition to appear and run. |
| `unlocks` | Full **recipe IDs** (not item IDs) that this research unlocks and boosts. |
| `depths` | Per-round cost and total benefit after each completion. The array length is the number of rounds; omitting it uses the default nine-round table in §3. |

### 2.1 Depth entries

Each `depths` element:

| Field | Meaning |
| --- | --- |
| `material_multiplier` | Round cost = base `ingredients` × this, default `1`. |
| `ingredients` | Optional. When present it **fully replaces** the round's materials and the multiplier is ignored; `[]` means a free round. |
| `parallel` | Total parallel limit after this round. Required positive long. |
| `speed_multiplier` | Base recipe time is divided by this and rounded up, default `1`. |
| `processing_ticks` | When greater than `0`, a fixed processing time that takes priority over `speed_multiplier`; `0` or omitted uses the multiplier. |

### 2.2 Long values and `2^53 - 1`

Every long field (`count`, `material_multiplier`, `parallel`, `speed_multiplier`)
accepts a decimal string, and quoting is recommended. Above JavaScript's exact
integer limit `2^53 - 1` a string is **mandatory** — write
`'9223372036854775807'`, never a float or `'9.22E18'`. Nothing detects a
precision loss for you; a wrong JavaScript number is silently wrong. Costs whose
multiplication exceeds the long range refuse to start instead of going negative
or becoming free.

---

## 3. Default nine-round progression

Used when `depths` is omitted, and by all three built-in researches. Costs are
charged per round and are not the difference from the previous round. `parallel`
is how many complete recipe executions a single craft may run.

| Completions | Round cost / base cost | Parallel limit after | Processing time after |
| --- | --- | --- | --- |
| 1 | ×1 | 1 | base time |
| 2 | ×2 | 256 | base time ÷ 2, rounded up |
| 3 | ×4 | 65,536 | base time ÷ 4, rounded up |
| 4 | ×8 | 16,777,216 | base time ÷ 8, rounded up |
| 5 | ×16 | 4,294,967,296 | base time ÷ 16, rounded up |
| 6 | ×32 | 1,099,511,627,776 | base time ÷ 32, rounded up |
| 7 | ×64 | 281,474,976,710,656 | base time ÷ 64, rounded up |
| 8 | ×128 | 72,057,594,037,927,936 | base time ÷ 128, rounded up |
| 9 | ×256 | 9,223,372,036,854,775,807 (≈9.22E18) | fixed 1 tick |

Processing time never drops below one tick. Parallelism and speed apply only to
the well recipes listed in that research's `unlocks`; they never shorten research
itself. When several researches unlock the same recipe, the **highest** parallel
limit and the **shortest** time win — bonuses are combined with max/min, not
multiplied.

---

## 4. Well recipe: `molecularmanipulator:matter_fabrication`

| Field | Meaning |
| --- | --- |
| `ingredients` | Counted item inputs, each `{ingredient: {item: ...} or {tag: ...}, count: 1-64}`. Defaults to empty. |
| `results` | Up to **2** item outputs as `{id: ..., count: ...}`. |
| `fluid_input` | Optional fluid input `{id: ..., amount: ...}` in mB. Does not count toward the nine-input limit. |
| `fluid_result` | Optional single fluid output, same format. |
| `ae_inputs` | Up to **9** generic AEKey inputs; see §4.1. |
| `processing_time` | Base ticks, default `200`; values below `1` are raised to `1`. |
| `ae_per_tick` | Base power, default `64.0`; negative values are raised to `0`. |
| `requires_research` | Default `false`. See §4.2. |

Load-time error conditions: `ingredients` + `ae_inputs` may not exceed **9**;
at least one of `ingredients`, `fluid_input` or `ae_inputs` must be present; every
`ae_inputs` amount must be a positive integer; and at least one item or fluid
result is required. Unlike research definitions, a malformed well recipe fails to
load instead of being rejected at craft time.

### 4.1 Generic inputs (`ae_inputs`)

`ae_inputs` accepts every registered AEKey type, which is how gases, chemicals
and other addon resources are supplied. On this branch the entries are decoded by
`ForgeRecipeCodecs.GENERIC_STACK`, which keeps the mod's `#t` / `#` recipe fields
and translates them to AE2 15's native key tag (`#c`) internally.

| Field | Meaning |
| --- | --- |
| `#t` | AEKey type ID. AE2 items use `ae2:i`, AE2 fluids use `ae2:f`; addons register their own. |
| `#` | Raw amount per craft, a positive integer from 1 through 9223372036854775807. Item types count items, fluid types count mB. |
| `key_nbt` | Optional SNBT string holding the complete native key payload, including `id`. This is the form the mod itself writes, and it preserves NBT numeric widths and array types for arbitrary addon keys. |
| `id`, `tag`, other native fields | Decoded by the selected key type. Use that addon's native NBT fields rather than assuming the item/fluid layout. |

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

A lossless form for addon keys uses the encoder's own output instead:

```json
{"#t": "ae2:f", "#": 1000, "key_nbt": "{id:\"minecraft:water\"}"}
```

The amount must be a JSON number: `#` is parsed by the bridge's exact integer
parser, not by the decimal-string codec used for research fields, so a quoted
`"3000000000"` is rejected. For values beyond JavaScript's exact integer range,
write an integer literal in a data-pack JSON file or build the stack in Java with
`new GenericStack(key, amount)`; do not pass it through a JavaScript number first.
Malformed `ae_inputs` reject the whole recipe — they never silently become an
empty cost list.

Generic keys match exactly, including their NBT, while ordinary `ingredients`
keep their Ingredient/tag matching. Repeated or overlapping requirements are
additive — the same stock is never counted for two different requirements.

Recipes containing `ae_inputs` must be fed by a **pattern assembly**. Manual item
and fluid ports cannot supply generic AE buffers and reject such recipes.

Java constructor for recipes with generic inputs:

```java
new MatterFabricationRecipe(ingredients, results, fluidInput, fluidResult,
        aeInputs, processingTime, aePerTick, requiresResearch); // aeInputs is List<GenericStack>
```

The older constructors remain available and default `aeInputs` to an empty list.
Outputs still come from `results` and `fluid_result` (up to two item results and
one fluid result); there are no generic output fields.

### 4.2 Research permission

A well recipe is locked when a research lists it in `unlocks`, or when
`requires_research` is `true`. It becomes usable once any owning research has at
least one completion; several owners mean any one of them is enough.
`requires_research: true` stays locked when no research grants it, when the
granting research is removed, or when its required mod is missing. A recipe whose
granting research is not currently available (for example AdvancedAE is absent)
is locked rather than silently opened.

Recipe permission applies to the Matter Fabrication Well. It does not globally
block other machines, and already-built multiblocks keep working.

---

## 5. Materials, admission and persistence

- The interface polls the selected research's live AE stock every 5 server ticks.
  Starting a round re-reads the storage providers, revalidates and extracts the
  whole round cost. Only items in the controller's AE network are used — player
  inventories and port buffers are not research supplies.
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

---

## 6. Built-in research and unlock chain

| Research ID (prefix `molecularmanipulator:research/`) | Time per round | Research power | First-completion unlocks |
| --- | --- | --- | --- |
| `ae_foundation` | 600 ticks / 30 s | 256 AE/t | 7 AE material recipes, 27 stage-two material and intermediate recipes, pattern assembly |
| `sequence_array` | 600 ticks / 30 s | 512 AE/t | 6 Sequence Array components, Molecular Sequence Rewrite Array, Assembler Matrix Sequence Rewrite Core |
| `omni_computation` | 600 ticks / 30 s | 1024 AE/t | 10 Omni-Computation components and the Transfinite Compute Nexus |

The 30 seconds applies to the first unlock and to every later deep-research round
of the built-in researches. Custom `duration` values stay free in ticks and still
default to 1200; a started round keeps the duration snapshot from its start.

Both stage-two branches require **one** `ae_foundation` completion by default.
The 27 stage-one additions are listed under the ID pattern
`molecularmanipulator:fabrication/research_materials/<modid>/<item>`. The two
stage-two researches consume 17 distinct material types: 8 AdvancedAE, 7
ExtendedAE (`expatternprovider`) and 2 vanilla AE2 items. The AE2 pair is covered
by the seven `fabrication/ae/` recipes, so 15 of the 17 live in the
research-materials set, which also supplies the related intermediates and quantum
infusion recipes. They open on the first tier-one completion and receive tier-one
deep-research bonuses.

Without AdvancedAE the Omni-Computation branch and its material recipes do not
load, and the interface lists nothing for them. Those definitions and recipes are
gated with Forge `conditions` on `advanced_ae`; the research list is filtered by
`required_mods`, so an unavailable research is absent from the interface and from
command completion. The Sequence Array branch is guarded the same way with
`required_mods: ["expatternprovider"]` — on Forge 1.20.1, ExtendedAE's mod ID is
`expatternprovider`, not `extendedae`.

The Molecular Sequence Rewrite Array (`molecular_manipulator`) and the Assembler
Matrix Sequence Rewrite Core (`assembler_matrix_molecular_core`) live in the
stage-two Sequence Array branch: their crafting recipes are well recipes using
the same materials and counts, at 400 ticks and 512 AE/t base. The branch's first
completion unlocks both, and its deep research gives them the same speed and
parallel bonuses. Already-built machines keep working.

The Transfinite Compute Nexus recipe, `molecularmanipulator:transfinite_compute_nexus`,
is unlocked by the Omni-Computation branch at 1200 ticks and 4096 AE/t before
production bonuses, inherits that branch's well bonuses, and is itself gated on
AdvancedAE. Once placed, the block's idle draw is configured separately through
`transfinite_compute_nexus.idle_power`, defaulting to 16384 AE/t; that setting
covers only the single-block nexus, while the Omni-Computation Core multiblock
keeps its fixed 8192 AE/t idle draw.

The well controller, the five structural blocks and the four item/fluid ports are
crafted from vanilla AE materials and are not gated by research. Pattern
assemblies are crafted inside the well and need the first tier-one completion.

Service blocks link to the controller on the **24 positions in front of the well**
and the **20 central platform positions** (five on each of the four collar
segments), 44 bays in total. The nine former outer positions are no longer
accepted. Pattern assemblies and the four item/fluid ports share the same bay set.
The blocks themselves can be placed anywhere, but only a bay position links to the
controller; the placement preview highlights the legal bays.

---

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
port. The controller's legacy batch compatibility path and manual port crafting
use their own buffers. Blocked outputs or a save/reload never discard accepted
materials, and removing one block does not collect other assemblies' inventories.

Pattern lookup indexes the **complete output map including amounts**, and reuses
the cached research definitions and recipe-ownership index. Candidates with the
same output keep RecipeManager order, and every input is validated again per
attempt. The index rebuilds automatically when the recipe manager's snapshot is
replaced, which covers data-pack reloads and `replaceRecipes`. Completion counts
are read live from each controller, so unlocks, revocations and save restores
never inherit another controller's or a stale state.

Parallel is a limit, not a guarantee: the actual batch still depends on
materials, power, output acceptance and each AEKey's long capacity. For example,
when one craft outputs 16 of the same item, a single batch is capped at
`Long.MAX_VALUE / 16`. Power is charged as the recipe's per-tick draw times the
batch's craft count.

Native tests cover the numeric side through the public API: the pattern assembly
accepts and commits a 3,000,000,000-craft generic batch and keeps those amounts
long-valued across NBT round trips, while the Sequence Rewrite Array and
Assembler Matrix providers prepare `Long.MAX_VALUE / 4` crafts and produce the
exact output count (3 billion crafts / 12 billion outputs in the powered fixture),
rejecting multiplication or buffer overflow without consuming inputs. That
verifies capacity, not that any pack has free materials or power.

Started work keeps its processing snapshot. New or not-yet-started work uses the
current recipe permissions and bonuses; queued work stores material ownership and
the recipe ID, so unstarted batches re-check the current recipe, permission and
profile instead of reusing old figures. Work that can no longer proceed keeps its
materials inside the assembly and can be returned as pending input.

### 7.1 Known limitations in 2.0.3

- Overlapping alternatives with identical outputs can match a different recipe
  while a batch is split, changing its time and power.
- A reload that introduces an earlier matching recipe can leave an existing queue
  waiting even though its original recipe still exists.
- A queue is refunded when its pattern definition disappears from the assembly,
  not when the matched recipe changes.

Neither output indexing nor API admission should be treated as a fix for these.

---

## 8. JEI and ports

JEI labels each well recipe at the top with the research tier and name that
grants it; hovering shows the full research conditions, and a recipe granted by
several researches lists all of them. Recipes without research show
"Basic recipe · No research", and recipes whose research is not configured show
"Research not configured". The time and power shown at the bottom are base
values; deep-research bonuses are applied by the controller.

| Port | Behaviour |
| --- | --- |
| Item input port, fluid input port | With a bound controller and an online AE grid, "Return all to AE" sends this port's buffer back to that network; what cannot be accepted stays. |
| Item output port, fluid output port | "Auto output" is off by default; when enabled it moves contents to adjacent containers in the selected directions every 5 ticks, deducting only what was actually accepted. |
| Output directions | Up, down, north, south, west and east toggle independently, and all six start off. An enabled side is drawn as a highlighted light-blue button with brackets; hovering tints it and several sides can be active at once. Each button shows the adjacent block's icon and its name on hover, updates after the neighbour changes, uses world directions, and connects to the neighbouring container's face that points at this port. |
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

Generic `ae_inputs` are also displayed in JEI and in the in-game GuideME pages.

---

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
branches' counts, tasks and invested materials alone. Reverting tier one pauses
unfinished tier-two research until the prerequisite is met again (one completion
by default), while tier-two completion records and granted production permissions
are kept. Results are written to the controller's NBT and pushed to open menus.

---

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

List<MatterResearchRecipe> definitions = MatterResearchApi.definitions(serverLevel);
var profile = MatterResearchApi.productionProfile(controller, fabricationRecipe);
long parallel = profile.parallel();
int ticks = profile.ticks();
```

The class is `com.atir.molecularmanipulator.research.MatterResearchApi`. Use
`compileOnly` against this branch's mod JAR, install the mod separately at
runtime, and do not embed these classes. There is no numeric ABI negotiation for
the research API; the separate batch API stays at v1.

| Entry point | Contract |
| --- | --- |
| `definitions(Level)` | Returns `List<MatterResearchRecipe>` sorted by `sort_order`, then ID; definitions whose `required_mods` are missing are filtered out. |
| `start(controller, id)`, `setPaused(controller, id, paused)` | Return whether the operation was accepted. `start` resumes an existing task instead of failing, and returns `false` for an unknown, unavailable, maxed-out, refund-blocked or prerequisite-blocked research. `setPaused(..., false)` re-checks the structure, grid, refunds and prerequisites and pays any unpaid part, so it can return `false`. |
| `completionCount`, `isCompleted` | Query completions; `isCompleted` means at least one completion, not maximum depth. |
| `canUseRecipe`, `isRecipeUnlocked` | Permission checks for other recipe executors, which must call them to enforce their own rules. `canUseRecipe` takes a `MatterFabricationRecipe` directly. |
| `productionProfile` | `(parallel, ticks)` for the current completed branches of that recipe: highest parallel, shortest time; an unowned recipe returns parallel `1` and its raw processing time. |
| `setCompletionCount`, `setCompleted`, `unlockAll` | Administrative mutations that clamp to the current maximum depth and end the affected active attempts without refunding. Negatives throw; the count is `int` and `unlockAll` returns the number of definitions it processed. None of them checks permissions — the caller must. |
| `prerequisitesMet`, `requiredPrerequisiteLevel` | Default one completion per prerequisite, and `false` for a prerequisite missing from the `available` list passed in — pass `definitions(level)`. Java map value `0` is the data-pack `"max"`. |

Mutations throw `IllegalStateException` when called off the owning server thread;
read queries may be used from any thread. Complete progress can be read with
`controller.getResearch().save()`.

Java mods can build modified definitions through the copy API:

```java
var parent = new ResourceLocation("molecularmanipulator:research/ae_foundation");
var three = existingResearch.withPrerequisiteLevels(Map.of(parent, 3));
var full = existingResearch.withPrerequisiteLevels(Map.of(parent, 0)); // Java 0 == "max" in data
int required = MatterResearchApi.requiredPrerequisiteLevel(three, parentResearch);
boolean eligible = MatterResearchApi.prerequisitesMet(three,
        MatterResearchApi.definitions(serverLevel), controller.getResearch()::completionCount);
```

`withPrerequisiteLevels` returns a new definition and leaves the old one and all
player progress untouched; it replaces the level map rather than merging it. The
returned definition must go through the normal recipe registration or replacement
path. The 9- and 10-argument constructors remain available and default to one
required completion per prerequisite; the full constructor takes
`Map<ResourceLocation, Integer> prerequisiteLevels` as its last parameter. The
Java map accepts positive counts or `0` for maximum, while JSON and KubeJS accept
a positive integer or the string `"max"` and reject `0` and negatives.

On this branch recipe instances are not wrapped in `RecipeHolder`: pass the
`MatterResearchRecipe` or `MatterFabricationRecipe` object itself. Both classes
expose `id()` / `getId()`, and `value()` returns the recipe itself so shared code
keeps working.

Other recipe executors must call the permission and production-parameter hooks
themselves.

---

## 11. World visuals

Research world effects automatically cover stages added by third-party mods, data
packs and KubeJS without any recipe change. The three built-in stages use an
ice-cyan crystal lattice, a mint-teal array with gold accents and a violet-white
orbital star map; any other stage picks one of the four star maps (lattice, array,
orbital sphere, double helix) pseudo-randomly but stably from the stage ID,
controller position, dimension and research round — so a custom stage can also
land on one of the three built-in shapes. A round keeps the same look across
multiplayer clients, pause/resume and reloads, and rerolls for the next round,
including possibly the same map again.

At most four research star maps render per controller, preferring running tasks.
The cap only bounds visual cost and does not limit how many researches may run.
Paused research, unmet prerequisites, an unavailable definition, a broken
structure, an offline network or insufficient power dims the map and stops its
light pulses; crown arcs grow with the research round, capped at three, and a real
completion plays a short breakthrough burst. Progress travels through block
updates every 5 ticks, so it stays visible with the controller screen closed.
Command-based unlocks do not play the completion animation. The client-side
`visual.dynamic_effect_level` option (0, 1 or 2, default 2) controls effect
detail: `0` disables the star maps entirely, and they only render while the
client sees a formed structure. Existing research definitions and public API
signatures remain compatible.
