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

These are common examples. Check the in-game Resource Collection list for every available resource and its quantity; the server sets both.

## Requirements

- A formed [Event Horizon Singularity Hub](event_horizon_singularity_hub.md), or a connected [Compact Hub](compact_singularity_hub.md).
- A powered ME network with channels and item storage that accepts every listed resource.
- Loaded required chunks. The multiblock hub also needs its whole motion area intact and clear.

Collection uses no Sequence Matter or fluid storage. For copying a sample or generating matter, use [Matter Duplication](singularity_matter_duplication.md). Those functions have separate controls.

## Start collection

1. Right-click the hub, open Resource Collection and check that the network is online.
2. Review the list and make sure ME accepts every item. A blocked product can stop the whole cycle even if other products have space.
3. Select Start Collection. The multiblock hub completes startup and begins moving; the compact hub has no moving structure.
4. Check the listed resource counts in an ME terminal. They should rise together; products go into storage without dropping into the world.

By default, each resource produces 1000 items every 20 ticks, about 1 second at normal speed. If raw iron and oak logs are listed, the cycle produces 1000 of each. Duplicate resources count once, and there is no target selection.

Server settings control resource selection, exclusions, quantities and intervals. Follow the current interface values; client settings cannot replace the server's production list.

## Stop and resume

- Select Stop Collection to stop supply. The multiblock hub docks its moving parts.
- Matter generation and sample copying continue until you stop them on Matter Duplication.
- When chunks are temporarily unavailable or ME is reconnecting after a restart, keep the area loaded and wait.
- Missing blocks, a genuine disconnection or construction can stop collection. After repairs, check the page and start it again if it remains stopped.
- Full storage does not spill products into the world. Missed production cycles are not replayed after recovery.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Empty list, or the item you want is absent | The server chooses the list. Check that the pack includes the resource and ask the server owner about selection and exclusions. |
| Collection appears active, but no products | Wait for startup, then check ME item capacity and storage filters for every product. |
| Multiblock cannot start or dock | Clear its motion path and load the entire motion area. The chunk-loading switch itself is not motion permission. |
| No output immediately after a restart | Let ME reconnect. If collection has stopped after recovery, start it manually. |
| Faster collection did not speed up copying | They are separate functions; see [Matter Duplication](singularity_matter_duplication.md). |

Select Guide on the machine's collection page to return here.
