---
navigation:
  parent: items-blocks-machines/event_horizon_singularity_hub.md
  title: "Singularity Resource Collection"
  icon: minecraft:raw_iron
  position: 0
---

# Singularity Resource Collection

**Start once to produce every listed resource together and store it directly in ME.** Defaults focus on raw ores, logs and similar materials. You do not select a single target, insert a sample or supply miniature black holes.

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

These are common examples. **The in-game Resource Collection list is the complete current list.** The server decides which resources and quantities are available.

## What you need

- A formed [Event Horizon Singularity Hub](event_horizon_singularity_hub.md), or a connected [Compact Hub](compact_singularity_hub.md).
- A powered ME network with channels and item storage that accepts **every listed resource**.
- Loaded required chunks. The multiblock hub also needs its whole motion area intact and clear.

Collection does not spend Sequence Matter or need fluid storage. For copying a chosen sample or generating matter, use [Matter Duplication](singularity_matter_duplication.md). Start Collection is not the control for those operations.

## Start collection

1. Right-click the hub, open **Resource Collection** and check that the network is online.
2. Review its resource list and make sure ME accepts every item. Reserving space for raw iron while blocking another product can stop the whole cycle.
3. Select **Start Collection**. The multiblock hub first completes startup and begins moving; the compact hub has no building motion to wait for.
4. Open an ME terminal and check that listed resource counts increase.

By default, every **20 ticks**—about **1 second** at normal speed—**each resource produces 1000 items**. That is 1000 of each, not 1000 shared across the list. Duplicate resources count once; you do not need to switch targets.

## How to tell it worked

The page shows collection running and several resource counts rise together in ME. For example, if raw iron and oak logs are listed, both increase by their own cycle amount. Products do not drop out of the core into the world.

If the server changes resource selection, exclusions, batch quantities or intervals, follow the current interface values. Client-local settings do not replace the server's production list.

## Stop and resume

- Select **Stop Collection** to stop supply. The multiblock hub docks its moving parts.
- Stopping collection **does not** stop black-hole matter generation or sample copying. Those run independently on Matter Duplication.
- When chunks are temporarily unavailable or ME is reconnecting after a restart, keep the area loaded and wait.
- Missing blocks, a genuine disconnection or construction can stop collection. After repairs, check the page and start it again if it remains stopped.
- Full storage does not spill products into the world. Missed production cycles are not replayed after recovery.

## Quick fixes

| Problem | What to check |
| --- | --- |
| Empty list, or the item you want is absent | The server chooses the list. Check that the pack includes the resource and ask the server owner about selection and exclusions. |
| Collection appears active, but no products | Wait for startup, then check ME item capacity and storage filters for every product. |
| Multiblock cannot start or dock | Clear its motion path and load the entire motion area. The chunk-loading switch itself is not motion permission. |
| No output immediately after a restart | Let ME reconnect. If collection has stopped after recovery, start it manually. |
| Faster collection did not speed up copying | They are separate functions; see [Matter Duplication](singularity_matter_duplication.md). |

Select **Guide** on the machine's collection page to return here.
