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

Carry it or place it as a lamp, or use it to enter a **personal, temporary simulation**. Its most useful trick is simple: **hold a lit lantern, hold G for about one second and release, then mine a Flawed Star Marrow Parent Rock yourself to obtain a permanent Flawless one.**

<Row>
<ItemImage id="molecularmanipulator:simulation_lantern" scale="6" />

<BlockImage id="molecularmanipulator:simulation_lantern" scale="4" />
</Row>

Left: the carried lantern. Right: the placed lantern. Both provide light in the simulation. Controls below use default keys; you can change them in control settings.

## What you need

- A lantern, crafted with **4 Star Marrow Alloy Ingots, 1 Star Marrow Crystal, 1 Stick and 2 Iron Nuggets**.
- A **Flawed Star Marrow Parent Rock** and a pickaxe. Lower-quality rocks cannot become Flawless this way.
- A dry place to stand: you cannot enter the simulation while in water. The simulation needs no fuel or AE.

## Obtain a Flawless parent rock

<Row>
<ItemImage id="molecularmanipulator:flawed_star_marrow_parent_rock" scale="4" />

<ItemImage id="molecularmanipulator:simulation_lantern" scale="4" />

<ItemImage id="molecularmanipulator:flawless_star_marrow_parent_rock" scale="4" />
</Row>

**Flawed rock → enter the lit-lantern simulation and mine it → Flawless rock.** Right-clicking the rock with the lantern is not the conversion action.

1. Hold the lantern in either hand. If it is unlit, press **B** to light it.
2. Hold **G** for about one second, then release to enter the simulation. You remain in the same world and dimension.
3. Press **F** to move the lantern to your offhand and use a pickaxe in your main hand. Alternatively, press **V** to place the lantern and free your hand.
4. **Mine the Flawed rock yourself** to receive a Flawless rock. In creative mode, pick-block also works during the simulation.
5. Place the Flawless rock whenever you want. It stays Flawless and never decays. The target does not have to be inside lantern light; your simulation just needs to remain active.

## How to tell it worked

The Flawed rock looks Flawless to you, and the item you mine is named **Flawless Star Marrow Parent Rock**. Leave the simulation and place it: it is still Flawless.

The simulation does not directly upgrade the original world block. Until mined, it still grows buds and can lose quality. If it has already decayed below Flawed, you cannot obtain a Flawless result. Another player outside the simulation, or an automatic miner, cannot perform this conversion for you.

## Place, retrieve and leave

| Action | Default control and result |
| --- | --- |
| Light or extinguish | **B** operates a held lantern first. Without one in hand, it operates the placed lantern you aim at. |
| Place on the ground | **V** places it on the target block's top face, with support beneath. Dry placement keeps its lit state. |
| Retrieve | Leave a hand empty and right-click the placed lantern. With both hands full, it stays in place. |
| Leave the simulation | Use **B** to extinguish a lit lantern. Only the player pressing B leaves; extinguishing a placed lantern also removes that lamp's light for others. |

**During the simulation, a held lantern cannot be dropped, stored or moved to another hotbar slot.** This is a normal control restriction, not a lost item. F can move it to your offhand so you can change main-hand tools; V can place it and free the hand.

Entering water with a lit held lantern extinguishes it and ends the simulation. Water flowing into a placed lantern only extinguishes that lamp; it does not end your simulation. Remove its water before relighting. Logout, death or a dimension change also ends the simulation. Placed lanterns and dropped items remain, but logging back in does not restart the simulation.

## Why is it sometimes black, sometimes a green grid?

A lantern's light reaches **10 blocks**, fading near the edge. Both held and placed lanterns count.

- **Stand inside any lit area:** illuminated surfaces look normal; unlit areas are pure black, without a green grid.
- **Place the lantern and leave every lit area:** dark surfaces show green grid lines and outlines. Surfaces still lit by distant lanterns look normal.
- **Walk back into light:** the grid disappears again. Carrying a lit lantern always keeps you inside a lit area.

It depends on **where you stand**, not where you aim. Torches and daylight do not change this boundary.

## Further exploration: Detached Signal Realm

This is a **real, persistent, buildable void dimension**, not a temporary view. On your first arrival there is only **one bedrock block** beneath you. **Prepare building blocks and a way to return across dimensions first; the lantern does not automatically take you home.**

1. In any dimension, hold a lit lantern, hold G for about one second and release to enter the simulation.
2. Press V to put it down and leave the 10-block range of **every** lit lantern, including lanterns held by you or other players. Keep the simulation active; do not extinguish a lamp with B to leave it.
3. Jump into that dimension's bottom void. When the conditions are met, you reach the realm before vanilla void damage. **Without the simulation, or while still in lantern light, ordinary void damage remains a danger.**

Arrival ends the simulation and its hand lock. The realm is always night, without natural terrain, and colored code streams drift through the air. Time alternates between **0.5× and 2× speed** every random **5–15 seconds**. Players, creatures and machines here follow that rate; other dimensions are unaffected. Revisiting does not erase your buildings.

## Quick fixes

| Problem | What to check |
| --- | --- |
| G does nothing | Hold a lit lantern outside water. Hold for about a second, then release, and check key conflicts. |
| Cannot change tools or drop the lantern | The simulation locks the held lamp. Press F for offhand use, or V to place it. |
| Cannot place the lantern | Aim at a supported block's top face and check space and build permission. |
| No green grid | Place the lamp, then leave every lantern's 10-block light range. |
| Mining did not give Flawless | Stay in the simulation, mine it yourself and check that the target has not decayed below Flawed. |

## Crafting recipe

<RecipeFor id="molecularmanipulator:simulation_lantern" fallbackText="Check JEI for the Simulation Lantern recipe." />
