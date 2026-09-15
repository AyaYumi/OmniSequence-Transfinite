---
navigation:
  parent: omnisequence-index.md
  title: Assembler Matrix Sequence Rewrite Core
  icon: molecularmanipulator:assembler_matrix_molecular_core
  position: 1010
item_ids:
- molecularmanipulator:assembler_matrix_molecular_core
---

# Assembler Matrix Sequence Rewrite Core

<BlockImage id="molecularmanipulator:assembler_matrix_molecular_core" scale="8" />

The Assembler Matrix Sequence Rewrite Core is an upgrade component for an Assembler Matrix. It replaces ordinary crafting and speed cores with a high-throughput internal recipe executor.

| Property | Value |
| --- | --- |
| Requires | A valid Assembler Matrix |
| Standalone machine | No — patterns, network access and structure validation come from the matrix |
| Logical batch limit | `Long.MAX_VALUE` |
| Unlocked by | Stage II: Sequence Array |

## Setup

1. Complete **Stage II: Sequence Array** once in the [Matter Fabrication Well](matter_fabrication_well.md), then manufacture this core there.
2. Build a valid Assembler Matrix and use this block as one of its functional cores.
3. Once the matrix is formed and online, molecular-assembler-compatible patterns in the matrix can be assigned to this core automatically.

## Processing and compatibility

The logical batch limit is `Long.MAX_VALUE` (9,223,372,036,854,775,807 crafts).

> Ingredients, energy and remaining output capacity bound each batch; input/output arithmetic overflow is rejected.

The core runs the real recipe assembly logic rather than multiplying an output stack. This preserves:

* crafting remainders such as empty containers;
* tool damage and other durable ingredients;
* ingredients that are required but not consumed;
* recipes with multiple output key types.

Outputs are aggregated by AE key and returned to the ME Network in batches. A persistent buffer holds blocked outputs until the network can accept them.

## Reusable inputs and cancellation

The core can execute same-key crafting remainders as one reusable batch, including items treated as unbreakable by their item data. A finite-durability tool is batched only when every craft deterministically increases its damage by exactly one; Unbreaking-enchanted, random, or context-dependent tools fall back to AE2's original one-craft path. Key-changing remainders, such as a water bucket becoming an empty bucket, also stay on that path.

After accepting a reusable batch, the core owns and persists its complete execution state. Saving, unloading, or restarting cannot lose or duplicate the remaining work. Canceling the AE2 job writes a persistent cancellation marker, stops every unexecuted craft, and refunds the exact unused materials together with the reusable item's current state. Outputs already completed before cancellation remain valid.

Batch execution uses AE2's native pattern-power calculation over the actual combined inputs, preserving AE2's original crafting-energy behavior.

Normal block drops retain stored materials, outputs and execution state. A replaced core still needs a valid Assembler Matrix and network.

## Recipe

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" fallbackText="This modpack has no available recipe for this item. Check JEI or the research configuration." />
