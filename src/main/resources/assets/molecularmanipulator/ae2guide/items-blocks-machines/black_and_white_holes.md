---
navigation:
  parent: omnisequence-index.md
  title: "Black Holes and White Holes"
  icon: molecularmanipulator:black_hole
  position: 1110
item_ids:
- molecularmanipulator:black_hole
- molecularmanipulator:white_hole
---

# Black Holes and White Holes

Move nearby items and non-player entities from a black hole to a white-hole exit in the same dimension.

<Row>
<ItemImage id="molecularmanipulator:black_hole" scale="6" />

<ItemImage id="molecularmanipulator:white_hole" scale="6" />
</Row>

## Fabricate the holes

Complete **Tier 3: Event Horizon** in the well. Run these large-input recipes through the Pattern Assembly. Each produces one item in 30 seconds at 4096 AE/t after the first unlock.

| Result | Only ingredient |
| --- | --- |
| Miniature Black Hole ×1 | AE2 Singularity ×100K |
| Miniature White Hole ×1 | AE2 Matter Ball ×1G |

<RecipeFor id="molecularmanipulator:black_hole" />

<RecipeFor id="molecularmanipulator:white_hole" />

## Set up an entrance and exit

1. Place a **white hole** and leave clear space above the exit.
2. Place a **black hole** in the same dimension. Nearby dropped items and non-player entities are pulled gradually toward it.
3. On reaching its center, entities teleport above the white hole with a small upward push.

| Rule | Behavior |
| --- | --- |
| Players | Never pulled or transferred |
| White-hole limit | One per dimension; remove it before placing another |
| Multiple miniature black holes | Share that dimension's white-hole exit |
| No white hole | No black-hole suction |
| White-hole chunk | Force-loaded while placed; released on removal |

The black hole's own chunk must remain loaded to operate. Entities are transferred intact rather than converted into resources or Sequence Matter.

## Use inside the hub

<ItemGrid>
<ItemIcon id="molecularmanipulator:event_horizon_singularity_hub" />
<ItemIcon id="molecularmanipulator:black_hole" />
<ItemIcon id="molecularmanipulator:singularity_sequence_matter_bucket" />
</ItemGrid>

Black-hole items in the hub's dedicated slot increase **Sequence Matter** production. The slot holds up to 64. See [Matter Duplication](singularity_matter_duplication.md).
