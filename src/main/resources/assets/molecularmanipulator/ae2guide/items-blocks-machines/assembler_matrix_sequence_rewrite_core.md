---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: Assembler Matrix Sequence Rewrite Core
  icon: molecularmanipulator:assembler_matrix_molecular_core
  position: 1010
categories:
- machines
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

## Recipe

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" />
