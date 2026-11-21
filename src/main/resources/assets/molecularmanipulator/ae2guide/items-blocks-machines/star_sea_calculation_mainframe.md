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

The Star Sea Calculation Mainframe produces **Star Signal Flow** at night and sends it straight into ME storage. One item places the entire **5 × 5 × 9-block building**.

<Row>
<ItemImage id="molecularmanipulator:star_sea_calculation_mainframe" scale="4" />

<ItemImage id="molecularmanipulator:star_signal_storage_cell" scale="4" />

<ItemImage id="ae2:fluix_glass_cable" scale="4" />
</Row>

For machine bonuses, see the [nine-ring Star Orrery](star_computation_engine.md).

## Before you start

- Make a **Star Chart Processor** through the [Star Marrow Materials](star_marrow_crystals.md) research and well recipes, then craft the mainframe below.
- Prepare a **level, sturdy 5 × 5 foundation** and **9 clear blocks above it**.
- Prepare a powered ME network with **one free channel**, enough energy for **1024 AE per operating tick**, and a [Star Signal Storage Cell](star_signal_flow.md) with space.
- Use the Overworld for your first setup. The mainframe draws its power through ME.

<RecipeFor id="molecularmanipulator:star_sea_calculation_mainframe" />

## Place and connect it

1. Hold the building item and aim at the foundation's front-center position. The tower faces you and extends behind that point. Right-click when the preview turns teal.
2. Connect ME cable to the outward face of the white socket at the bottom front center.
3. Put the signal cell in an ME Drive or ME Chest on the same network. Check the night and roof conditions below. The mainframe makes signal from ME power.
4. Right-click the building with an empty hand to see its status and current rates. When it says **Calculating**, check that the Star Signal Flow count rises in your ME terminal.

If the preview stays red, clear the full building volume and check all foundation blocks, world borders, height limits and build permission.

## Night and roof lighting

In ordinary dimensions, the mainframe runs during **day ticks 13000–22999** in a dimension with skylight and a normal day/night cycle.

The full **5 × 5 layer just above the roof** needs light **7 or lower**, counting block light and nighttime skylight. One position at 8 pauses production. Move nearby lamps away or shade the roof as needed.

### Detached Signal Realm

In the **Detached Signal Realm**, the mainframe can run at any time if the roof is dark enough and ME supplies power, a channel and storage space. By default it makes **8× as much signal** for the same power each operating tick. Half and double speed in this realm change output and power use per real second.

## Output and energy

| Default location | Signal per operating tick | Energy per operating tick |
| --- | --- | --- |
| Ordinary supported nighttime dimension | 128 bit | 1024 AE |
| Detached Signal Realm | 1024 bit | 1024 AE |

A dimension normally runs 20 ticks per second. Use the rates shown in the building's status if your pack changes them. The mainframe has no idle power draw.

## If it does not work

| Status or problem | What to do |
| --- | --- |
| Star Signal storage unavailable or full | Install a Star Signal Storage Cell on this network or make room in one already installed. |
| Insufficient energy | Increase the ME energy supply to cover the displayed operating rate. |
| Incomplete building or unloaded chunks | Keep the entire building loaded and check that no part is missing. |
| Signal count is hidden | Enable **Star Signal Flow** under the terminal's **Configure visible types**. |

## Moving the tower

Mine any physical part with an **iron pickaxe or better** to dismantle the whole tower and recover one building item. Your foundation remains. Creative removal drops nothing.

## Next steps

Read [Star Signal Storage](star_signal_flow.md) to store and find the output, then use a [Pattern Assembly](matter_fabrication_pattern_assembly.md) for optional [crystal production from signal](star_marrow_crystals.md).
