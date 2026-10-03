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

An execution core installed inside an ExtendedAE Assembler Matrix, providing large-batch recipe processing.

<Row>
<BlockImage id="molecularmanipulator:assembler_matrix_molecular_core" scale="4" />

<BlockImage id="molecularmanipulator:molecular_manipulator" scale="4" />
</Row>

## Install in a matrix

1. Complete **Tier 2: Sequence Array** research and fabricate the core.
2. Build a valid matrix according to ExtendedAE's structure rules.
3. Install the core in a functional core position, form the matrix, and connect ME.
4. Supply Molecular Assembler-compatible patterns and request products from ME.

| Requirement | Detail |
| --- | --- |
| Complete matrix | Supplies structure, patterns, and networking |
| Standalone placement | Cannot operate independently |
| Batch capability | Logical ceiling around 9.22E crafts; actual inputs and outputs limit it |

## Reusable inputs

Recipes preserve containers, reusable inputs, and tool state. Eligible recipes execute in batches. Random durability or context-sensitive recipes use AE2's normal per-craft path.

<ItemGrid>
<ItemIcon id="minecraft:bucket" />
<ItemIcon id="minecraft:iron_pickaxe" />
<ItemIcon id="ae2:crafting_pattern" />
</ItemGrid>

| Situation | Result |
| --- | --- |
| ME cannot accept output | Retain it until storage accepts it |
| Job canceled | Stop unexecuted work; return unused materials and current tools |
| Save or reload | Retain work, materials, and progress |
| Core removed normally | Carry contents with it; resume in a valid matrix |

Products completed before cancellation remain valid. Crafting energy follows AE2 pattern rules.

## Recipe

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
