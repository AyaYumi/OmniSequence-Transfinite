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

Keep products stocked automatically. Nine independent pattern positions each have their own targets and switches.

<Row>
<BlockImage id="molecularmanipulator:molecular_auto_crafter" scale="4" />

<ItemImage id="ae2:crafting_pattern" scale="4" />

<ItemImage id="ae2:smithing_table_pattern" scale="4" />

<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

## Maintain stock

1. Connect a powered ME network with a channel. Insert up to **nine** crafting, smithing, or stonecutting patterns.
2. Select the gear above a pattern, or right-click it, to open its settings.
3. Set the **product stock limit** and each **ingredient reserve**. Press Enter or leave the field to apply.
4. Enable that position. Ingredients are drawn from ME and products return to the same network.

| Setting | Purpose |
| --- | --- |
| Product stock limit | Pause when stock reaches the target |
| Limit of zero | Continue until reserves or other conditions prevent crafting |
| Ingredient reserve | Keep the specified amount available in ME |
| Per-position switch | Enable or stop each pattern independently |

## Waiting and caches

Missing ingredients, insufficient power, disconnection, or blocked outputs cause the position to wait. It resumes when conditions recover. Use the arrows for additional ingredient and output entries.

This device executes crafting, smithing, and stonecutting. External-machine recipes use processing patterns and providers.

The Sequence Array's **Auto Crafting** page uses the same nine-position stock-maintenance settings.

## Recipe

<RecipeFor id="molecularmanipulator:molecular_auto_crafter" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
