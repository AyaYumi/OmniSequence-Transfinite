---
navigation:
  parent: omnisequence-index.md
  title: "Compact Singularity Hub"
  icon: molecularmanipulator:compact_singularity_hub
  position: 1110
item_ids:
- molecularmanipulator:compact_singularity_hub
---

# Compact Singularity Hub

The Compact Singularity Hub provides resource collection, Sequence Matter generation and item duplication without a multiblock structure. It uses the same production and energy settings as the [Event Horizon Singularity Hub](event_horizon_singularity_hub.md).

<Row>
<BlockImage id="molecularmanipulator:compact_singularity_hub" scale="4" />
<ItemImage id="ae2:fluix_glass_cable" scale="4" />
</Row>

## Obtaining and connecting

This device has no default crafting recipe. Obtain it from the creative inventory or use `/give @s molecularmanipulator:compact_singularity_hub`.

Connect ME cables to any of its six faces. Supply power and **one channel**, then right-click to open its interface. Collection and duplication need ME item storage; Sequence Matter needs ME fluid storage.

Overview's quantum slot accepts a **Quantum Entangled Singularity** paired with a powered AE2 quantum bridge. The quantum link requires an additional **512 AE/t**.

## Collection and duplication

| Page | Operation |
| --- | --- |
| [Resource Collection](singularity_resource_collection.md) | Review available resources and start or stop collection |
| [Matter Duplication](singularity_matter_duplication.md) | Insert miniature black holes to generate matter and a sample to begin copying |

By default, every listed resource produces **1000 items every 20 ticks**. Collection pauses while offline and resumes when the network returns.

The miniature black-hole slot holds up to **64**. Each generates **20 mB** every 20 ticks by default, costing **1000 FE or 256 AE per mB**. Each copied item uses **1000 mB** of matter and retains the sample. Matter generation and copying do not require active resource collection.

## Relocation

Normal removal retains quantum contents, the sample, miniature black holes and caches. Place it again and reconnect ME to continue.

The compact hub does not force-load chunks. Keep its chunk loaded for continuous production.
