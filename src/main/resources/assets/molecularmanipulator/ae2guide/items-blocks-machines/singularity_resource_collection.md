---
navigation:
  parent: items-blocks-machines/event_horizon_singularity_hub.md
  title: "Singularity Resource Collection"
  icon: minecraft:raw_iron
  position: 0
---

# Singularity Resource Collection

The hub produces every item in its resource list simultaneously. Defaults include raw ores and logs; modpacks can configure other resources.

<ItemGrid>
<ItemIcon id="minecraft:raw_iron" />
<ItemIcon id="minecraft:raw_copper" />
<ItemIcon id="minecraft:raw_gold" />
<ItemIcon id="minecraft:oak_log" />
<ItemIcon id="minecraft:spruce_log" />
<ItemIcon id="minecraft:birch_log" />
<ItemIcon id="minecraft:crimson_stem" />
<ItemIcon id="minecraft:warped_stem" />
</ItemGrid>

## Starting collection

1. Form an [Event Horizon Singularity Hub](event_horizon_singularity_hub.md) or connect a [Compact Hub](compact_singularity_hub.md).
2. Open **Resource Collection** and review the complete current list.
3. Provide ME storage for its items and select **Start Collection**.
4. The multiblock hub begins production after startup. Products enter its connected ME network automatically.

By default, each resource produces **1000 items every 20 ticks**, about one second at normal TPS. An item matching multiple tags appears only once. There is no need to switch collection targets.

## Resource list

The server configuration's **Singularity Hub → Resource Collection** group controls resource tags, quantities, intervals and the blacklist.

| Setting | Default or example |
| --- | --- |
| Item tags | #c:raw_ores, #c:raw_materials, #minecraft:raw_ores, #minecraft:logs, #c:logs |
| Additional tag example | #minecraft:planks to produce planks |
| Blacklist | Item IDs such as minecraft:raw_iron |

Only tag members present in the pack enter the list. Blacklisted items are neither displayed nor produced. The page's list shows the current resources; the icons above are common examples.

## Stopping and waiting

Select **Stop Collection** to stop production and dock the multiblock hub. An offline network, incomplete structure or insufficient storage makes the device wait for operating conditions to recover. Products do not spill into the world when storage is full.
