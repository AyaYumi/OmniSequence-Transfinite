---
navigation:
  parent: omnisequence-index.md
  title: "Star Sea Calculation Mainframe"
  icon: molecularmanipulator:star_sea_calculation_mainframe
  position: 1215
item_ids:
- molecularmanipulator:star_sea_calculation_mainframe
---

# Star Sea Calculation Mainframe

The Star Sea Calculation Mainframe produces **Star Signal Flow** at night and sends it straight into ME storage; one item places the entire **5 × 5 × 9-block building**.

<Row>
<ItemImage id="molecularmanipulator:star_sea_calculation_mainframe" scale="4" />

<ItemImage id="molecularmanipulator:star_signal_storage_cell" scale="4" />

<ItemImage id="ae2:fluix_glass_cable" scale="4" />
</Row>

You need the building item, a signal storage cell and an ME cable connection. This is the signal-producing tower, not the separate [nine-ring Star Orrery](star_computation_engine.md).

## Before you start

- Make a **Star Chart Processor** through the [Star Marrow Materials](star_marrow_crystals.md) research and well recipes, then craft the mainframe below.
- Prepare a **level, sturdy 5 × 5 foundation** and **9 clear blocks above it**.
- Prepare a powered ME network with **one free channel**, enough energy for **1024 AE per operating tick**, and a [Star Signal Storage Cell](star_signal_flow.md) with space.
- For your first setup, use the Overworld and wait for night. The mainframe takes ME energy; it does not accept direct FE input.

<RecipeFor id="molecularmanipulator:star_sea_calculation_mainframe" />

## Place and connect it

1. **Hold the building and aim at the foundation.** The preview shows the whole tower: **teal means it fits, red means it cannot be placed**.
2. **Right-click once.** One item places the entire building. The target is the **front-center base position**; the tower faces you and extends behind it.
3. **Connect the correct socket.** Attach ME cable to the **white square at the bottom front center**, with a teal center. Only its outward-facing front accepts cable. The two maze panels above it are decorations, not ports.
4. **Install signal storage.** Put the signal cell in an ME Drive or ME Chest on the same network. Ensure the mainframe has its channel and the network can supply the operating power.
5. **Check night and roof light**, as described below. No crystals or processors are consumed during operation.
6. **Empty-hand right-click the building** to read its status and actual output/energy rates.

You do not assemble the tower one block at a time. If the preview stays red, clear the whole reserved volume, check all foundation blocks and stay within world borders and height limits. Placement protection can also prevent deployment.

## Night and roof lighting

In ordinary dimensions, it works during **true night: day ticks 13000–22999**. The dimension must have a normal day/night cycle and skylight. Daytime thunderstorms do not count, and the Nether and End do not meet this requirement.

Every position in the **5 × 5 layer immediately above the roof** must have light **7 or lower**. The check includes block light and nighttime skylight. One position at 8 is enough to pause it. Move nearby lamps away if needed; an unobstructed view of the sky is not required.

### Detached Signal Realm

The eternal-night **Detached Signal Realm** is a special supported location. It bypasses the ordinary dimension/night requirement, but still needs a dark roof, ME power, a channel and storage space.

By default, it produces **8× as much signal at the same energy cost per operating tick**. Its time also alternates between half and double speed, so output and energy use per real second change with that time flow.

## What success looks like

Empty-hand right-click shows **Calculating**. With dynamic effects enabled, teal character streams appear around the building while it actually produces signal. The stored **Star Signal Flow** amount rises in your ME terminal.

| Default location | Signal per operating tick | Energy per operating tick |
| --- | --- | --- |
| Ordinary supported nighttime dimension | 128 bit | 1024 AE |
| Detached Signal Realm | 1024 bit | 1024 AE |

An operating tick is a simulation step in that dimension, normally 20 per second. Use the right-click status for your server's current rates; packs can change them. There is **no idle energy draw** when production conditions fail or output is blocked.

The glowing maze texture animates even when stopped, so it is not a production indicator. The tower's visual glow and character effects emit no block light and do not interfere with its roof checks.

## If it does not work

Read the empty-hand right-click message first.

| Status / problem | What to do |
| --- | --- |
| Waiting for night | Wait for actual night in a supported dimension; darkening the daytime sky is not enough. |
| Roof too bright | Check the full 25-position layer above the roof, not just its center. Remove nearby light sources until every position is ≤7. |
| ME offline or missing channel | Connect cable to the bottom-front white socket and check power and channel availability. |
| Star Signal storage unavailable or full | Install or empty a signal cell on this network; ordinary item/fluid cells do not store signal. |
| Insufficient energy | Increase the ME energy supply to cover the displayed operating rate. |
| Incomplete building or unloaded chunks | Keep the entire building loaded and check that no part is missing. |
| Signal count is hidden | Enable **Star Signal Flow** under the terminal's **Configure visible types**. |

## Moving the tower

Mine any physical part with an **iron pickaxe or better** to dismantle the whole tower and recover one building item. Your foundation remains. Creative removal drops nothing.

## Next steps

Read [Star Signal Storage](star_signal_flow.md) to store and find the output, then use a [Pattern Assembly](matter_fabrication_pattern_assembly.md) for optional [crystal production from signal](star_marrow_crystals.md).
