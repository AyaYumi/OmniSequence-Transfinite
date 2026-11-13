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

The Compact Singularity Hub collects resources, generates Sequence Matter and copies sample items in one block. It follows the same output and energy rules as the [multiblock hub](event_horizon_singularity_hub.md).

<Row>
<BlockImage id="molecularmanipulator:compact_singularity_hub" scale="4" />

<ItemImage id="ae2:fluix_glass_cable" scale="4" />
</Row>

Connect the hub to a powered ME network. No casing, tracks or separate resource core are needed.

## What you need

- Get the device from creative inventory, or use `/give @s molecularmanipulator:compact_singularity_hub` with command permission. There is no default survival recipe.
- Prepare ME power, at least 1 available channel and item storage that accepts the products.
- Matter generation and copying also need ME fluid storage, miniature black holes and energy; see [Matter Duplication](singularity_matter_duplication.md).

## First use

1. Place the hub and keep its chunk loaded. Connect ME cable to any face, or use Remote connection in the right sidebar. Follow [Sequence Wireless Hub setup](sequence_array_controller.md) for wireless requirements.
2. Right-click it and check that the network is online.
3. Open **Resource Collection**, review the list and select **Start Collection**. By default, each listed resource produces about **1000 items per second**.
4. Check that item counts rise in ME. To copy items, switch to **Matter Duplication** and follow its guide for the black-hole and sample slots.

| Your goal | Page to use |
| --- | --- |
| Produce the listed raw ores, logs and other resources together | [Resource Collection](singularity_resource_collection.md); no sample or black holes needed |
| Spend energy to generate Sequence Matter fluid | [Matter Duplication](singularity_matter_duplication.md); insert miniature black holes |
| Spend matter to copy a chosen item | [Matter Duplication](singularity_matter_duplication.md); insert a sample, which is retained |

Collection runs independently of matter generation and copying. Remove the sample to stop copying, or remove the black holes to stop matter generation.

## How to tell it worked

The interface shows an online network and collected items appear in ME. When copying, new copies appear while the sample stays in its slot. With no sample inserted, generated matter should accumulate in ME fluid storage.

## Moving the device

Stop collection, remove the block normally and keep its dropped item. Stored items, including the sample and black holes, pending deliveries and the wireless binding stay with the block. Place it again and restore its ME connection. Rebind if the wireless hub was moved or replaced. This device does not use the multiblock Reclaim package action.

## Quick fixes

| Problem | What to check |
| --- | --- |
| Network offline | Check ME power, channels and the selected cable or wireless connection. |
| No collected items | Start collection and provide item capacity for every listed resource. |
| Item space is available, but no matter appears | Fluid storage is also required. Check black holes and energy in [Matter Duplication](singularity_matter_duplication.md). |
| Production stops when you leave | The compact hub does not force-load chunks. Keep its chunk loaded. |

Collection pauses during temporary network loss and continues when the network returns. Missed production is not replayed.
