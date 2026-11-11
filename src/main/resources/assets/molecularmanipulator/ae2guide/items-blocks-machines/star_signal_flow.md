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

A Star Signal Storage Cell holds **Star Signal Flow** produced by the mainframe, so ME crafting can use it for recipes such as automated crystal production.

<Row>
<ItemImage id="molecularmanipulator:star_signal_storage_component" scale="5" />

<ItemImage id="molecularmanipulator:star_signal_storage_cell" scale="5" />

<ItemImage id="molecularmanipulator:creative_star_signal_disk" scale="5" />
</Row>

From left to right: the component used to craft a cell, the survival storage cell, and the creative-only disk.

Signal is measured in **bits**. It appears as its own resource in ME, not as an item stack or a fluid you can carry in a bucket.

## Before you start

- Complete **Tier II: Star Marrow Materials** in the [Fabrication Well](matter_fabrication_research.md).
- Prepare the crystals, dust, alloy and processors from the [star-material supply line](star_marrow_crystals.md).
- Have an ME Drive or ME Chest on a powered network.
- Prepare a [Star Sea Calculation Mainframe](star_sea_calculation_mainframe.md) to produce your first signal. Making the component and cell does **not** consume signal.

## 1. Fabricate the component

In the well, process **32 Star Marrow Crystals + 32 Redstone + 8 Star Chart Processors → 8 Star Signal Storage Components**. Base time is **20 seconds at 256 AE/t**. Use ports or a matching processing pattern.

<RecipeFor id="molecularmanipulator:star_signal_storage_component" />

## 2. Assemble and install the cell

1. At a crafting table, use the exact shaped recipe below: **2 Quartz Vibrant Glass, 3 Star Marrow Crystal Dust, 1 component, 2 Star Marrow Alloy Ingots and 1 Star Chart Processor**.
2. Keep the component in the center; the bottom row is **alloy ingot — processor — alloy ingot**.
3. Insert the resulting **Star Signal Storage Cell** into an ME Drive or ME Chest on the **same network as the mainframe**.

The cell stores one resource type and has **256 MiB** capacity. Ordinary item/fluid cells do not replace it.

<RecipeFor id="molecularmanipulator:star_signal_storage_cell" />

## 3. Produce and use signal

1. Connect and power the mainframe. Wait for nighttime and keep its roof lighting at **7 or lower**; see the [mainframe setup](star_sea_calculation_mainframe.md) for its cable socket and full conditions.
2. Open an ME terminal and look for **Star Signal Flow**. The mainframe inserts its output directly into network storage: by default **128 bit per operating tick for 1024 AE**.
3. To use signal in a Fabrication Well recipe, encode it with the recipe's other consumed inputs in a processing pattern and install that pattern in a [Pattern Assembly](matter_fabrication_pattern_assembly.md).

You do not need to extract a physical signal item. The crafting CPU delivers the stored signal for the recipe.

## What success looks like

The ME terminal lists **Star Signal Flow** in bits, and its amount rises while the mainframe is calculating. An ordered recipe spends those bits alongside its other ingredients.

### If the terminal hides signal

1. Open **Configure visible types** in the terminal's left toolbar.
2. Enable **Star Signal Flow**.
3. Clear any search or view filter that hides the entry.

## If it does not work

| Problem | Fix |
| --- | --- |
| Cell will not craft | Follow the shown shaped recipe, including the bottom-center processor. The component itself is made in the well. |
| Mainframe says storage is unavailable or full | Install a Star Signal Storage Cell on its ME network and check remaining capacity. |
| Cell stays empty | Empty-hand right-click the mainframe; check night, roof light, channel and energy. |
| Signal is stored but not shown | Enable its visible type in the terminal as above. |
| A recipe cannot receive signal | Use a Pattern Assembly and a processing pattern, not item/fluid input ports. |

## Creative disk

The **Creative Star Signal Disk** provides unlimited signal in creative mode. Insert it in an ME Drive; the terminal displays about **2.1G**, but that is not a finite supply limit. It uses the same visible resource type as the survival cell.

## Next steps

Set up the [Star Sea Calculation Mainframe](star_sea_calculation_mainframe.md), then try optional [Signal-Induced Crystallization](star_marrow_crystals.md) through a [Pattern Assembly](matter_fabrication_pattern_assembly.md).
