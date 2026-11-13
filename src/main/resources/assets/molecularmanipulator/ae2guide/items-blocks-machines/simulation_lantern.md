---
navigation:
  parent: omnisequence-index.md
  title: "Simulation Lantern"
  icon: molecularmanipulator:simulation_lantern
  position: 1215
item_ids:
- molecularmanipulator:simulation_lantern
---

# Simulation Lantern

The Simulation Lantern provides light and lets you enter a personal, temporary simulation. While it is active, mine a Flawed Star Marrow Parent Rock yourself to obtain a permanent Flawless rock.

<Row>
<ItemImage id="molecularmanipulator:simulation_lantern" scale="6" />

<BlockImage id="molecularmanipulator:simulation_lantern" scale="4" />
</Row>

Both held and placed lanterns provide light in the simulation. The controls below use default keys, which you can change in control settings.

## Requirements

- Craft a lantern with 4 Star Marrow Alloy Ingots, 1 Star Marrow Crystal, 1 Stick and 2 Iron Nuggets.
- Bring a Flawed Star Marrow Parent Rock and a pickaxe. Lower-quality rocks cannot become Flawless this way.
- Stand outside water to enter the simulation. It needs no fuel or AE.

## Obtain a Flawless parent rock

<Row>
<ItemImage id="molecularmanipulator:flawed_star_marrow_parent_rock" scale="4" />

<ItemImage id="molecularmanipulator:simulation_lantern" scale="4" />

<ItemImage id="molecularmanipulator:flawless_star_marrow_parent_rock" scale="4" />
</Row>

1. Hold the lantern in either hand. If it is unlit, press B to light it.
2. Hold G for about one second, then release to enter the simulation. You remain in the same world and dimension.
3. Press F to move the lantern to your offhand and use a pickaxe in your main hand. You can also press V to place the lantern and free your hand.
4. Mine the Flawed rock yourself. It looks Flawless to you during the simulation and drops a Flawless Star Marrow Parent Rock. Creative pick-block also works while the simulation is active.
5. Leave the simulation and place the rock. It stays Flawless and never decays.

The target can be outside lantern light as long as your simulation remains active. Right-clicking the rock with the lantern does not convert it. Until harvested, the original block still grows buds and can decay. If it falls below Flawed, you receive its real lower tier. A player outside the simulation or an automatic miner cannot perform the conversion for you.

## Place, retrieve and leave

| Action | Default control and result |
| --- | --- |
| Light or extinguish | B operates a held lantern first. Without one in hand, it operates the placed lantern you aim at. |
| Place on the ground | V places it on the target block's top face, with support beneath. Dry placement keeps its lit state. |
| Retrieve | Leave a hand empty and right-click the placed lantern. With both hands full, it stays in place. |
| Leave the simulation | Use B to extinguish a lit lantern. Only the player pressing B leaves; extinguishing a placed lantern also removes that lamp's light for others. |

During the simulation, a held lantern cannot be dropped, stored or moved to another hotbar slot. Use F to move it to your offhand or V to place it so you can use other tools.

Entering water with a lit held lantern extinguishes it and ends the simulation. Water flowing into a placed lantern only extinguishes that lamp. Remove the water before relighting it. Logout, death or a dimension change also ends the simulation. Placed lanterns and dropped items remain, but logging back in does not restart it.

## Darkness and the green grid

Lantern light reaches 10 blocks and fades near the edge. Both held and placed lanterns count.

While you stand inside any lantern's lit area, illuminated surfaces look normal and unlit areas are black. Place your lantern and leave every lit area to see green grid lines and outlines on dark surfaces. Surfaces lit by distant lanterns still look normal. Walking back into light hides the grid again.

The view depends on where you stand. Carrying a lit lantern keeps you inside its light, and torches or daylight do not change this boundary.

## Enter the Detached Signal Realm

The Detached Signal Realm is a persistent void dimension where you can build. On your first arrival, there is only one bedrock block beneath you. Bring building blocks and a way to return across dimensions; the lantern cannot automatically take you home.

1. In any dimension, hold a lit lantern, hold G for about one second and release to enter the simulation.
2. Press V to place the lantern. Move beyond the 10-block range of every lit lantern, including those held by other players. Keep the simulation active; using B to extinguish a lamp ends it for you.
3. Jump into that dimension's bottom void. With the simulation active and every lantern's light behind you, you reach the realm before vanilla void damage. Ordinary void damage remains a danger if either condition is missing.

Arrival ends the simulation and frees the held lantern. The realm is always night, has no natural terrain, and contains drifting colored code streams. Time alternates between 0.5× and 2× speed every random 5 to 15 seconds. Players, creatures and machines in the realm follow that rate; other dimensions are unaffected. Your buildings remain when you leave and return.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| G does nothing | Hold a lit lantern outside water. Hold for about a second, then release, and check key conflicts. |
| Cannot change tools or drop the lantern | The simulation locks the held lamp. Press F for offhand use, or V to place it. |
| Cannot place the lantern | Aim at a supported block's top face and check space and build permission. |
| No green grid | Place the lamp, then leave every lantern's 10-block light range. |
| Mining did not give Flawless | Stay in the simulation, mine it yourself and check that the target has not decayed below Flawed. |

## Crafting recipe

<RecipeFor id="molecularmanipulator:simulation_lantern" fallbackText="Check JEI for the Simulation Lantern recipe." />
