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

Gravity Crystals let you walk on walls and ceilings or build lifts. Making them requires Ghost Matter as a reusable catalyst. Ghost Matter is harmless in your inventory or a catalyst slot, but placing it creates a hazard.

<Row>
<ItemImage id="molecularmanipulator:gravity_crystal" scale="5" />

<ItemImage id="molecularmanipulator:ghost_matter" scale="5" />
</Row>

Left to right: Gravity Crystal and Ghost Matter.

## Make Gravity Crystals

Complete Tier I: AE Foundation in a [Fabrication Well](matter_fabrication_well.md) and install a [Pattern Assembly](matter_fabrication_pattern_assembly.md) for catalyst storage.

1. Store 64 Certus Quartz Crystals, 32 Fluix Crystals, 32 Amethyst Shards, 64 Copper Ingots and 16 Ender Pearls in the well's ME network. Research cannot take materials from your inventory or input ports.
2. Open Study, select Special: Exotic Matter Studies and click Unlock. The first round takes 30 seconds at 256 AE/t. This optional branch has nine rounds; the first unlocks both recipes.
3. Fabricate 4 Ghost Matter from 8 Ender Pearls, 16 Glowstone Dust and 16 Certus Quartz Crystals.
4. Put one Ghost Matter in the Catalyst page of any assembly in this well. It remains there as a reusable tool, and one is enough for larger batches too. You do not need to place it in the world.
5. Encode a processing pattern for 16 Amethyst Shards, 8 Copper Ingots and 4 Fluix Crystals → 1 Gravity Crystal. Request the crystal in ME. Leave Ghost Matter out of the pattern's consumed inputs.

Both processing recipes take a base 20 seconds at 256 AE/t, before Star Computation Engine speed bonuses. The well returns the crystal while Ghost Matter stays in its catalyst slot.

<RecipeFor id="molecularmanipulator:ghost_matter" />

<RecipeFor id="molecularmanipulator:gravity_crystal" />

## Walk on walls or build a lift

Choose a low wall with open space and a safe landing for your first crystal.

1. Place the crystal on a floor, wall or ceiling. It needs no energy.
2. Leave it in the default attraction mode and walk close to the mounting surface. Your gravity turns toward it, letting you stand, walk and jump on that surface.
3. Empty-hand right-click the crystal to switch to repulsion. A floor-mounted crystal pushes upward in this mode, which you can use for a lift.
4. Apply redstone power to pause the field and remove the signal to restore it. Mine the crystal with a pickaxe to recover it.

| Field rule | What it means for your build |
| --- | --- |
| 5 × 5 × 5 area, centered on the crystal | Connect several fields for a longer route. |
| Solid walls block the field | Leave a clear path between the crystal and the player or object. |
| Creatures and dropped items | They are pulled or pushed; attraction also rotates player gravity. |
| Creative flight or spectator mode | These players are unaffected. |

Leaving the field, switching it off, entering water, riding or enabling creative flight restores normal player gravity. Leave a safe landing below walls and ceilings.

When Antarchy is installed, its forced or inverted gravity and transitions take priority over crystal attraction. The crystal takes over again when Antarchy releases control.

## Place and remove Ghost Matter

Prepare water and move creatures away before placing Ghost Matter. A deposit needs a supporting block below it and harms nearby living creatures in roughly a 3 × 3 × 3 area, bypassing armor. Solid walls block the hazard. Dropped items and creative or spectator players are unharmed.

Use a bucket or natural flowing water to waterlog the deposit and suppress the hazard. Creatures immersed in water or a bubble column are also protected. The hazard returns when the water drains away.

The deposit cannot be mined directly. Removing its support shatters it without an item drop and leaves dangerous mist for one minute. Keep the mist suppressed with water until it expires. Replacing the support does not restore the deposit, and water does not reset or extend the mist's expiry time.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Gravity Crystal production waits | Complete Exotic Matter Studies and put Ghost Matter in an assembly's Catalyst page in this well, not an input port. |
| The field seems off | Remove redstone power, move within range and remove solid obstructions. Check attraction/repulsion mode. |
| A player does not turn onto the wall | Stop creative flight; leave water or a vehicle. Check whether Antarchy is controlling gravity. |
| Ghost Matter still hurts after removing its support | The mist lasts one minute. Suppress it with water and wait. |
| The water was removed and damage returned | Water suppresses the deposit; it does not destroy it. |

## Catalysts and research

See [Pattern Assembly](matter_fabrication_pattern_assembly.md) for shared catalyst storage and [Fabrication Research](matter_fabrication_research.md) for larger batches. The [Simulation Lantern](simulation_lantern.md) is another reusable tool.
