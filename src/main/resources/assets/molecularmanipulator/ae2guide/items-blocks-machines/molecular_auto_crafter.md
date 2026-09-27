---
navigation:
  parent: omnisequence-index.md
  title: Sequence Auto Crafter
  icon: molecularmanipulator:molecular_auto_crafter
  position: 1015
item_ids:
- molecularmanipulator:molecular_auto_crafter
---

# Sequence Auto Crafter

<BlockImage id="molecularmanipulator:molecular_auto_crafter" scale="8" />

The Sequence Auto Crafter is the sequence array's passive auto-crafting subsystem in a single block. Connect it to a powered ME network with a channel and it continuously crafts from its dedicated pattern slots.

It has nine independent slots for encoded crafting, smithing and stonecutting patterns. Each slot supports an input reserve, a primary-output inventory limit, and its own enable switch. A limit of `0` means unlimited production. Missing materials, power, output room, or a reached limit pauses that slot and retries automatically.

Click the gear above a pattern, or right-click the pattern, to open its settings. Set the output limit and ingredient reserves with **Set**, Enter, or **Save all**. Use the bottom-right arrows for recipes with more than five ingredients. Return to the main page and use the button below the pattern to start or stop that slot.

<RecipeFor id="molecularmanipulator:molecular_auto_crafter" fallbackText="This recipe is unavailable when research or the recipe is disabled." />
