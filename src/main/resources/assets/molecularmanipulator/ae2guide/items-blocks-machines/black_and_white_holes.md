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

Miniature Black Holes transfer nearby dropped items and non-player entities to the nearest same-team Miniature White Hole in the same dimension. They provide item and creature transport and are ingredients for hub components.

<Row>
<ItemImage id="molecularmanipulator:black_hole" scale="6" />

<ItemImage id="molecularmanipulator:white_hole" scale="6" />

<ItemImage id="molecularmanipulator:miniature_supernova" scale="6" />
</Row>

## Fabrication

Complete **Tier 3: Event Horizon** and process these recipes through the well's [Pattern Assembly](matter_fabrication_pattern_assembly.md). After the first unlock, each recipe takes **30 seconds at 4096 AE/t**.

| Result | Ingredients |
| --- | --- |
| Miniature Black Hole ×1 | AE2 Singularity ×10,000 |
| Miniature White Hole ×1 | Miniature Black Hole ×10 |
| Miniature Supernova ×1 | Miniature Black Hole ×100, Miniature White Hole ×100 |

The hub controller and White Hole Resource Core each need one Miniature Supernova.

## Setting up transport

1. Place a **Miniature White Hole** and leave clear exit space above it.
2. Have a member of the same FTB team place a **Miniature Black Hole** in the same dimension.
3. Nearby dropped items and non-player entities are pulled toward the black hole. Reaching its center transfers them above the nearest white hole.

Players are neither pulled nor transferred. Without a matching white hole, the black hole does not begin pulling. Multiple black holes each choose their nearest exit; there is no white-hole placement limit.

## Teams and chunks

Placement binds a hole to the placer's FTB team, or to the individual without FTB Teams. Re-place holes after changing teams to update their ownership.

A white hole keeps its chunk loaded until removal. A black hole operates only while its own chunk is loaded. Transport preserves the entities and items themselves.

## Use in the hub

Miniature Black Hole items can also go into the hub's dedicated slot, up to **64**, to increase Sequence Matter production. See [Matter Duplication](singularity_matter_duplication.md).

## Recipes

<RecipeFor id="molecularmanipulator:black_hole" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<RecipeFor id="molecularmanipulator:white_hole" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<RecipeFor id="molecularmanipulator:miniature_supernova" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
