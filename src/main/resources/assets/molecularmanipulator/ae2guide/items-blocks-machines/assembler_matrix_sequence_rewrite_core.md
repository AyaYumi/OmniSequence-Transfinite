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

The Assembler Matrix Sequence Rewrite Core performs crafting inside an ExtendedAE Assembler Matrix. Install it in a formed matrix; it cannot run on its own.

<Row>
<BlockImage id="molecularmanipulator:assembler_matrix_molecular_core" scale="4" />
<BlockImage id="expatternprovider:assembler_matrix_pattern" scale="4" />
<BlockImage id="expatternprovider:assembler_matrix_frame" scale="4" />
</Row>

Left to right: the rewrite core, a matrix pattern core and a frame. Put encoded patterns in the pattern core. The matrix still needs its frames, pattern cores and other required parts.

## Requirements

- ExtendedAE and an Assembler Matrix built according to its structure rules.
- One completed round of Tier III: Sequence Engineering in the [Fabrication Well](matter_fabrication_research.md), then the core's ingredients below. Its recipe uses a [Molecular Sequence Rewrite Array](molecular_sequence_rewrite_array.md). To make that ingredient, first unlock L3 Single-Block Integration.
- A powered ME network with item storage, an available crafting CPU and enough channels for the connected devices.
- Encoded crafting patterns and their ingredients.

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" fallbackText="Check JEI for the Fabrication Well recipe, ingredients and research requirements." />

## Install the core and request products

1. Build the frames and functional parts required by ExtendedAE. Put the rewrite core in an interior position that accepts a crafting core.
2. Insert a crafting pattern into a matrix pattern core. For example, encode an oak log to oak planks recipe in a Pattern Encoding Terminal.
3. Confirm that the matrix has formed. Connect it to ME and check power and channels. The terminal should list the pattern's product as craftable.
4. For 16 oak planks, store at least 4 oak logs in ME and request the planks from a terminal.
5. Select an available CPU in the crafting confirmation screen and confirm. Follow the order in Crafting Status, then collect the 16 planks from ME.

The core accepts Molecular Assembler-compatible patterns. Processing recipes for external machines still need those machines.

Suitable recipes run in batches; recipes requiring individual handling run one craft at a time. Containers, reusable items and tool durability follow the recipe. Larger orders need enough ingredients, energy and ME output space. A Star Computation Engine on the same network can raise the core's crafting parallelism.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| A standalone core does nothing | Install it in a valid ExtendedAE Assembler Matrix. For a standalone machine, use the Molecular Sequence Rewrite Array. |
| The matrix does not form | Check ExtendedAE's rules for frames, internal parts and core placement. A rewrite core cannot replace a pattern core. |
| The terminal cannot find the recipe | Put the encoded pattern in a matrix pattern core and check the matrix's network connection. |
| No CPU is available, or materials stay missing | Connect an idle CPU and supply the ingredients listed in Crafting Status. The core neither accepts orders nor stores the network's ingredient stock. |
| The order is waiting for products | Check ME capacity and item acceptance. Finished products wait inside the core when ME cannot accept them. |

Canceling an order stops unexecuted work and returns unused ingredients and current tools. Finished products are retained.

## Related machines

Use a [Molecular Sequence Rewrite Array](molecular_sequence_rewrite_array.md) for crafting in one block, or a [Sequence Array](sequence_array_controller.md) for a larger pattern library. An [Omni Computation Core](omni_computation_core.md) increases order-handling capacity; the rewrite core's crafting capacity is a separate limit.

Settle orders before relocating. Normal core removal carries retained ingredients, products and unfinished contents with it; reinstall it in a valid matrix and restore ME to continue.
