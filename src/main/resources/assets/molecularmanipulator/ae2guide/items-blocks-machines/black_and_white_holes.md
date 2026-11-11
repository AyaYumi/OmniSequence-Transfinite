---
navigation:
  parent: omnisequence-index.md
  title: "Black Holes and White Holes"
  icon: molecularmanipulator:black_hole
  position: 1110
item_ids:
- molecularmanipulator:black_hole
- molecularmanipulator:white_hole
- molecularmanipulator:miniature_supernova
---

# Black Holes and White Holes

**The black hole is the entrance; the white hole is the exit.** Nearby dropped items and creatures travel to the nearest same-team white hole in the same dimension. No ME cables are needed. Players are not pulled in and cannot use this to teleport themselves.

<Row>
<ItemImage id="molecularmanipulator:black_hole" scale="6" />

<ItemImage id="molecularmanipulator:white_hole" scale="6" />

<ItemImage id="molecularmanipulator:miniature_supernova" scale="6" />
</Row>

Left to right: **Miniature Black Hole (entrance) → Miniature White Hole (exit)**. The Miniature Supernova is a hub ingredient, not a transport exit.

## What you need

- Complete **Tier IV: Event Horizon Materials** at the well for black and white holes. The supernova also needs **Tier V: Stellar Cores**.
- Process the recipes below through the well's [Pattern Assembly](matter_fabrication_pattern_assembly.md). Each has a base time of **20 seconds at 4096 AE/t**; Star Computation Engine bonuses can shorten it.
- Leave a safe exit and keep livestock away from the entrance: the black hole also pulls in nearby creatures.

| To make 1 | Ingredients |
| --- | --- |
| Miniature Black Hole | AE2 Singularity ×10,000 |
| Miniature White Hole | Miniature Black Hole ×10 |
| Miniature Supernova | Miniature Black Hole ×100, Miniature White Hole ×100 |

The hub controller and White Hole Resource Core each need one supernova. Black-hole items can also generate matter inside a hub; see [Matter Duplication](singularity_matter_duplication.md).

## Set up transport

1. **Place the white hole first.** Prepare clear space above it, with a safe landing, enclosure or item collector.
2. **Place the black hole.** Both must be in the same dimension and placed by members of the same FTB team. Without FTB Teams, use the same player for both.
3. **Test with one expendable item.** It is pulled into the entrance, then appears above the nearest matching white hole.
4. Once the test works, use the route for items or creatures. Each black hole chooses its own nearest exit; you can place multiple white holes.

## How to tell it worked

The item disappears at the entrance and **that same item or creature** appears above the exit. This transports it, rather than making a copy. Without a matching exit, the black hole does not begin pulling.

A white hole keeps its own chunk loaded until removed. The black hole's chunk must also stay loaded for continuous operation.

## Quick fixes

| Problem | What to check |
| --- | --- |
| Nothing is pulled in | Check the dimension, team and whether the black hole's chunk is loaded. |
| Transport stopped after a team change | Team ownership is set on placement. Re-place both holes. |
| Items arrive at another exit | The nearest same-team white hole wins. Move the exits. |
| Creatures wander away | Keep the space above the exit clear and add an enclosure. |

## Recipe lookup

<RecipeFor id="molecularmanipulator:black_hole" fallbackText="Check JEI for the well recipe and research requirements." />

<RecipeFor id="molecularmanipulator:white_hole" fallbackText="Check JEI for the well recipe and research requirements." />

<RecipeFor id="molecularmanipulator:miniature_supernova" fallbackText="Check JEI for the well recipe and research requirements." />
