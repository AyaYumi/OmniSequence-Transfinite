---
navigation:
  parent: omnisequence-index.md
  title: "Molecular Sequence Rewrite Array"
  icon: molecularmanipulator:molecular_manipulator
  position: 1000
item_ids:
- molecularmanipulator:molecular_manipulator
---

# Molecular Sequence Rewrite Array

Execute crafting, smithing, and stonecutting patterns inside one block for high-throughput ME autocrafting.

<Row>
<BlockImage id="molecularmanipulator:molecular_manipulator" scale="4" />

<ItemImage id="ae2:crafting_pattern" scale="4" />

<ItemImage id="ae2:smithing_table_pattern" scale="4" />

<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

## Connect it

1. Complete **Tier 2: Sequence Array** research and fabricate the block in the well.
2. Connect the array to a powered ME network with an available channel.
3. Insert encoded crafting, smithing, or stonecutting patterns. Use page buttons to manage them.
4. Request products in an ME terminal. The array accepts ingredients and executes the recipes.

| Capability | Detail |
| --- | --- |
| Pattern library | 10 pages × 36 slots = 360 slots |
| Internal execution | Crafting, smithing, stonecutting |
| External processing | Use processing patterns, providers, and the required machine |
| Unlock | Sequence Array research branch |

## Large batches

The logical batch ceiling is about **9.22E crafts**. Eligible recipes can finish a batch in one tick; available materials, energy, and ME output space determine actual size.

Parallelism preserves ingredient costs, tool returns, and crafting energy. Fabrication deep-research speed bonuses apply to well recipes only.

## Blocked outputs

Products and returns are retained before insertion into ME. Full storage delays delivery and triggers retries. Normal removal carries patterns and unfinished contents with the block.

## Recipe

<RecipeFor id="molecularmanipulator:molecular_manipulator" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
