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
- With Silk Touch: the cluster block itself, so you can move it and keep growing

## Materials

| Item | Use |
| --- | --- |
| Negentropy Crystal | Main cluster drop and base material for the negative entropy series |
| Negentropy Crystal Dust | Reserved material; no survival recipe in the current version |
| Negative Entropy Alloy Ingot | Reserved material; no survival recipe in the current version |

The **Flawless** parent rock has no survival source in the current version. It is available in creative mode; the engine conversion will be added later.
