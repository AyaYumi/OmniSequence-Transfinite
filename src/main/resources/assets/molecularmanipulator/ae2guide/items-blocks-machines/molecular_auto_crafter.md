---
navigation:
  parent: omnisequence-index.md
  title: "Molecular Auto Crafter"
  icon: molecularmanipulator:molecular_auto_crafter
  position: 1040
item_ids:
- molecularmanipulator:molecular_auto_crafter
---

# Molecular Auto Crafter

The Molecular Auto Crafter keeps products stocked using ingredients already in ME. Insert a pattern from a Pattern Encoding Terminal, set a stock target and start the slot. It performs the recipe and delivers the products without a terminal order or crafting CPU.

<Row>
<BlockImage id="molecularmanipulator:molecular_auto_crafter" scale="4" />
<ItemImage id="ae2:crafting_pattern" scale="4" />
<ItemImage id="ae2:smithing_table_pattern" scale="4" />
<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

Left: the machine. Right: supported crafting, smithing and stonecutting patterns. Processing patterns for external machines do not belong here.

Stock the ingredients in ME first; the machine cannot order missing ingredients. Its nine pattern slots have separate settings and switches, but share the machine's crafting capacity.

## Requirements

- Complete one round of Tier I: AE Foundation in the [Fabrication Well](matter_fabrication_research.md), then fabricate the machine. No additional star-research unlock is required by default.
- A powered ME network with item storage and one free channel.
- An encoded oak log to oak planks crafting pattern, 32 oak logs and room for products.

<RecipeFor id="molecularmanipulator:molecular_auto_crafter" fallbackText="Check JEI for the Fabrication Well recipe, ingredients and research requirements." />

## Keep 64 planks in ME

1. Place the machine, connect it to ME and store the 32 logs. If ME already holds 64 planks or more, use some first so the machine has stock to replenish.
2. Insert the plank pattern. Click the slot's gear, or right-click its pattern, to open settings.
3. Set the output limit to 64 and the log reserve to 8. The machine can only consume logs above that reserve. It waits if the usable logs cannot complete another recipe.
4. Keep ME Output and choose ME network stock as the stock source. Click Save all, or submit individual fields with Set or Enter. Check that the page no longer says "Unsaved."
5. Return to the main screen and start the slot. You can leave the other slots off while setting it up.
6. The machine pauses when ME holds 64 planks. Take out 16 and it will replenish them to 64, provided it has enough logs above the reserve.

An output limit of 0 allows unlimited production. Use the switch or remove the pattern to stop a slot. An ingredient reserve of 0 holds none of that ingredient back.

### Output to a nearby chest

<Row>
<BlockImage id="molecularmanipulator:molecular_auto_crafter" scale="3" />
<BlockImage id="minecraft:chest" scale="3" />
</Row>

Place a chest beside the machine, switch to Nearby Container Output, then open Output Directions and enable the chest's side. Ingredients still come from ME; products and returned containers go to the selected adjacent containers.

| Stock source | When to use it |
| --- | --- |
| Destination stock | Replenish the selected chest to your target. Counts matching products in adjacent containers on enabled sides. |
| ME network stock | Decide whether to craft based on stock in ME. Even with chest output selected, this option counts ME stock only. |

Use the ingredient page buttons when a recipe has more entries. Submit reserve changes on those pages too.

## Crafting limits

Each batch needs enough room for products and returned items. Production pauses when the destination is full, so output cannot build up without limit inside the machine. A Star Computation Engine raises the Auto Crafter's crafting parallelism. Deep research raises the well's batch limit for fabricating Auto Crafters.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| Crafting never starts | Check power, channel, pattern and the slot's start switch. Make sure settings were submitted. |
| It needs ingredients, although ME still has some | Check reserves. Only ingredients above the reserve and sufficient for one complete recipe can be used. Missing intermediate ingredients are not automatically ordered. |
| It stops at the limit | This is normal. Remove products or increase the limit. |
| The chest has enough, but crafting continues | Check whether the stock source is "ME network stock." Choose "Destination stock" to replenish based on the chest's contents. |
| Output is blocked | For ME Output, check storage capacity. For nearby containers, check the chest, enabled sides and free slots. Returned containers need room too. |
| A number did not take effect | Click Set or Save all, or press Enter in the field. Moving the pointer away or closing the page does not submit it. |

## Wireless connection and relocation

Use Remote connection in the right sidebar to bind the machine to a Sequence Wireless Hub. Follow the [wireless setup](sequence_array_controller.md) for requirements.

Normal removal retains the machine's contents and wireless binding. Restore its ME connection after placing it again. If the wireless hub was moved or replaced, rebind the machine.

## Related machines

The [Sequence Array](sequence_array_controller.md)'s Stocking page has nine independent restocking slots with the same targets and reserves. They deliver to ME. Put restocking patterns in those slots and terminal-order patterns in its main library.

To request a specific quantity from a terminal, use a [Molecular Sequence Rewrite Array](molecular_sequence_rewrite_array.md) and connect a crafting CPU to the network.
