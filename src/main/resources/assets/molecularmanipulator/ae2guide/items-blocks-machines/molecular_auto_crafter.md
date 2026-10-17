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

The Molecular Auto Crafter draws ingredients from ME, performs recipes internally and returns products. It keeps frequently used materials stocked. Its nine pattern positions have independent stock targets, ingredient reserves and switches.

<Row>
<BlockImage id="molecularmanipulator:molecular_auto_crafter" scale="4" />

<ItemImage id="ae2:crafting_pattern" scale="4" />

<ItemImage id="ae2:smithing_table_pattern" scale="4" />

<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

## Setting up restocking

1. Connect a powered ME network with a channel.
2. Insert up to **nine** crafting, smithing or stonecutting patterns.
3. Select the gear above a pattern, or right-click it, to open that position's settings.
4. Set the **product stock limit** and each **ingredient reserve**. Press Enter or leave the input field to save.
5. Enable the position to begin automatic restocking.

| Setting | Effect |
| --- | --- |
| Product stock limit | Pause at the target and replenish when stock falls below it |
| Product limit of zero | Keep crafting until ingredients or other operating conditions prevent it |
| Ingredient reserve | Leave the specified amount in ME instead of using it for restocking |
| Per-position switch | Control each pattern separately |

For example, a plank target of 4096 and log reserve of 256 replenishes planks while leaving logs available to the network.

## Output to nearby containers

Switch the output mode to **Nearby Containers**, then open **Output Directions**. The left and right cells correspond to neighbours as seen from the machine's front. Select the desired sides; multiple sides can be enabled.

## Waiting and stopping

Each batch is limited by the space available at the selected output destination. Products and returned containers are delivered immediately; normal production does not accumulate an internal stockpile. A full destination pauses crafting, with retries up to one second apart. Disabling a position or removing its pattern stops new crafts.

Resources retained from older saves or an interrupted delivery are preserved and returned automatically. New crafting waits until these pending resources have been delivered.

Use the settings page arrows for additional ingredient and output entries. This device runs crafting, smithing and stonecutting recipes. External processing requires Pattern Providers and processing patterns.

The [Sequence Array](sequence_array_controller.md) provides the same nine-position restocking function on its **Auto Crafting** page.

## Recipes

<RecipeFor id="molecularmanipulator:molecular_auto_crafter" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
