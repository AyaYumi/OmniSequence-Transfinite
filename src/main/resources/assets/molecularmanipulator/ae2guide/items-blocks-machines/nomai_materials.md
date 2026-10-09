---
navigation:
  parent: omnisequence-index.md
  title: "Gravity Crystals and Ghost Matter"
  icon: molecularmanipulator:gravity_crystal
  position: 1120
item_ids:
- molecularmanipulator:gravity_crystal
- molecularmanipulator:ghost_matter
---

# Gravity Crystals and Ghost Matter

Gravity Crystals change nearby players' gravity or push creatures and dropped items. Ghost Matter is a placeable hazard and a retained catalyst for fabricating Gravity Crystals.

<Row>
<ItemImage id="molecularmanipulator:gravity_crystal" scale="6" />

<ItemImage id="molecularmanipulator:ghost_matter" scale="6" />
</Row>

## Unlocking and fabrication

After tier 1, research **Special: Exotic Matter Studies** in the well. It has one round, taking **30 seconds at 256 AE/t** by default.

Research consumes 64 Certus Quartz Crystals, 32 Fluix Crystals, 32 Amethyst Shards, 64 Copper Ingots and 16 Ender Pearls.

| Result | Consumed ingredients | Retained catalyst |
| --- | --- | --- |
| Ghost Matter ×4 | Ender Pearl ×8, Glowstone Dust ×16, Certus Quartz Crystal ×16 | None |
| Gravity Crystal ×1 | Amethyst Shard ×16, Copper Ingot ×8, Fluix Crystal ×4 | Ghost Matter ×1 |

Both recipes take **10 seconds at 256 AE/t**. Store Ghost Matter in the **Catalysts** page of any [Pattern Assembly](matter_fabrication_pattern_assembly.md) in the same well to supply its Gravity Crystal recipes. The catalyst is retained, excluded from processing patterns and does not need to be multiplied for batches.

## Using Gravity Crystals

Crystals mount on any of the six block faces and require no energy. Default **attraction** directs player gravity toward the mounting surface, allowing standing, walking and jumping on walls and ceilings. Creatures and dropped items are pulled toward that surface.

| Control or condition | Effect |
| --- | --- |
| Empty-hand right-click | Toggle attraction and repulsion |
| Redstone signal | Pause the field; removing the signal restores it |
| Range | A 5×5×5 area centered on the crystal |
| Solid walls | Block the field |
| Flying creative players or spectators | Unaffected |

Place a crystal on the ground and select **repulsion** to create a lift. Connecting multiple fields lets players move between different surfaces.

Mine a crystal with a pickaxe to retrieve it. Leaving the field, pausing or removing the crystal, entering water, riding or enabling creative flight restores ordinary gravity.

## Placing Ghost Matter

Ghost Matter requires a supporting block below and cannot be mined directly. It damages creatures within roughly a **3×3×3** area, bypassing armor. Solid walls provide protection. Dropped items and creative or spectator players are unharmed.

Breaking its support shatters it without an item drop and leaves hazardous mist for **one minute**. Replacing the support does not restore the deposit.

## Suppression with water

Buckets or natural flowing water can waterlog Ghost Matter and suppress its hazard. After removing the water source, the hazard returns as water drains. Creatures immersed in water or bubble columns are protected as well.

Mist left by a shattered deposit still expires after its original one-minute duration. Ghost Matter in inventories or catalyst pages is harmless.

## Recipes

<RecipeFor id="molecularmanipulator:ghost_matter" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<RecipeFor id="molecularmanipulator:gravity_crystal" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
