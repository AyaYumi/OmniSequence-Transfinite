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

Gravity Crystals let you walk on walls or build lifts; Ghost Matter is the reusable material needed to make them, and becomes a dangerous hazard if placed in the world.

<Row>
<ItemImage id="molecularmanipulator:gravity_crystal" scale="5" />

<ItemImage id="molecularmanipulator:ghost_matter" scale="5" />
</Row>

Gravity Crystal on the left, Ghost Matter on the right. **Carried Ghost Matter and Ghost Matter in catalyst slots are harmless. You do not need to place it to craft with it.**

## Before you start

- Complete **Tier I: AE Foundation** in a [Fabrication Well](matter_fabrication_well.md).
- For the first **Special: Exotic Matter Studies** research, store **64 Certus Quartz Crystals, 32 Fluix Crystals, 32 Amethyst Shards, 64 Copper Ingots and 16 Ender Pearls** in its ME network. Your backpack and input ports do not supply research.
- Have an installed [Pattern Assembly](matter_fabrication_pattern_assembly.md) for the reusable catalyst, plus processing ingredients from the recipes below.
- For testing the gravity field, choose a low wall with open space and a safe landing area.

## Make your first Gravity Crystal

1. Open the well's **Study** page and select **Special: Exotic Matter Studies**. Select **Unlock** when its first-round materials are in ME.
2. Supply **256 AE/t for 30 seconds** to finish the first round. This optional branch has nine rounds; only the first is needed to unlock both recipes.
3. Fabricate **4 Ghost Matter** from **8 Ender Pearls, 16 Glowstone Dust and 16 Certus Quartz Crystals**.
4. Keep **one Ghost Matter** in any same-well assembly's **Catalyst** page. It is a reusable tool, not a consumed ingredient. One serves the well even for larger batches.
5. Encode the Gravity Crystal recipe using only **16 Amethyst Shards, 8 Copper Ingots and 4 Fluix Crystals → 1 Gravity Crystal**, then request it in ME.

Both processing recipes take a base **20 seconds at 256 AE/t**, before Star Computation Engine speed bonuses. Keep the Ghost Matter out of the pattern's consumed inputs.

<RecipeFor id="molecularmanipulator:ghost_matter" />

<RecipeFor id="molecularmanipulator:gravity_crystal" />

## Use the crystal: try a low wall first

1. Place the crystal on the surface you want to stand on: floor, wall or ceiling. It needs **no energy**.
2. Leave it in its default **attraction** mode. Walk close to the mounting surface; within the field, your gravity points toward it so you can stand, walk and jump there.
3. Empty-hand right-click the crystal to switch to **repulsion**. A crystal on the floor in this mode pushes upward, useful for a lift.
4. Apply a redstone signal to **pause** the field; remove the signal to restore it. Mine it with a pickaxe to recover the crystal.

| Field rule | What it means for your build |
| --- | --- |
| **5 × 5 × 5 area**, centered on the crystal | One crystal has a short reach. Connect several fields for a longer route. |
| Solid walls block the field | Keep a clear path between the crystal and the player or object. |
| Creatures and dropped items | They are pulled or pushed; attraction rotates player gravity. |
| Creative flight or spectator mode | These players are unaffected. |

Leaving the field, switching it off, entering water, riding or enabling creative flight restores normal player gravity. Plan a safe landing when testing walls and ceilings.

When Antarchy is installed, its forced/inverted gravity and transitions take priority over crystal attraction. The crystal takes over again when Antarchy releases control.

## If you want to place Ghost Matter

**Prepare water and keep creatures away before placing it.** A deposit needs a supporting block below and harms nearby living creatures in roughly a **3 × 3 × 3 area**, bypassing armor. Solid walls block the hazard. Dropped items and creative/spectator players are unharmed.

- **Suppress it with water:** a bucket or natural flowing water can waterlog the deposit and stop its hazard. Being immersed in water or a bubble column also protects a creature.
- **Do not remove the water casually:** the hazard returns when the water drains away.
- **To clear it:** the deposit cannot be mined directly. Removing its support shatters it **without an item drop** and leaves dangerous mist for **one minute**. Keep it suppressed with water while the mist expires.

Replacing the support does not restore the deposit. Water suppresses the lingering mist but does not reset or extend its expiry time.

## What success looks like

The well returns a Gravity Crystal while Ghost Matter remains in the catalyst slot. Near an attracting wall-mounted crystal, you can stand on that wall; toggling to repulsion pushes away instead. A waterlogged Ghost Matter deposit stops harming nearby creatures.

## If it does not work

| Problem | Fix |
| --- | --- |
| Gravity Crystal production waits | Complete Exotic Matter Studies and put Ghost Matter in an assembly's **Catalyst** page in this well, not an input port. |
| The field seems off | Remove redstone power, move within range and remove solid obstructions. Check attraction/repulsion mode. |
| A player does not turn onto the wall | Stop creative flight; leave water or a vehicle. Check whether Antarchy is controlling gravity. |
| Ghost Matter still hurts after removing its support | The mist lasts one minute. Suppress it with water and wait. |
| The water was removed and damage returned | Water suppresses the deposit; it does not destroy it. |

## Next steps

Read [Pattern Assembly](matter_fabrication_pattern_assembly.md) for shared catalyst storage, or [Fabrication Research](matter_fabrication_research.md) to raise this branch's batch size. For a different reusable tool, try the [Simulation Lantern](simulation_lantern.md).
