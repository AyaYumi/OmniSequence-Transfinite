---
navigation:
  parent: omnisequence-index.md
  title: "Green Fire Lantern"
  icon: molecularmanipulator:green_fire_lantern
  position: 1215
item_ids:
- molecularmanipulator:green_fire_lantern
---

# Green Fire Lantern

The **Green Fire Lantern** is a portable lamp with four open sides and a green flame. You can carry it, place it, light it or put it out. Its special use is a **personal, temporary simulation**: with a lit lantern in either hand, hold **G** for about one second and release. While that simulation is active, a **Flawed** star marrow parent rock looks **Flawless** to you, and mining it or pick-blocking it gives you a **permanent Flawless parent rock**. The real world block is not changed.

If you only want the short version: craft the lantern, hold a lit one, hold **G** for one second and release, then mine a **Flawed** parent rock and place the **Flawless** block you receive.

<Row>
<ItemImage id="molecularmanipulator:green_fire_lantern" scale="6" />

<BlockImage id="molecularmanipulator:green_fire_lantern" scale="4" />
</Row>

## Recipe

Craft one lantern in a shaped recipe:

- 4 Star Marrow Alloy Ingots
- 1 Star Marrow Crystal
- 1 Stick
- 2 Iron Nuggets

The recipe does **not** use a Star Marrow Seed.

<RecipeFor id="molecularmanipulator:green_fire_lantern" fallbackText="No recipe is available in this pack. Check JEI." />

## Placing and retrieving

- **V** places the held lantern on the **top face** of the block you are aiming at. It needs a supporting block underneath. On a dry support, the placed lantern keeps its lit state.
- **Empty-hand right-click** a placed lantern takes it back into a free hand. If your main hand is empty it goes there; otherwise it goes to an empty offhand. With both hands occupied, the lantern stays in place and you get a message.
- **B** toggles the lantern you are holding. If you are not holding one, B toggles the lantern you are aiming at.
- Placing a lantern into water makes it waterlogged and unlit. If water later flows into a placed lantern, only its flame goes out; your simulation is not ended. A waterlogged lantern cannot be lit until it is no longer waterlogged.

## Entering and leaving the simulation

With a **lit** lantern in your main hand or offhand, hold **G** for about one second and release to enter. You cannot enter while standing in water. Each player enters their own simulation. It stays in the same world and dimension; there is no teleport and no second world.

To leave:

- Press **B** to extinguish a lit lantern, whether it is held or placed. Only the player who pressed B leaves; other players in their own simulations stay. If the lantern was placed on the ground, its shared light goes out for everyone in the simulation.
- Standing in water extinguishes a lit lantern in your hand and ends your simulation.
- Logging out, dying or changing dimension also ends it.
- World lanterns and dropped items remain. The simulation itself is not saved; logging back in does not restore it automatically.

## What the simulation shows

- Lit lanterns light the scene within a radius of **10 blocks**.
- The light fades between **9 and 10 blocks** from the source.
- The nearest **32 light sources** are merged, so overlapping lamps do not stack brighter.
- Both held lanterns and placed lanterns count as light sources.

**Whether the grid appears depends on where you stand, not on which surface you look at:**

- While you stand within 10 blocks of any lit lantern (holding one always keeps you inside, and standing next to a placed one is the same), lit surfaces look normal and unlit ones are pure black, with **no green grid or outlines visible**.
- Only after you place the lantern and walk out of every lit zone do unlit surfaces show the **dark green grid and outlines**. Surfaces still lit by a distant lantern keep their normal look.
- Walking back into any lit zone hides the lines again and turns the unlit area black.
- Torches and daylight do not change the green fire lantern light boundary.

Inside a lantern's light, you see the lit surfaces surrounded by darkness. Place the lantern and walk beyond every light pool to reveal the green grid.

## Getting a Flawless parent rock

Only a **Flawed** parent rock is affected.

1. Enter the simulation with a lit lantern.
2. Mine the flawed rock with a pickaxe, or pick-block it in creative.
3. The server checks who performed the action and gives that player a **Flawless Star Marrow Parent Rock**.
4. Place it whenever you like. The placed Flawless parent rock stays Flawless and never decays.

The world block itself stays **Flawed** while you are in the simulation. Bud growth and the usual chance to lose a tier still happen in the real world and are shared by everyone. If the rock degrades before you mine it, you receive the real lower tier. A normal player without the simulation, a fake player, or an automatic tool with no player actor still gets the real Flawed result, including when using pick-block. Standard tools that carry a player actor are covered; an external tool that edits blocks or inventories directly needs its own integration.

The simulation costs **no fuel, no AE and no extra Star Marrow Seeds**, and there is **no 12-block range requirement** for the Flawless result.

## Holding the lantern during the simulation

While the simulation is active, the lantern in your hand is locked to that hand:

- You cannot switch it to another hotbar slot, drop it, or put it into a container or storage.
- **F** still swaps the lantern and the item in your other hand.
- An offhand lantern does not stop you from switching tools in the main hand.
- Placing the lantern frees the hand for tools or other items.
