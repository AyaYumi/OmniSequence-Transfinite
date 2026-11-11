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

## Purpose: perform rewrite crafting inside an Assembler Matrix

This is an **internal crafting component for ExtendedAE's Assembler Matrix**, not a standalone machine. Inside a formed matrix, it makes items using the matrix's recipes. Placing the core on the ground or connecting a cable to it is not enough.

<Row>
<BlockImage id="molecularmanipulator:assembler_matrix_molecular_core" scale="4" />
<BlockImage id="extendedae:assembler_matrix_pattern" scale="4" />
<BlockImage id="extendedae:assembler_matrix_frame" scale="4" />
</Row>

Left to right: this core performs crafting; the matrix pattern core holds **patterns**, the recipe cards made in a Pattern Encoding Terminal; frames and other structure parts form the matrix. The rewrite core does not replace the latter two.

## Prepare

- ExtendedAE and an Assembler Matrix built according to its structure rules.
- One completed round of **Tier III: Sequence Engineering** in the [Fabrication Well](matter_fabrication_research.md), then the core's ingredients below. Its recipe uses a [Molecular Sequence Rewrite Array](molecular_sequence_rewrite_array.md); when making that ingredient from scratch, first unlock its L3 Single-Block Integration permission.
- A powered ME network with item storage and a **crafting CPU**, the device that accepts and arranges crafting orders. The matrix makes items, the CPU manages orders, and ME storage holds ingredients and products.
- Enough **channels**, the network connection allowances for devices, plus encoded crafting patterns and their ingredients.

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" fallbackText="Check JEI for the Fabrication Well recipe, ingredients and research requirements." />

## Steps: install it, then place a test order

1. Build the frames and functional parts required by ExtendedAE. Put the rewrite core in an **interior position that accepts a crafting core**.
2. Insert an “oak log → oak planks” crafting pattern into a **matrix pattern core**, not the rewrite core.
3. Confirm that the matrix has formed. Connect it to the prepared ME network and check power and channels.
4. Store at least **4 oak logs** in ME, then request **16 oak planks** from a terminal.
5. Select an available CPU in the crafting confirmation screen. Confirm, watch Crafting Status, then retrieve the finished items from ME.

The core accepts Molecular Assembler-compatible patterns. Processing recipes for external machines still need those machines.

## What success looks like

- ExtendedAE recognizes a valid matrix, and the terminal offers the pattern's product.
- The test order finishes, turning 4 logs into 16 planks delivered to ME.
- Larger orders use the ingredients and energy actually available, with output returning to the network.

Suitable recipes run in batches; recipes requiring individual handling run one craft at a time. Containers, reusable items and tool durability follow the actual recipe. A Star Computation Engine on the same network can raise the core's crafting parallelism; it does not supply missing ingredients, power or output space.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| A standalone core does nothing | Install it in a valid ExtendedAE Assembler Matrix. For a standalone machine, use the Molecular Sequence Rewrite Array. |
| The matrix does not form | Check ExtendedAE's rules for frames, internal parts and core placement. A rewrite core cannot replace a pattern core. |
| The terminal cannot find the recipe | Put the encoded pattern in a matrix pattern core and check the matrix's network connection. |
| No CPU is available, or materials stay missing | Connect an idle CPU and supply the ingredients listed in Crafting Status. The core neither accepts orders nor stores the network's ingredient stock. |
| The order is waiting for products | Check ME capacity and item acceptance. Finished products wait inside the core when ME cannot accept them. |

Canceling an order stops unexecuted work and returns unused ingredients and current tools. Finished products are retained.

## Next steps

- Avoid a matrix structure: use a [Molecular Sequence Rewrite Array](molecular_sequence_rewrite_array.md).
- Organize a larger recipe library: see the [Sequence Array](sequence_array_controller.md).
- Increase order-handling capacity: see the [Omni Computation Core](omni_computation_core.md). Order capacity and the core's crafting capacity are different limits.

Settle orders before relocating. Normal core removal carries retained ingredients, products and unfinished contents with it; reinstall it in a valid matrix and restore ME to continue.
