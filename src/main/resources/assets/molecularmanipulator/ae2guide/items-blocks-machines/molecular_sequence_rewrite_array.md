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

Insert patterns made in a Pattern Encoding Terminal, then request their products from an ME terminal. The rewrite array performs crafting, smithing and stonecutting inside one block, without a separate Molecular Assembler.

<Row>
<BlockImage id="molecularmanipulator:molecular_manipulator" scale="4" />
<ItemImage id="ae2:crafting_pattern" scale="4" />
<ItemImage id="ae2:smithing_table_pattern" scale="4" />
<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

Left: the machine. Right: crafting, smithing and stonecutting patterns. Put processing patterns for furnaces and other external machines in Pattern Providers.

A crafting CPU accepts orders and arranges their steps. The rewrite array stores patterns and performs recipes, using ingredients from ME storage and returning products there. You need all of these for terminal orders.

## Requirements

- Complete one round of Tier III: Sequence Engineering in the [Fabrication Well](matter_fabrication_research.md).
- Bring a [Star Computation Engine](star_computation_engine.md) to L3. Open Recipe Tree and click Unlock for Single-Block Integration, which costs 0 bit. By default, only this machine's and the Transfinite Compute Nexus's fabrication recipes need this permission.
- A powered ME network with item storage, a terminal, an available crafting CPU and one free channel.
- An encoded pattern and its ingredients.

<RecipeFor id="molecularmanipulator:molecular_manipulator" fallbackText="Check JEI for the Fabrication Well recipe, ingredients and research requirements." />

## Request products

1. Place the rewrite array and connect it to ME with cable, or follow [Sequence Wireless Hub setup](sequence_array_controller.md) to use Remote connection in the right sidebar.
2. Encode an oak log to oak planks crafting pattern in a Pattern Encoding Terminal. Insert it into the array's pattern inventory.
3. Store at least 4 oak logs in ME and leave room for the products.
4. Find oak planks in your ME terminal's craftable items. Request 16, select an available crafting CPU and confirm.
5. Follow the order in Crafting Status. When it finishes, the 4 logs have become 16 planks in ME and the CPU is no longer waiting for the array's output.

The pattern library holds 360 patterns across 10 pages of 36 slots. Use the page buttons for more space, or organize it through a Pattern Access Terminal on the same network.

## Batches and returned items

Large orders use the available ingredients, energy and output capacity. Suitable recipes run in batches; containers and tools return according to the recipe, with normal durability changes. A Star Computation Engine on the same network can raise crafting parallelism. Deep research raises the corresponding well recipe's parallelism, not this machine's crafting speed.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| The terminal does not offer the product | Check that the pattern is encoded for crafting, smithing or stonecutting, and that the array has power and a channel. |
| The product is listed, but no CPU is available | Connect an idle crafting CPU to the same network. The array does not accept orders itself. |
| The order keeps waiting for ingredients | Read the missing-material list in Crafting Status. External processing steps need Pattern Providers and the appropriate machines. |
| Products are made but do not reach storage | Free ME capacity and check that storage accepts the item. Products and returned items wait inside the array until they can be delivered. |
| The well shows a locked recipe | Check both Sequence Engineering in that well and Single-Block Integration permission. An engine supplying borrowed network permission must stay online. |

Canceling an order stops unexecuted work and returns unused ingredients. Already-made products remain available for delivery.

## Related machines

A [Sequence Array](sequence_array_controller.md) has a larger pattern library and built-in crafting. Use a [Molecular Auto Crafter](molecular_auto_crafter.md) to keep products stocked automatically. For orders that exceed your CPU's capacity, see the [Omni Computation Core](omni_computation_core.md).

Settle orders before relocating. Normal removal retains patterns, unfinished contents and the wireless binding. Place the array again and restore its ME connection and power. If the wireless hub was moved or replaced, rebind the array.
