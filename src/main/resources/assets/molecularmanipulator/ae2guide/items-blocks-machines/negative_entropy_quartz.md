---
navigation:
  parent: omnisequence-index.md
  title: "Negative Entropy Quartz and Seed"
  icon: molecularmanipulator:negative_entropy_crystal
  position: 1210
item_ids:
- molecularmanipulator:negentropy_seed
- molecularmanipulator:negative_entropy_crystal
- molecularmanipulator:negative_entropy_crystal_dust
- molecularmanipulator:negative_entropy_alloy_ingot
- molecularmanipulator:printed_negentropy_information_processor
- molecularmanipulator:negentropy_information_processor
- molecularmanipulator:negative_entropy_quartz_bud_small
- molecularmanipulator:negative_entropy_quartz_bud_medium
- molecularmanipulator:negative_entropy_quartz_bud_large
- molecularmanipulator:negative_entropy_quartz_cluster
- molecularmanipulator:negative_entropy_damaged_budding_quartz
- molecularmanipulator:negative_entropy_chipped_budding_quartz
- molecularmanipulator:negative_entropy_flawed_budding_quartz
- molecularmanipulator:negative_entropy_budding_quartz
---

# Negative Entropy Quartz and Seed

The **Negentropy Seed** converts entroized Fluix parent rocks into negative entropy parent rocks. Those parent rocks grow buds and clusters in the same way as Certus Quartz.

<Row>
<ItemImage id="molecularmanipulator:negentropy_seed" scale="6" />

<ItemImage id="molecularmanipulator:negative_entropy_crystal" scale="6" />

<BlockImage id="molecularmanipulator:negative_entropy_quartz_cluster" scale="4" />
</Row>

## Seed recipe

Craft shapeless:

- 1 Charged Certus Quartz Crystal
- 1 Sky Stone Dust
- 1 Amethyst Shard
- 1 Snowball
- 1 Ender Pearl Dust

This yields **2 Negentropy Seeds**.

<RecipeFor id="molecularmanipulator:negentropy_seed" fallbackText="No recipe is available in this pack. Check JEI." />

## Converting parent rocks

1. Get an **entroized Fluix parent rock** from ExtendedAE: fully, mostly, half or hardly entroized.
2. Right-click it with a Negentropy Seed. Each use consumes one seed and moves the rock one step:
   - Fully → Mostly → Half → Hardly entroized
   - Hardly entroized → **Severe Negentropy Quartz Parent Rock**
   - Severe → **Damaged Negentropy Quartz Parent Rock**
   - Damaged → **Flawed Negentropy Quartz Parent Rock**
3. The seed stops at **Flawed**. It cannot create a Flawless parent rock.

> Without ExtendedAE, the seed can still repair existing negative entropy parent rocks, but it cannot convert entroized Fluix rocks.

## Parent rock tiers

| Tier | Notes |
| --- | --- |
| Severe Negentropy Quartz Parent Rock | Lowest tier; it never decays further |
| Damaged Negentropy Quartz Parent Rock | Can decay to Severe when a bud grows |
| Flawed Negentropy Quartz Parent Rock | Can decay to Damaged; the seed stops here |
| Flawless Negentropy Quartz Parent Rock | Never decays; no survival source in the current version |

Each successful bud growth has about a **1 in 12** chance to lower the parent rock by one tier. Severe cannot decay further, and Flawless never decays.

## Growth and harvesting

Buds can grow on all six faces of a parent rock:

**Small Bud → Medium Bud → Large Bud → Cluster**

Mine a mature cluster with a pickaxe:

- Without Silk Touch: **3 Negentropy Crystals**, affected by Fortune
- With Silk Touch: the cluster block itself, for moving or decoration; continued crystal production requires buds growing on a parent rock

## Materials

| Item | Use |
| --- | --- |
| Negentropy Crystal | Main cluster drop and base material for the negative entropy series |
| Negentropy Crystal Dust | Grind crystals in an Inscriber for alloy processing |
| Negative Entropy Alloy Ingot | Alloyed in the AdvancedAE Reaction Chamber; structural material for the engine and anchors |
| Negentropy Information Circuit Board | Inscribe a crystal with the Calculation Processor Press |
| Negentropy Information Processor | Assemble a circuit board, redstone and Printed Silicon in an Inscriber for the engine and anchors |

### Crystal dust and alloy ingots

Without a press installed, the Inscriber turns **1 Negentropy Crystal into 1 Crystal Dust**. In the **AdvancedAE Reaction Chamber**, combine 4 crystal dust, 4 iron ingots, 1 Fluix Dust, 1 Sky Stone Dust and **500 mB water**, using **51,200 AE** in total, to produce **4 Negative Entropy Alloy Ingots**. Reaction Chamber processing needs no Fabrication Well research. This alloy recipe only loads when AdvancedAE is installed.

<RecipeFor id="molecularmanipulator:negative_entropy_crystal_dust" />

<RecipeFor id="molecularmanipulator:negative_entropy_alloy_ingot" />

### Circuit board and processor

Put a **Calculation Processor Press** in the Inscriber's top slot and a Negentropy Crystal in the middle slot to print the circuit board. The press is not consumed.

Then place the circuit board in the top slot, redstone in the middle slot and Printed Silicon in the bottom slot to assemble the processor. Neither step consumes Negentropy Information Flow.

<RecipeFor id="molecularmanipulator:printed_negentropy_information_processor" />

<RecipeFor id="molecularmanipulator:negentropy_information_processor" />

The **Flawless** parent rock has no survival source in the current version. It is available in creative mode; the engine conversion will be added later.
