---
navigation:
  parent: omnisequence-index.md
  title: Sequence Array Controller
  icon: molecularmanipulator:molecular_center_controller
  position: 1030
item_ids:
- molecularmanipulator:molecular_center_controller
- molecularmanipulator:molecular_center_casing
- molecularmanipulator:molecular_center_glass
- molecularmanipulator:molecular_center_coil
- molecularmanipulator:molecular_center_stabilizer
- molecularmanipulator:molecular_center_core
---

# Sequence Array Controller

<BlockImage id="molecularmanipulator:molecular_center_controller" scale="8" />

The Sequence Array Controller forms a Sequence Array within a 61x61 footprint and a 29-block height. Its current layout is the baseless Frost Feather Crown with a central controller. The completed multiblock combines massively
parallel AE crafting, per-pattern passive auto crafting, matter sequence rewriting, and an optional cross-dimensional
quantum connection.

## Building the structure

The controller and dedicated components are produced in the [Matter Fabrication Well](matter_fabrication_well.md) after
the first completion of **Stage II: Sequence Array** research. That branch also unlocks both sequence rewrite machines.
Packs may change the research requirements.

1. Place the controller in the central focusing seat, facing the operating side, with a quantum-crystal ornament above it. A single ring connects four crystal-feather fans.
   A compact five-layer amethyst pendant hangs beneath the center; the old ring socket is an ME-connectable casing.
2. Right-click the controller and enable the projection. Ghost blocks show missing positions, while red outlines show
   conflicts.
3. Use the JEI structure category for the complete layer view and material list.
4. Clear conflicts and use **Build**. Construction force-loads the required chunks and fills missing blocks in
   batches.
5. Connect the completed structure to a powered ME Network. An AE2 wrench rotates the controller and refreshes the
   structure check.

The exact field center remains air for the rendered quantum star. Feather ribs are separated by open gaps; there is no continuous floor or circular foundation.
Eight casing/glass/stabilizer panels and crystal nodes decorate the ring. Four low inner crystal seats preserve central access, and the front casing has an exposed outward-facing side for a cable.
Allow 6 blocks below and 22 above the central controller; the visual core sits 8 blocks above it. Use the projection to check the area first.
In dimensions allowing blocks at Y=-64 through 319, the central controller may be placed at Y=-58 through 297.
Legacy support retains only the official 1.3.9 palace array (31×31×46), labeled **Legacy 1.3.9**. Other historical and experimental layouts are no longer recognized or migrated.
This version changes the building substantially: open its projection first. The first **Update Structure** click arms confirmation; wait briefly and click again to proceed. Confirmation expires after five seconds.
Updating recovers the old structure and builds the current one, requiring new materials and recovery space. The controller moves three blocks down and fifteen blocks behind its original position; patterns, contents and settings are retained.
Unrelated blocks or entities at the destination stop relocation. Clear the destination and confirm again; use the new central controller after completion.

The array force-loads required chunks while formed and during construction, dismantling or structure updates. Valid
loading tasks resume after world reloads. Structural damage pauses work while retaining progress; unnecessary tickets
are released. Normal controller drops retain patterns, inventory, quantum-slot contents and owned task state.

Dismantling keeps the controller and queues only actual matching blocks. It completes each world-height layer from top to bottom, using serpentine rows within the layer. Air is not counted, and targets removed or changed externally are skipped without using the removal budget. Insufficient recovery capacity or denied operations pause the current block; the same queue and progress resume after conditions recover or the world reloads.

## Autocrafting

The completed array supports virtual parallelism up to the signed 64-bit limit. Real throughput is still limited by
ingredients, energy, output capacity, and server tick time.

The large pattern library accepts encoded AE2 crafting, smithing, and stonecutting patterns for ordinary AE crafting.
Shift-moving a pattern fills the current page first and continues into later pages when necessary; processing, blank,
and invalid patterns are rejected.

The **Auto Crafting** tab has its own row of nine dedicated pattern slots and never selects patterns from the large
library. Place patterns directly into these slots, then click the numbered selector or right-click the slot to configure
it. New patterns start disabled. Each logical input has an independent ME reserve: passive crafting never extracts enough
of that ingredient or any valid substitute to lower its stock below the reserve. The primary output has an ME stock
limit; `0` disables that limit and continues until protected ingredients or AE power run out. Conditions are checked
again automatically, so a paused pattern resumes when stock, power, or output capacity returns.

Passive batches extract directly from this controller's ME Network and use the `Long.MAX_VALUE` aggregate execution path
without acceleration cards. Primary outputs, byproducts, containers, reusable ingredients, and rollback refunds return
exclusively to ME through persistent escrow. There is no internal-storage or adjacent-inventory output mode.

### Reusable inputs and cancellation

The array can execute same-key remainders, including items marked as unbreakable, as one persistent reusable batch.
Finite-durability tools are batched only when every craft deterministically adds exactly one damage; Unbreaking-enchanted
or otherwise random and context-dependent tools fall back to AE2's original one-craft path. Key-changing remainders such
as water buckets also remain on that path.

Accepted reusable batches survive saves, chunk unloads, and server restarts. Canceling the AE2 crafting job persistently
stops all remaining executions and refunds the exact unused materials plus the reusable item's current state. Completed
outputs remain valid, and canceled work cannot resume after reload. Batch energy uses AE2's native pattern-power
calculation over the actual combined inputs.

## Matter sequence rewriting

The matter-rewriting system is similar to item transmutation in Equivalent Exchange: supported items are deconstructed
into stored sequence, and that sequence can then be spent to reproduce other supported items. Metal, mineral, crystal,
and organic sequence are stored independently. Deconstruction yields and rewriting costs are separate values, so an
item is not necessarily reproduced for the same amount recovered from deconstructing it.

### Deconstruction

Drag an item from JEI onto the **Deconstruction Marker** slot to select it; the marker is only a filter, so no physical
item is placed in or consumed by this slot. After deconstruction starts, the array automatically extracts matching
items from its connected ME Network and converts each one into the four configured sequence types.

The target controls how many items the current job processes. A target of `0` runs continuously. Missing input, ME
power, or a working network pauses the job and it retries automatically. Full sequence storage stops the job, while
excessive rewrite entropy puts it into the temporary **Cooling** state described below.

### Rewriting

Put one real item in the **Blueprint Sample** slot and start rewriting. The array spends that item's configured sequence
cost to create copies. The sample itself is never consumed, but it must remain in the slot unchanged for the entire job;
removing or replacing it stops the job.

Rewritten items can return directly to the ME Network or enter the controller's output slot. A target of `0` runs
continuously. If stored sequence or ME power is temporarily insufficient, the job waits and retries. A changed
blueprint or blocked output stops it.

Up to four AE2 acceleration cards affect batch size, batch interval and entropy cooling for both operations.
Packs may configure each value; defaults are:

| Cards | Maximum items per batch | Batch interval | Cooling multiplier |
| --- | --- | --- | --- |
| 0 | 1 | 20 ticks | ×1 |
| 1 | 2 | 10 ticks | ×2 |
| 2 | 4 | 5 ticks | ×4 |
| 3 | 16 | 2 ticks | ×16 |
| 4 | 64 | 1 tick | ×64 |

### Rewrite entropy

Rewrite entropy is a heat-like throughput limit for matter deconstruction and rewriting; it does not affect normal
autocrafting. Every successful operation adds entropy based on the total sequence handled:

- Deconstruction adds at least 1 entropy, or the total recovered sequence divided by 64.
- Rewriting adds at least 1 entropy, or the total sequence cost divided by 16.

Default entropy capacity is 1,000,000. Base cooling is 25 per second, multiplied by the installed cards' cooling multiplier;
four cards therefore remove 1,600 per second by default. Capacity, base cooling and multipliers are configurable.
Insufficient free entropy capacity pauses work in **Cooling** until it can resume. If a single operation costs more entropy
than total capacity, waiting cannot solve it: change the target or configuration. Cards do not change entropy per item;
the net accumulation depends on processing throughput and cooling together.

### Rules and item eligibility

Supported items and their exact deconstruction yields and rewriting costs are configured in:

`config/molecularmanipulator/matter_rewrite_rules.json`

The generated file supports exact item IDs, item tags, and tag-prefix wildcards, with separate `deconstruct` and
`rewrite` entries. Omit either entry to disable only that operation. Changes are loaded when the server or single-player
world starts.

Items with custom data, such as names, enchantments, durability, or container contents, are rejected.

Eligible item tooltips show a compact `[Hold Shift]` prompt by default. The client setting
`matter_sequence_tooltip_mode` can disable the details, keep Shift expansion, or show them permanently.

## Quantum link and visual settings

The quantum slot accepts one half of a paired entangled singularity. Put the other half in a powered AE2 Quantum Ring to
connect the array across dimensions. Before the structure is formed, this remote link can be used only to retrieve
construction materials. A full link consumes an additional 512 AE/t and one channel.

The colors tab changes the energy field, core, rings, and crystal effects. These settings are visual only and do not
change processing speed.

## Recipe

<RecipeFor id="molecularmanipulator:molecular_center_controller" fallbackText="This modpack has no available recipe for this item. Check JEI or the research configuration." />
