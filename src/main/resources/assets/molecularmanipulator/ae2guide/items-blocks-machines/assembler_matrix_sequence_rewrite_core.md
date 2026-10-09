---
navigation:
  parent: omnisequence-index.md
  title: "Assembler Matrix Sequence Rewrite Core"
  icon: molecularmanipulator:assembler_matrix_molecular_core
  position: 1010
item_ids:
- molecularmanipulator:assembler_matrix_molecular_core
---

# Assembler Matrix Sequence Rewrite Core

The Assembler Matrix Sequence Rewrite Core is an execution component for ExtendedAE's Assembler Matrix. It performs large crafting batches using the matrix's patterns and cannot operate on its own.

<Row>
<BlockImage id="molecularmanipulator:assembler_matrix_molecular_core" scale="4" />

<BlockImage id="molecularmanipulator:molecular_manipulator" scale="4" />
</Row>

## Installation

1. Complete **Tier 2: Sequence Array** in the well and fabricate the core.
2. Build an Assembler Matrix using ExtendedAE's rules, placing the rewrite core in an interior functional core position.
3. Insert Molecular Assembler-compatible patterns in the matrix's pattern cores.
4. Check formation and ME connectivity, then request products from a terminal.

The matrix still needs its structure blocks and pattern cores. The rewrite core supplies execution; the formed matrix supplies ingredients and networking.

## Tools and containers

Processing consumes the recipe's ingredients and preserves returned containers, reusable items and tool durability. Eligible recipes run in batches; recipes requiring individual handling run one craft at a time.

Batch crafting still requires enough materials, crafting energy and output capacity.

## Tasks and output

If ME cannot accept products, the core retains them until space is available. Canceling a craft stops unexecuted work and returns unused ingredients and current tools. Completed products are retained.

Normal core removal carries materials, products and unfinished work with it. Reinstall it in a valid matrix and restore ME to continue.

## Recipes

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
