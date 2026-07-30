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

The Assembler Matrix Sequence Rewrite Core is an upgrade component for the ExtendedAE Assembler Matrix. It replaces
ordinary crafting and speed cores with a high-throughput internal recipe executor.

## Setup

Build a valid ExtendedAE Assembler Matrix and use this block as one of its functional cores. It is not a standalone
machine: patterns, network access, and structure validation are supplied by the completed Assembler Matrix.

Once the matrix is formed and online, molecular-assembler-compatible patterns in the matrix can be assigned to this
core automatically.

## Processing and compatibility

The core runs the real recipe assembly logic rather than multiplying an output stack. This preserves:

- crafting remainders such as empty containers;
- tool damage and other durable ingredients;
- ingredients that are required but not consumed;
- recipes with multiple output key types.

Outputs are aggregated by AE key and returned to the ME Network in batches. A persistent buffer holds blocked outputs
until the network can accept them.

## Reusable inputs and cancellation

The core can execute same-key crafting remainders as one reusable batch. This includes items treated as unbreakable by
their item data. A finite-durability tool is batched only when every craft deterministically increases its damage by
exactly one; Unbreaking-enchanted, random, or context-dependent tools fall back to AE2's original one-craft path.
Key-changing remainders, such as a water bucket becoming an empty bucket, also stay on that path.

After accepting a reusable batch, the core owns and persists its complete execution state. Saving, unloading, or
restarting cannot lose or duplicate the remaining work. Canceling the AE2 job writes a persistent cancellation marker,
stops every unexecuted craft, and refunds the exact unused materials together with the reusable item's current state.
Outputs already completed before cancellation remain valid.

Batch execution uses AE2's native pattern-power calculation over the actual combined inputs, preserving AE2's original
crafting-energy behavior.

## Recipe

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" />
