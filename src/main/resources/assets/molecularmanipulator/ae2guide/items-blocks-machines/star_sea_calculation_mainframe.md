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

The Star Sea Calculation Mainframe produces Star Signal Flow at night and sends it directly into ME storage. Place its single building item on a suitable foundation to deploy the entire 5 × 5 × 9-block tower.

<Row>
<ItemImage id="molecularmanipulator:star_sea_calculation_mainframe" scale="4" />

<ItemImage id="molecularmanipulator:star_signal_storage_cell" scale="4" />

<ItemImage id="ae2:fluix_glass_cable" scale="4" />
</Row>

Left to right: the mainframe building item, a signal storage cell and ME cable. The mainframe produces signal; the separate [nine-ring Star Orrery](star_computation_engine.md) supplies network bonuses.

## Requirements

- Make a Star Chart Processor through the [Star Marrow Materials](star_marrow_crystals.md) research and well recipes, then craft the mainframe below.
- Prepare a level, sturdy 5 × 5 foundation with 9 clear blocks above it.
- Prepare a powered ME network with one free channel, 1024 AE per operating tick and a [Star Signal Storage Cell](star_signal_flow.md) with space.
- For your first setup, use the Overworld and wait for night. The mainframe takes ME energy and cannot accept direct FE input.

<RecipeFor id="molecularmanipulator:star_sea_calculation_mainframe" />

## Place and connect it

1. Hold the building item and aim at the foundation. A teal preview means it fits; red means placement is blocked.
2. Right-click once to place the whole tower. The target is its front-center base position. It faces you and extends behind that point.
3. Connect ME cable to the white square with a teal center at the bottom front center. Only its outward-facing front accepts cable. The two maze panels above it are decoration.
4. Put the signal cell in an ME Drive or ME Chest on the same network. Check the mainframe's channel and power supply.
5. Wait for night and check roof light as described below. Operation consumes no crystals or processors.
6. Empty-hand right-click the building to read its status and current output and energy rates. Calculating means it is producing signal; check the amount in your ME terminal.

If the preview stays red, clear the full building volume, check every foundation block and stay within world borders and height limits. Placement protection can also prevent deployment.

## Night and roof lighting

In ordinary dimensions, production runs during day ticks 13000 to 22999. The dimension must have a normal day/night cycle and skylight. Daytime thunderstorms do not count, and the Nether and End do not qualify.

Every position in the 5 × 5 layer immediately above the roof must have light 7 or lower. This includes block light and nighttime skylight. A single position at 8 pauses the mainframe. Move nearby lamps away if needed. A clear view of the sky is not required.

### Detached Signal Realm

The eternal-night Detached Signal Realm bypasses the ordinary dimension and night requirements. It still needs a dark roof, ME power, a channel and storage space.

By default, it produces 8× as much signal for the same energy per operating tick. Time there alternates between half and double speed, changing output and energy use per real second.

## Output and energy

| Default location | Signal per operating tick | Energy per operating tick |
| --- | --- | --- |
| Ordinary supported nighttime dimension | 128 bit | 1024 AE |
| Detached Signal Realm | 1024 bit | 1024 AE |

An operating tick is a simulation step in that dimension, normally 20 per second. Check right-click status for your server's current rates. There is no idle draw when conditions prevent production or output is blocked.

With dynamic effects enabled, teal character streams appear during production. The maze texture animates even while stopped, so check status or stored signal to confirm output. The tower's glow and character effects emit no block light and cannot interfere with roof lighting.

## Troubleshooting

Read the empty-hand right-click message first.

| Status / problem | What to do |
| --- | --- |
| Waiting for night | Wait for actual night in a supported dimension; darkening the daytime sky is not enough. |
| Roof too bright | Check the full 25-position layer above the roof, not just its center. Remove nearby light sources until every position is ≤7. |
| ME offline or missing channel | Connect cable to the bottom-front white socket and check power and channel availability. |
| Star Signal storage unavailable or full | Install or empty a signal cell on this network; ordinary item/fluid cells do not store signal. |
| Insufficient energy | Increase the ME energy supply to cover the displayed operating rate. |
| Incomplete building or unloaded chunks | Keep the entire building loaded and check that no part is missing. |
| Signal count is hidden | Enable Star Signal Flow under the terminal's Configure visible types. |

## Moving the tower

Mine any physical part with an iron pickaxe or better to dismantle the whole tower and recover one building item. Your foundation remains. Creative removal drops nothing.

## Storing and using signal

Read [Star Signal Storage](star_signal_flow.md) to store and find the output, then use a [Pattern Assembly](matter_fabrication_pattern_assembly.md) for optional [crystal production from signal](star_marrow_crystals.md).
