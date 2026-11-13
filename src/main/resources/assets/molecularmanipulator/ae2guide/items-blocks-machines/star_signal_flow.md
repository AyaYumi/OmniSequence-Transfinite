---
navigation:
  parent: omnisequence-index.md
  title: "Star Signal Storage"
  icon: molecularmanipulator:star_signal_storage_cell
  position: 1220
item_ids:
- molecularmanipulator:star_signal_storage_component
- molecularmanipulator:star_signal_storage_cell
- molecularmanipulator:creative_star_signal_disk
---

# Star Signal Storage

A Star Signal Storage Cell stores the mainframe's Star Signal Flow for ME recipes such as automated crystal production. Craft the cell and put it in an ME Drive or ME Chest on the mainframe's network.

<Row>
<ItemImage id="molecularmanipulator:star_signal_storage_component" scale="5" />

<ItemImage id="molecularmanipulator:star_signal_storage_cell" scale="5" />

<ItemImage id="molecularmanipulator:creative_star_signal_disk" scale="5" />
</Row>

From left to right: the component used to craft a cell, the survival storage cell, and the creative-only disk.

Signal is measured in bits and appears as its own ME resource. It cannot be carried as an item stack or a bucket of fluid.

## Requirements

- Complete Tier II: Star Marrow Materials in the [Fabrication Well](matter_fabrication_research.md).
- Prepare crystals, dust, alloy and processors from the [star-material supply line](star_marrow_crystals.md).
- Have an ME Drive or ME Chest on a powered network.
- Prepare a [Star Sea Calculation Mainframe](star_sea_calculation_mainframe.md) to produce signal. The component and cell recipes need no signal.

## 1. Fabricate the component

In the well, process 32 Star Marrow Crystals, 32 Redstone and 8 Star Chart Processors into 8 Star Signal Storage Components. The base time is 20 seconds at 256 AE/t. Use ports or a matching processing pattern.

<RecipeFor id="molecularmanipulator:star_signal_storage_component" />

## 2. Assemble and install the cell

1. At a crafting table, follow the shaped recipe below. It uses 2 Quartz Vibrant Glass, 3 Star Marrow Crystal Dust, 1 component, 2 Star Marrow Alloy Ingots and 1 Star Chart Processor.
2. Keep the component in the center. The bottom row is alloy ingot, processor, alloy ingot.
3. Insert the resulting Star Signal Storage Cell into an ME Drive or ME Chest on the mainframe's network.

The cell stores one resource type with 256 MiB capacity. Ordinary item and fluid cells cannot store signal.

<RecipeFor id="molecularmanipulator:star_signal_storage_cell" />

## 3. Produce and use signal

1. Connect and power the mainframe. Wait for night and keep roof lighting at 7 or lower. See the [mainframe setup](star_sea_calculation_mainframe.md) for the cable socket and full operating conditions.
2. Open an ME terminal and find Star Signal Flow. The mainframe adds 128 bit per operating tick for 1024 AE by default.
3. For a Fabrication Well recipe, include signal with the other consumed inputs in a processing pattern. Install it in a [Pattern Assembly](matter_fabrication_pattern_assembly.md).

The CPU delivers stored signal directly to the recipe. Its bit count rises during production and falls as recipes use it.

### Show signal in a terminal

1. Open Configure visible types in the terminal's left toolbar.
2. Enable Star Signal Flow.
3. Clear any search or view filter that hides the entry.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Cell will not craft | Follow the shown shaped recipe, including the bottom-center processor. The component itself is made in the well. |
| Mainframe says storage is unavailable or full | Install a Star Signal Storage Cell on its ME network and check remaining capacity. |
| Cell stays empty | Empty-hand right-click the mainframe; check night, roof light, channel and energy. |
| Signal is stored but not shown | Enable its visible type in the terminal as above. |
| A recipe cannot receive signal | Use a Pattern Assembly and a processing pattern, not item/fluid input ports. |

## Creative disk

The Creative Star Signal Disk provides unlimited signal in creative mode. Insert it in an ME Drive. The terminal displays about 2.1G, while the supply remains unlimited. It uses the same visible resource type as the survival cell.

## Using stored signal

Set up the [Star Sea Calculation Mainframe](star_sea_calculation_mainframe.md), then try optional [Signal-Induced Crystallization](star_marrow_crystals.md) through a [Pattern Assembly](matter_fabrication_pattern_assembly.md).
