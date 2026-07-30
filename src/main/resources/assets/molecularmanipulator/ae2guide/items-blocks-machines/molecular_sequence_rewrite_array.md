---
navigation:
  parent: omnisequence-index.md
  title: Molecular Sequence Rewrite Array
  icon: molecularmanipulator:molecular_manipulator
  position: 1000
item_ids:
- molecularmanipulator:molecular_manipulator
---

# Molecular Sequence Rewrite Array

<BlockImage id="molecularmanipulator:molecular_manipulator" scale="8" />

The Molecular Sequence Rewrite Array is a high-throughput [autocrafting](ae2:ae2-mechanics/autocrafting.md) machine.
It stores encoded patterns like a <ItemLink id="ae2:pattern_provider" />, but performs every
molecular-assembler-compatible recipe inside the block instead of sending ingredients to adjacent machines.

## Setup

1. Connect the array to a powered [ME Network](ae2:ae2-mechanics/me-network-connections.md) with an available channel.
2. Right-click it and insert encoded crafting, smithing, or stonecutting patterns.
3. Use the page buttons to move through the fixed pattern inventory: 10 pages with 36 slots each, for a total of
   360 pattern slots.
4. Request one of the encoded results from an ME terminal as usual.

Processing patterns are not executed internally. Use a normal pattern provider and an external machine for those.

## Processing and outputs

The array uses virtual parallel processing and can finish supported recipes in as little as one tick. Actual throughput
still depends on available ingredients, ME power, and whether the network can accept the results.

Recipe results, intermediate products, and container remainders are aggregated by AE key and returned to the ME Network.
If the network cannot accept them immediately, the array keeps them in a persistent output buffer and retries later,
including after a world reload.

## Recipe

<RecipeFor id="molecularmanipulator:molecular_manipulator" />
