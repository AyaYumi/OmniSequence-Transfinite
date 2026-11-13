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

Insert a pattern made in a Pattern Encoding Terminal and set how many products to keep. The machine takes ingredients from ME, performs the recipe and delivers the output. Use it to keep everyday supplies stocked without placing terminal orders.

<Row>
<BlockImage id="molecularmanipulator:molecular_auto_crafter" scale="4" />
<ItemImage id="ae2:crafting_pattern" scale="4" />
<ItemImage id="ae2:smithing_table_pattern" scale="4" />
<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

Left: the machine. Right: supported crafting, smithing and stonecutting patterns. Processing patterns for external machines do not belong here.

Restocking runs inside the machine without a crafting CPU. Stock the ingredients in ME first; the machine does not order missing ingredients. Each of its nine pattern slots has its own settings and switch, while sharing the machine's crafting capacity.

## Prepare

- Complete one round of **Tier I: AE Foundation** in the [Fabrication Well](matter_fabrication_research.md), then fabricate the machine. No additional star-research node is required by default.
- A powered ME network with item storage and **one free channel**. A channel is a network connection allowance for a device.
- An encoded "oak log → oak planks" crafting pattern, **32 oak logs** and room for products.

<RecipeFor id="molecularmanipulator:molecular_auto_crafter" fallbackText="Check JEI for the Fabrication Well recipe, ingredients and research requirements." />

## Keep 64 planks in ME

1. Place the machine, connect it to the prepared ME network and store the 32 logs in ME. Remove any existing oak planks from ME for this test so you can watch replenishment.
2. Insert the plank pattern. Click the slot's **gear**, or right-click its pattern, to open settings.
3. Set the **output limit** to **64** and the log **reserve** to **8**. Only logs above the reserve may be consumed.
4. For your first setup, keep ME Output and choose ME network stock as the stock source. Click Save all, or submit individual fields with Set or Enter. Check that the page no longer says "Unsaved."
5. Return to the main screen and **start** that slot. Leave the other slots off for now.
6. Once ME holds 64 planks, take out 16. Watch the machine replenish them to 64.

**An output limit of 0 means unlimited, not stopped.** Stop a slot with its switch or by removing its pattern. An ingredient reserve of 0 means none of that ingredient is held back.

### Output to a nearby chest

<Row>
<BlockImage id="molecularmanipulator:molecular_auto_crafter" scale="3" />
<BlockImage id="minecraft:chest" scale="3" />
</Row>

Place a chest beside the machine, switch to **Nearby Container Output**, then open **Output Directions** and enable the chest's side. Ingredients still come from ME; products and returned containers go to the selected adjacent containers.

| Stock source | When to use it |
| --- | --- |
| Destination stock | Replenish the selected chest to your target. Counts matching products in adjacent containers on enabled sides. |
| ME network stock | Decide whether to craft based on stock in ME. Even with chest output selected, this option counts ME stock only. |

Use the ingredient page buttons when a recipe has more entries. Submit reserve changes on those pages too.

## What success looks like

- While enabled, the machine consumes logs and delivers planks to the selected destination.
- At 64 planks it reports that the limit has been reached and pauses. Removing some starts replenishment again.
- Restocking does not consume logs below the reserve of 8. If the usable ingredients cannot complete another recipe, the machine waits.

Each batch needs enough room for products and returned items. Production pauses when the destination is full, so output cannot build up without limit inside the machine. A Star Computation Engine can raise this machine's crafting parallelism; deep research raises the well recipe's parallelism when fabricating the machine itself.

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

## Next steps

The [Sequence Array](sequence_array_controller.md)'s **Stocking** page also has nine independent restocking slots, using the same target and reserve meanings and delivering to ME. Its main library serves terminal orders; its restocking slots serve automatic replenishment. Put patterns in the appropriate inventory.

To request a specific quantity from a terminal instead, use a [Molecular Sequence Rewrite Array](molecular_sequence_rewrite_array.md) and connect a crafting CPU to the network.
