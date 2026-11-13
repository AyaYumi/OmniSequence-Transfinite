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

Black holes pull in nearby dropped items and creatures and send them to the nearest same-team white hole in the same dimension. Place a black hole at the entrance and a white hole at the exit. They need no ME cables and neither pull in nor transport players.

<Row>
<ItemImage id="molecularmanipulator:black_hole" scale="6" />

<ItemImage id="molecularmanipulator:white_hole" scale="6" />

<ItemImage id="molecularmanipulator:miniature_supernova" scale="6" />
</Row>

Left to right: Miniature Black Hole, Miniature White Hole and Miniature Supernova. The supernova is a hub ingredient.

## Requirements

- Complete Tier IV: Event Horizon Materials at the well for black and white holes. The supernova also needs Tier V: Stellar Cores.
- Process the recipes below through the well's [Pattern Assembly](matter_fabrication_pattern_assembly.md). Each has a base time of 20 seconds at 4096 AE/t; Star Computation Engine bonuses can shorten it.
- Prepare a safe exit and keep livestock away from the entrance. The black hole pulls in nearby creatures.

| To make 1 | Ingredients |
| --- | --- |
| Miniature Black Hole | AE2 Singularity ×10,000 |
| Miniature White Hole | Miniature Black Hole ×10 |
| Miniature Supernova | Miniature Black Hole ×100, Miniature White Hole ×100 |

The hub controller and White Hole Resource Core each need one supernova. Black-hole items can also generate matter inside a hub; see [Matter Duplication](singularity_matter_duplication.md).

## Set up transport

1. Place the white hole first. Leave clear space above it, with a safe landing, enclosure or item collector.
2. Place the black hole in the same dimension. Both holes must be placed by members of the same FTB team. Without FTB Teams, place both as the same player.
3. Drop an expendable item near the black hole. It should disappear at the entrance and appear above the nearest matching white hole. The route transports the original item or creature without copying it.
4. Each black hole chooses its own nearest exit, so you can place multiple white holes.

Without a matching exit, the black hole does not begin pulling. A white hole keeps its own chunk loaded until removed. Keep the black hole's chunk loaded too for continuous transport.

## Troubleshooting

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
