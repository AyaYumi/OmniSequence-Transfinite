# Star Orrery: upgrade rules and data-pack / KubeJS configuration

Use the engine's **Upgrade** page to supply anchors and build the next ring.
This reference starts with the normal player steps, then gives **modpack authors**
the schema and examples for changing upgrade costs and requirements. Players can
use the opening instructions and default table; the in-game **G** guide shows the
structure and controls. Installation and the first well are in the [player guide](../README.md).

Current for OmniSequence **21.3.0.0**, Minecraft **1.21.1 / NeoForge**, Java **21**.
The mod ID is `molecularmanipulator`.
简体中文：[天体仪升级规则](star-orrery-upgrades.zh-CN.md).
See also the [Matter Fabrication research API](matter-research-api.md).

The default upgrade path needs **only the corresponding Star Position Anchors**. Build the central
body and foundation, open **Upgrade**, prepare the anchors shown in the requirements, then click
**Auto-build L… rings** (L1–L3) or **Upgrade to L…** (L4–L9). The server places missing anchors in the
real structure. There is **no extra upgrade energy, quest, research, machine or node prerequisite**
in the defaults. Selecting a level is only a preview and spends nothing.

## How to upgrade

1. Complete the **27-block center** (1 core, 22 casing, 4 horizontal face-center ports) and
   **928-block foundation** (520 engraved, 408 gilded), using the structure preview. The center
   alone is not a working 3×3×3 engine. Building from L1 is recommended. L4–L9 certification
   must advance in order; a higher-level action can also fill missing inner rings.
2. Put the required anchors in your inventory. Missing inventory materials can come from the
   **same powered, channel-active ME grid**; a grid is not required when inventory materials suffice.
3. Hover a requirement for quantities, available supply and instructions. The list keeps short
   titles; scroll or drag its scrollbar to see every condition without clicking a row first.
4. Click the upgrade button. Correct anchors already at their required positions count toward
   the requirement and **are not consumed again**. Only missing anchors are taken, inventory first,
   then ME, and placed through the selected ring, including any damaged inner rings.
5. Keep the affected chunks loaded and placement positions clear. Occupied positions, unavailable
   chunks or denied placement permission block construction; clear the indicated problem and retry.
   The core and foundation are not built by this action. Completing one level **never starts the next**.

L1–L3 still activate automatically if you build their rings by hand, and their internal certification
ceiling remains 3; that ceiling does not grant unbuilt rings. L4–L9 require the explicit upgrade action
even if you prebuild all rings. Physical damage lowers usable benefits, not saved certification.

Upgrade declarations are ordinary server recipes of type **`molecularmanipulator:star_orrery_upgrade`**,
not crafting operations, and produce no items. **KubeJS is not required**: normal data packs and KubeJS
`ServerEvents.recipes` share the same schema. Modpacks may still add energy, research, machines,
nodes and optional FTB conditions, or set `auto_build: false` for hand-built rings and calibration.

## 1. Default L1–L9 anchor requirements

L4–L9 defaults live at `src/main/resources/data/molecularmanipulator/recipe/star_orrery_upgrade/l4.json`
through `l9.json`, with recipe IDs `molecularmanipulator:star_orrery_upgrade/l4` through `/l9`.
They use `auto_build: true`, `energy: 0`, `ae_per_tick: 1` and only an `anchors` leaf.
The positive rate is a schema requirement, **not an actual upgrade charge** when energy is zero.
L1–L3 have the same anchor-building UI through fixed built-in entries, not configurable recipes.

| Level | New ring anchors when earlier rings are complete | Cumulative Reference | Cumulative Quantum | Cumulative Terminal | Extra upgrade AE / quests / research |
| --- | --- | ---: | ---: | ---: | --- |
| L1 | 24 Reference | 24 | 0 | 0 | None |
| L2 | 32 Reference | 56 | 0 | 0 | None |
| L3 | 40 Reference | 96 | 0 | 0 | None |
| L4 | 48 Quantum | 96 | 48 | 0 | None |
| L5 | 56 Quantum | 96 | 104 | 0 | None |
| L6 | 64 Quantum | 96 | 168 | 0 | None |
| L7 | 72 Terminal | 96 | 168 | 72 | None |
| L8 | 80 Terminal | 96 | 168 | 152 | None |
| L9 | 88 Terminal | 96 | 168 | 240 | None |

The cumulative columns include correctly placed anchors. They are **not an additional inventory
payment** at each upgrade. If inner rings were damaged, supply only their missing anchors as well.
Anchor target IDs are `reference`, `quantum` and `terminal`, corresponding to the real
`molecularmanipulator:reference_star_position_anchor`, `quantum_star_position_anchor` and
`terminal_star_position_anchor` blocks/items. No fake anchors or virtual ring counts are granted.

Only L4–L9 declaration conditions, energy, draw limits and `auto_build` are configurable. The codec
accepts `level: 1..9`, but L1–L3 always use the fixed automatic entries. A custom rule cannot skip
certification levels. The examples below demonstrate **optional pack-authored gates**, not defaults.

## 2. Declaration schema

### 2.1 Recipe root

| Field | Requirement |
| --- | --- |
| `type` | Exactly `molecularmanipulator:star_orrery_upgrade`. |
| `level` | Required exact integer; codec range `1..9`, configurable certification levels `4..9`. |
| `energy` | Required finite, nonnegative JSON number in AE; `0` is allowed. |
| `ae_per_tick` | Required finite JSON number **>0**, in AE/t; must remain positive even when `energy: 0`. |
| `auto_build` | Optional boolean, default `false`. `true` places missing anchors from inventory first, then ME; `false` requires hand-built rings. |
| `conditions` | Required nonempty condition-tree object: either a leaf or a combination group. |

Here, `count` / `required` are **exact JSON integers in 1..2,147,483,647**, not the research API's long
strings; fractional counts are rejected. Do not use numeric FTB IDs or quote these count fields.

### 2.2 Combination groups and leaves

- Groups have `type: "all" | "any" | "at_least"` and a nonempty **`children`** array, not targets.
  `all` requires every direct child; `any` requires at least one; `at_least` requires at least
  `required` passing children. Children may themselves be groups or leaves.
- Leaves have `type: "ftb_quests" | "machines" | "research" | "nodes" | "anchors"` and a nonempty **`targets`**
  array, not children. Each target is `{ "id": "...", "count": 1, "title": "..." }`.
- A leaf's **`match`** defaults to `"all"`; `"any"` and `"at_least"` are also supported. It combines
  only that leaf's targets. `required` defaults to `1` and, with `at_least`, counts passing **target
  entries**, not total machines or total research completions.
- `required` must be within `1..children.length` or `1..targets.length`, including on `all` / `any`
  where it does not affect the result. A group's operator is its `type`, not its `match` field.
- `count` defaults to `1`: cumulative anchors, machine count or research completions. FTB and node checks only return
  `0/1`, so keep their `count: 1`. Setting `count: 2` is an impossible boolean target, not a task
  progress threshold of two.
- `same_machine` defaults to `true` and only affects `research`; `formed` defaults to `true` and only
  affects `machines`.
- Groups, leaves and individual targets may have a **`title`**, defaulting to an empty string. It is
  interpreted as a translation key, with untranslated text displayed verbatim. Either custom text
  or a language-file key works. A custom title replaces the default list title without changing the target ID or evaluation.
  The list shows titles and completion marks only. Hover for progress, instructions, missing requirements
  and how multiple targets are combined; internal IDs are not shown.
  Completion instructions retain the real in-game name even when a custom short title is used in the list. `title` is not a recipe-root naming field.
- Maximum condition depth is `32` (root depth `0`), with at most `1,024` condition nodes and targets
  combined. Unknown types, empty groups / targets and invalid thresholds are rejected.

Targets are independent predicates, not allocation or consumption requests. One controller can match
both its explicit ID and a containing tag. Repeated targets do not require distinct machines. To
require three machines, use one target with `count: 3`, not three copies of the target.

## 3. Evaluation boundaries of the five leaf types

### `anchors`: placed anchors and available materials

Target IDs are strictly `reference`, `quantum` and `terminal`; `count` is the cumulative physical
requirement. With `auto_build: true`, the upgrade-page preview counts correct target-prefix anchors
plus available inventory and same-grid ME materials, **capped at the slots this target can actually
build**. A custom `count` above that cap requires pre-existing higher physical rings; spare inventory
alone cannot satisfy it. Running upgrades recheck actual placed anchors only. With `auto_build: false`,
only placed anchors count, never inventory or ME stock. Correctly placed anchors are not charged again.
The leaf itself checks conditions; `auto_build: true` performs extraction and placement. Every missing
anchor still costs a real item, even if `count` is lowered or another `any` branch passes.
`auto_build: false` never places anchors. Other leaves keep their behavior and may be combined with this leaf.


### `machines`: controllers on the real grid

A target `id` is a **block registry ID** or a **block tag** prefixed with `#`, for example
`#kubejs:orrery_support_controllers`. It is not an item tag, BlockEntityType ID or JEI category.
Only loaded, nonremoved, active controller BlockEntities on the owning core's actual **`IGrid`** are
counted. Native AE hosts must expose their active main node; multiple exposed nodes belonging to one
controller count only once. Nearby blocks, same-team machines on another grid, proxy parts without a
machine BlockEntity, logical shared-storage networks, controller items and folded packages do not
substitute for a live machine. A working quantum connection may count across locations or dimensions
if it really joins the same `IGrid`. There is no additional machine-team filter.

For explicitly supported native multiblocks, `formed: true` calls their actual structure APIs. These
include the Star Orrery, Matter Fabrication Well, Sequence Array, Omni Computation, Assembler Matrix
Molecular Core, Singularity Hub and Star Sea Calculation Mainframe.
**Third-party machines have no universal formed contract.** For an unrecognized controller, even
`formed: true` proves only that it is a loaded, active controller candidate, not that its whole
multiblock is complete. The resolver does not guess a `formed` blockstate property. Third-party
targets must name real controller blocks / tags, not auxiliary service ports. Stronger guarantees
require an explicit integration. `formed: false` skips structure API calls but still requires a
loaded, active, same-grid controller; it does not accept disconnected loose blocks.

### `research`: same-well or cross-well, never summed

Only loaded, online, formed Matter Fabrication Wells on the real grid are eligible, excluding wells
being built or dismantled. Every target's **research definition must still exist** before reading each
well's `completionCount`. Saved counts cannot revive a deleted definition. Setting `formed: false`
does not relax research-well eligibility.

- `same_machine: true`: there must be **one well** for which the entire leaf's `targets` and `match`
  pass. Put research targets in **one leaf** to require a common well. Two separate leaves under an
  outer `all` can have different witness wells.
- `same_machine: false`: take the **maximum count per research target** across eligible wells, then
  apply `match`. Counts are not added.
- Example: well A has `(Sequence=3, Omni=0)`, well B has `(Sequence=0, Omni=2)`. Targets `(3,2)` fail
  same-well mode but pass cross-well mode. If both wells only have Sequence `1`, cross-well mode
  still cannot satisfy Sequence `≥2`.
- No research completions are consumed, and no well is permanently selected. A different well may
  take over if it satisfies the entire leaf. With no eligible well, historical progress elsewhere
  does not make the check pass.

### `nodes`: only the owning core's bound-team bits

This leaf accepts only the five legacy short IDs: `foundation`, `material`, `quantum`,
`terminal`, `transfinite`, not arbitrary custom research IDs. These are compatibility bits,
not five default research definitions. Only **Single-Block Integration / 单方块集成**
(`molecularmanipulator:material`, legacy bit 1) has a default definition: actual L3,
zero information cost, no prerequisites and a manual unlock. See the [star node guide](star-research-nodes.zh-CN.md)
(Chinese) for custom definitions and purchases.

The check reads **`core.getUnlockMask()`**, meaning the owning core's bound-team permanent record and
its existing migration bits. It **does not use the same-grid permission union from
`StarComputationNetwork.unlockMask(grid)`**, nor the current GUI viewer's team. Another team's engine
may contribute crafting permissions without satisfying this core's upgrade prerequisites. Checks do
not buy nodes or charge again; unknown node IDs fail.

### `ftb_quests`: optional, read-only Quest / Task checks

A single leaf may contain multiple Quest IDs and Task IDs. The actual object returned by the server
determines which kind of check is used. **Always use hexadecimal strings**, such as
`"1234567890ABCDEF"` or `"0x234567890ABCDEF0"`, never JavaScript numbers, decimal conversions or values
that have already passed through `Number`. IDs above `2^53-1` would otherwise lose precision.

The schema accepts an optional `0x` / `0X` prefix and 1–16 hexadecimal digits. Runtime also requires a
positive signed long: **`1..7FFFFFFFFFFFFFFF`**. `0`, `8000000000000000` and `FFFFFFFFFFFFFFFF` are
unavailable IDs. Unprefixed `"10"` still means hexadecimal `0x10`. Do not include whitespace.

- Ownership is always the **Star Orrery's bound team UUID**, not the player pressing a button or
  viewing the page. There is no viewer fallback and no creation of missing team progress.
- Quests use `TeamData.isCompleted(quest)`. Tasks compare that **Task's own** `getProgress(task)` with
  `task.getMaxProgress()` (which must have a valid positive maximum). Completing the parent Quest
  does not complete a Task, and parent progress is not substituted.
- Checks do not trigger detection, mark anything complete, submit items or claim rewards. A target
  tests completion, not an arbitrary custom threshold for partial task progress.
- Missing `ftbquests`, unready server / team data, deleted IDs, non-Quest / non-Task objects, missing
  dependencies or incompatible APIs yield **unavailable and not completed**, never an ignored gate.
  Under `any` / `at_least`, other genuinely passing branches may still meet the configured threshold;
  the unavailable FTB branch itself never counts as passing.

**FTB API boundary:** the reflection bridge targets the
[**FTB Quests v2101.1.38 API**](https://github.com/FTBTeam/FTB-Quests/tree/v2101.1.38/common/src/main/java/dev/ftb/mods/ftbquests/quest)
on Minecraft 1.21.1 / NeoForge. A release being listed for Minecraft 1.21.1 does not establish
compatibility with that API. Compatibility across all 2100 / 2101 releases, other loaders or
future versions is not guaranteed.

Reflection validates `ServerQuestFile.INSTANCE` / `server` / `isLoading()`, `getBase(long)`,
`getNullableTeamData(UUID)`, `getTeamId()`, `isCompleted(QuestObject)`, the long-returning
`getProgress(Task)` / `getMaxProgress()`, and Component titles. Only reflection metadata (including
failed discovery) is cached. Each check reads the current server, team, target, title and completion.
Recipe reloads do not freeze old progress. Restart after changing mods / APIs; `/reload` does not
rediscover an incompatible API.

## 4. Without KubeJS: ordinary data-pack overrides

Create `pack.mcmeta` under `<world>/datapacks/orrery_rules/`:

```json
{
  "pack": {
    "pack_format": 48,
    "description": "Star Orrery upgrade rules"
  }
}
```

For example, create `data/molecularmanipulator/recipe/star_orrery_upgrade/l4.json`:

```json
{
  "type": "molecularmanipulator:star_orrery_upgrade",
  "level": 4,
  "energy": 0,
  "ae_per_tick": 1,
  "auto_build": true,
  "conditions": {
    "type": "anchors",
    "match": "all",
    "targets": [
      { "id": "reference", "count": 96 },
      { "id": "quantum", "count": 48 }
    ]
  }
}
```

A higher-priority data pack overrides the built-in declaration using the **same namespace and
path / recipe ID**, then `/reload`. Repeat for L5–L9. Minecraft 1.21.1 uses the singular **`recipe`**
directory, not `recipes`. Adding the same level under another namespace while keeping the original
creates a conflict; it does not append conditions.

## 5. Complete KubeJS example A: custom research and energy gates for all six levels

Copy this into `kubejs/server_scripts/star_orrery_upgrades.js`. It is a complete usable script with no
FTB dependency; edit the table or condition-generation logic as needed. **Choose example A or B; do
not install both.**

```javascript
ServerEvents.recipes(event => {
  const upgradeType = 'molecularmanipulator:star_orrery_upgrade';
  event.remove({ type: upgradeType });

  const stages = [
    { level: 4, energy: 614400,   rate: 512,   sequence: 1, omni: 0 },
    { level: 5, energy: 1843200,  rate: 1024,  sequence: 2, omni: 0 },
    { level: 6, energy: 4915200,  rate: 2048,  sequence: 3, omni: 0 },
    { level: 7, energy: 9830400,  rate: 4096,  sequence: 3, omni: 1 },
    { level: 8, energy: 29491200, rate: 8192,  sequence: 3, omni: 2 },
    { level: 9, energy: 98304000, rate: 16384, sequence: 3, omni: 3 }
  ];

  stages.forEach(stage => {
    const research = [
      { id: 'molecularmanipulator:research/sequence_array', count: stage.sequence }
    ];
    if (stage.omni > 0) {
      research.push({ id: 'molecularmanipulator:research/omni_computation', count: stage.omni });
    }
    const children = [
      { type: 'research', same_machine: true, match: 'all', targets: research }
    ];
    if (stage.level >= 5) {
      const machines = [
        { id: 'molecularmanipulator:molecular_center_controller', count: 1 }
      ];
      if (stage.level >= 7) {
        machines.push({ id: 'molecularmanipulator:omni_computation_controller', count: 1 });
      }
      children.push({ type: 'machines', formed: true, match: 'all', targets: machines });
    }
    children.push({ type: 'nodes', match: 'all', targets: [{ id: 'material', count: 1 }] });

    event.custom({
      type: upgradeType,
      level: stage.level,
      auto_build: false,
      energy: stage.energy,
      ae_per_tick: stage.rate,
      conditions: { type: 'all', children: children }
    }).id('kubejs:star_orrery_upgrade/l' + stage.level);
  });
});
```

`event.remove({ type: ... })` removes every declaration of this type. Re-add **all six L4–L9 rules**
that you need. Re-adding only one leaves other high levels unconfigured, rather than falling back to
old defaults. L1–L3 are unaffected by this removal.

## 6. Complete KubeJS example B: multiple targets and nested groups, replacing only L7

This example starts with **all six built-in defaults still present**. It removes only default L7,
leaving the other five levels alone. The syntax is ready to copy, but FTB IDs are placeholders:
replace them with actual Quest / Task IDs in your pack. If you already customized L7, remove its
actual recipe ID as well so that exactly one L7 rule remains. These alternative routes demonstrate configuration, not default
balance.

```javascript
ServerEvents.recipes(event => {
  const upgradeType = 'molecularmanipulator:star_orrery_upgrade';
  event.remove({
    type: upgradeType,
    id: 'molecularmanipulator:star_orrery_upgrade/l7'
  });

  event.custom({
    type: upgradeType,
    level: 7,
    auto_build: false,
    energy: 9830400,
    ae_per_tick: 4096,
    conditions: {
      type: 'all',
      title: 'L7: modpack certification',
      children: [
        {
          type: 'nodes', match: 'all', title: 'Owning core team prerequisites',
          targets: [{ id: 'material' }]
        },
        {
          type: 'research', same_machine: true, match: 'all',
          title: 'Both targets must be met in one well',
          targets: [
            { id: 'molecularmanipulator:research/sequence_array', count: 3, title: 'Sequence research' },
            { id: 'molecularmanipulator:research/omni_computation', count: 1, title: 'Omni research' }
          ]
        },
        {
          type: 'at_least', required: 2, title: 'At least two of these three routes',
          children: [
            {
              type: 'ftb_quests', match: 'all', title: 'Complete both Quest and Task',
              targets: [
                { id: '1234567890ABCDEF', title: 'Main Quest (replace ID)' },
                { id: '0x234567890ABCDEF0', title: 'Child Task (replace ID)' }
              ]
            },
            {
              type: 'machines', formed: true, match: 'all', title: 'Same-grid machine group',
              targets: [
                { id: 'molecularmanipulator:molecular_center_controller', count: 2 },
                { id: 'molecularmanipulator:omni_computation_controller', count: 1 },
                { id: '#kubejs:orrery_support_controllers', count: 3, title: 'Support controller tag' }
              ]
            },
            {
              type: 'any', title: 'Extra tasks or deeper research',
              children: [
                {
                  type: 'ftb_quests', match: 'at_least', required: 2,
                  title: 'kubejs.orrery.optional_tasks',
                  targets: [{ id: '31' }, { id: '32' }, { id: '33' }]
                },
                {
                  type: 'research', same_machine: false, match: 'any',
                  title: 'Cross-well maxima, either target',
                  targets: [
                    { id: 'molecularmanipulator:research/sequence_array', count: 4 },
                    { id: 'molecularmanipulator:research/omni_computation', count: 2 }
                  ]
                }
              ]
            }
          ]
        }
      ]
    }
  }).id('kubejs:star_orrery_upgrade/l7');
});
```

Define the accompanying block tag in a normal data pack at
`data/kubejs/tags/block/orrery_support_controllers.json`, or at
`kubejs/data/kubejs/tags/block/orrery_support_controllers.json` when using KubeJS's data directory:

```json
{
  "replace": false,
  "values": [
    "molecularmanipulator:molecular_center_controller",
    "molecularmanipulator:omni_computation_controller"
  ]
}
```

The two Sequence Arrays and one Omni Computation machine can also satisfy the tag count of three;
this does not require three additional nonoverlapping machines. The group's `required: 2` counts
**two of three child routes**; the FTB leaf's `required: 2` counts **two of three targets**. Root-level
owning-core nodes and same-well research remain mandatory. Without FTB, task branches still fail;
other routes must really meet the group threshold to pass. A custom translation key is displayed as
the key when no translation exists; plain text works too.

## 7. Core calibration, pausing, reload and persistence

### 7.1 Physical rings are not the effective level

**A new core starts with a certified ceiling of 3.** This does not give an unbuilt machine L3
benefits. Complete the central body, foundation and corresponding consecutive rings to gain L1–L3
for free and automatically, without extra quests, machines, research, nodes, ME connection or
calibration payment. Effective level is always
**`min(server-verified consecutive complete physical rings, this core's certified ceiling)`**.
Network benefits and computing-power gates use that effective level; prebuilding nine rings cannot
bypass L4–L9 certification.

L4–L9 advance one level at a time, with a target **exactly equal to this core's certified ceiling + 1**.
Start/resume checks the central body, foundation, unique valid rule, live conditions and permissions.
With `auto_build: true`, the server checks supply and space, fills missing inner and target ring
anchors, then verifies the actual structure. With `auto_build: false`, all required rings must
already be hand-built. **An active main ME connection for calibration is required only when
`energy > 0`**; custom research/machine conditions or ME material supply may still need a grid.
Old saves must first finish migration. Completion never starts another level, and previewing does
not change the retained task.

The upgrade page's **condition catalog refreshes every 10 ticks** (about 0.5 s at 20 TPS), showing
names, counts and unmet requirements. This is not the calibration check interval: the core checks
structure, required connection, current rules and conditions **on every running tick**, then checks again
when the energy requirement is paid before recording certification. A client-side checkmark is not
authority to certify.

### 7.2 Defaults use zero energy; custom calibration credits actual AE extraction

Default rules complete the level after filling its anchors, without an extra energy wait. The tick
calibration below applies to pack-authored positive `energy` rules. Energy and `auto_build` are
independent and may be combined.


The core's **`serverTick()`** runs calibration independently of an open menu. Each tick requests
`min(current ae_per_tick, max(0, current energy - paid AE))` from the real ME grid through
`extractAEPower(..., Actionable.MODULATE, PowerMultiplier.ONE)`. **Only AE actually extracted becomes
paid progress.** While the grid remains active, partial supply credits the amount available; zero
extraction adds no paid AE. An `energy: 0` rule still requires a manual start, eligibility and the
final recheck; it does not auto-start or allow skipping levels.

Paid AE is not elapsed idle time, simulated extraction or historic machine consumption. Research,
node and anchor costs do not offset calibration. **Upgrade calibration does not charge Star Signal
Flow or consume research completions, machines or node bits.** Node purchases, idle power and optional
overclocking retain their separate charges. Orrery overclocking, production speed and well parallelism
**do not accelerate calibration**.

### 7.3 Manual pause differs from an automatic blocker

| Situation | Behavior of the current task |
| --- | --- |
| Click **Pause** | Retains target and paid AE, but clears run intent. Restored conditions do not restart it; click **Resume** explicitly. |
| Lost power / channel, damaged structure, unmet live conditions, missing or conflicting rule | Stops progress and calibration draw while retaining target, paid AE and run intent. The same level resumes automatically when eligible. |
| Close the screen, switch tabs or preview another level | Does not start, pause or cancel a task. Calibration continues while the core is ticking on the server and eligibility holds. |

You can also pause manually during an automatic blockage if you still have permission; an explicit
resume is then required. Unloaded chunks and a stopped server do not accrue offline progress. This
does not promise forced loading of third-party dependencies.

### 7.4 `/reload` uses live rules without erasing paid progress

A running task queries the current recipe every tick, never a saved rule used as a fallback. Reloaded
conditions, research definitions, **`energy` and `ae_per_tick` apply to the unfinished task**. A price
increase requires only the unpaid difference; a decrease does not refund AE. If the new cost is at
or below paid AE, the next eligible running tick can complete after its final recheck. A manually
paused task must still be resumed first. Reload does not erase or refund actual paid AE. A missing
or conflicting rule automatically blocks the task until fixed, preserving its run intent.

**Existing certifications are not revoked retroactively** and require no new payment under changed
rules. Their requirement lists are references to current rules only. Physical damage reduces the
physical and effective levels without erasing certification; repair restores the effective level
according to the formula.

### 7.5 Saved per core; old saves wait for physical verification

Certification and unfinished calibration belong to **this core**, not shared team-node bits. Its
**`star_orrery_upgrade`** NBT compound stores `version`, `certified_level`, `target_level`, `paid_ae`,
`running` (run intent, not a guarantee of current power draw) and `migration_pending`. The bound-team
UUID is stored separately as `entropy_team`; the current viewer's team does not replace it.

- World saves, chunk unloads and restarts retain this state. Whole-machine reclaim also keeps it in
  the core's **native archive data**, with the physical structure verified again after deployment.
- Normal core drops and **AE2 dismantle items** carry upgrade state and the bound team in
  `minecraft:custom_data`, restored on placement. Item data does not grant a saved formed state;
  the central body, foundation and rings must still be verified at the destination.
- **Old saves and old archives without `star_orrery_upgrade` are migration-pending**, not permanently
  reset to L3 and not granted high-level certification from saved formed flags, ring hints or package
  labels. Only after the central body and foundation pass live verification and a runtime ring scan
  is resolved does migration credit the actually complete consecutive rings. The resulting certified
  ceiling is `max(3, verified physical rings)`: 3 is the automatic ceiling for the first three levels,
  not a grant of three physical rings. Unresolved scans keep waiting; saved hints cannot grant L4–L9.
  Further levels after migration use the normal one-at-a-time calibration process.

### 7.6 Configuration and unavailable targets fail closed

- **Multiple declarations for one level:** two different IDs remaining in the final RecipeManager
  conflict even if their contents are identical. There is no first / last / lexicographic winner or
  implicit merge. A same-ID data-pack override is not two declarations.
- **Missing / invalid configuration:** without exactly one valid L4–L9 declaration, that level cannot
  start or continue. There is no saved-rule or hidden-default fallback. Invalid JSON / schema produces
  loading errors; inspect server logs first.
- **Deleted / unavailable targets:** removed research definitions, missing blocks, missing / empty
  tags, absent FTB objects / team data and unknown nodes cannot pass using stale saved completion.
  Normal `any` / `at_least` semantics still permit other genuinely passing entries to suffice.
- **Live authority:** active same-grid controllers, research counts, team nodes and FTB state are
  evaluated by the current server, not client checkboxes, the current viewer or a previously opened
  GUI snapshot.

`auto_build: true` places only the Orrery's required Star Position Anchors. It does not create other
machines, perform research, buy nodes or submit FTB tasks. Default upgrading does not charge or
produce Star Signal Flow. The separate Star Sea Calculation Mainframe provides survival signal
production; node purchases, idle power and overclocking retain their own costs.
