---
navigation:
  parent: items-blocks-machines/event_horizon_singularity_hub.md
  title: "Singularity Resource Collection"
  icon: minecraft:raw_iron
  position: 0
---

# Singularity Resource Collection

Produce every resource included in configured item tags. Any item tag is supported, including resources beyond raw ores and logs; each cycle produces the full list simultaneously.

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

These are common examples. The **complete list in Resource Collection** follows the pack's tags and blacklist.

## Start harvesting

1. Form the [Singularity Hub](event_horizon_singularity_hub.md) and connect online ME.
2. Open **Resource Collection** and review the full production list.
3. Reserve ME item space for every entry and select **Start Collection**.
4. Wait for startup. Products are inserted into the connected ME network.

| Default setting | Value |
| --- | --- |
| Per item, per cycle | 1000 items |
| Interval | 20 ticks; about one second at normal TPS |
| Production | All entries together, duplicates removed |
| Insufficient storage | Wait for capacity; do not spill products into the world |

## Configure the resource list

The server configuration's **Singularity Hub → Resource Collection** group controls tags, quantity, interval, and the item blacklist.

| Setting | Default or format |
| --- | --- |
| Item tags | #c:raw_ores, #c:raw_materials, #minecraft:raw_ores, #minecraft:logs, #c:logs |
| Item blacklist | Exact item IDs, such as minecraft:raw_iron |

Add other item tags, such as #minecraft:planks for planks. Existing ore/log lists migrate into one list while preserving custom entries.

Only tags actually defined in the pack contribute items. All matching tag members are combined, then excluded IDs are removed. **The blacklist wins**, and excluded items also disappear from the collection list.

## Stop or wait

Select **Stop Collection** to stop harvesting and dock the building. Check Overview and Collection status for network, structure, and storage requirements before resuming.
