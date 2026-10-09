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

The Molecular Sequence Rewrite Array combines pattern storage and recipe execution in one block. It runs crafting, smithing and stonecutting recipes for ME autocrafting.

<Row>
<BlockImage id="molecularmanipulator:molecular_manipulator" scale="4" />

<ItemImage id="ae2:crafting_pattern" scale="4" />

<ItemImage id="ae2:smithing_table_pattern" scale="4" />

<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

## Setup

1. Complete **Tier 2: Sequence Array** in the well and fabricate the array.
2. Connect it to a powered ME network with an available channel.
3. Insert encoded crafting, smithing or stonecutting patterns.
4. Request products from an ME terminal. The array receives ingredients and performs the recipes internally.

Its pattern inventory holds **360 patterns** across ten pages of 36 slots. For recipes using external machines, place processing patterns in a Pattern Provider connected to the required machines.

## Batch processing

Eligible recipes complete a batch in **one tick**. Batch size depends on ingredients, energy and output capacity. Crafting still consumes the recipe's materials and returns containers and tools.

Deep research bonuses apply to fabrication recipes in the well, not this device's processing speed.

## Output and relocation

When ME storage is full, products and returned items remain in the device until the network accepts them. Canceling work returns unused ingredients; completed products are retained.

Normal removal carries patterns and unfinished contents with the block. Restore its network connection and power to continue.

## Recipes

<RecipeFor id="molecularmanipulator:molecular_manipulator" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
