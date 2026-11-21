---
navigation:
  parent: items-blocks-machines/event_horizon_singularity_hub.md
  title: "Singularity Resource Collection"
  icon: minecraft:raw_iron
  position: 0
---

# Singularity Resource Collection

Resource Collection produces every listed resource together and sends the output directly to ME. Start it once to collect raw ores, logs and other configured materials. It needs no sample or miniature black holes.

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

## Requirements

- A formed [Event Horizon Singularity Hub](event_horizon_singularity_hub.md), or a connected [Compact Hub](compact_singularity_hub.md).
- A powered ME network with channels and item storage that accepts every listed resource.
- No construction, rebuild or reclaim in progress on the multiblock hub.

Collection uses no Sequence Matter or fluid storage. For copying a sample or generating matter, use [Matter Duplication](singularity_matter_duplication.md). Those functions have separate controls.

## Start collection

1. Right-click the hub, open Resource Collection and check that the network is online.
2. Review the list and make sure ME accepts every item. A blocked product can stop the whole cycle even if other products have space.
3. Select **Start Collection**. The multiblock hub starts after **40 ticks**, whether its motion is stopped, paused or docking.
4. Open an ME terminal and check that the listed resources increase together. The outputs go straight into storage.

By default, the hub produces **1000 of each listed resource every 20 ticks**, about **1 second** at normal speed. Each resource counts once. Use the in-game list and rates for your server's current selection, exclusions, quantities and intervals.

## Stop and resume

- Select **Stop Collection** to stop supply without changing motion.
- Use the Matter Duplication page to stop matter generation or sample copying separately.
- If storage fills, free space to resume future cycles.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Empty list, or the item you want is absent | The server chooses the list. Check that the pack includes the resource and ask the server owner about selection and exclusions. |
| Collection appears active, but no products | Wait for startup, then check ME item capacity and storage filters for every product. |
| Multiblock motion cannot start or dock | Clear its motion path and load the entire motion area. |
| Collection stopped after a restart, damage or construction | Keep the area loaded and let ME reconnect. Repair any damage and finish construction. Select **Start Collection** if it remains stopped. |

Select Guide on the machine's collection page to return here.
