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

**Star Signal Flow** is an AE resource used by the star computation series. It is not a normal item or fluid. It is measured in **bits** and can be stored in ME, written into patterns and used by autocrafting like any other AE key.

<Row>
<ItemImage id="molecularmanipulator:star_signal_storage_component" scale="6" />
<ItemImage id="molecularmanipulator:star_signal_storage_cell" scale="6" />
<ItemImage id="molecularmanipulator:creative_star_signal_disk" scale="6" />
</Row>

## Star Signal Storage Component

The **Star Signal Storage Component** is used to craft the storage cell. Its workbench recipe takes **4 Star Marrow Crystals, 4 redstone dust and 1 Star Chart Processor**. The processor handles addressing; the four crystals form the data cores.

<RecipeFor id="molecularmanipulator:star_signal_storage_component" />

## Star Signal Storage Cell

Combine the **Star Signal Storage Component** with an AE2 **Item Cell Housing** to craft the cell. Insert it into an ME Drive or ME Chest to store star signal flow. It supports **1 type** and **256 MiB (268,435,456 bytes)** of capacity.

<RecipeFor id="molecularmanipulator:star_signal_storage_cell" />

## Creative Star Signal Disk

The **Creative Star Signal Disk** contains unlimited star signal flow and is available only in creative mode. When inserted into an ME Drive, the terminal shows about **2.1G** and treats it as an unlimited supply.

## Cannot see Star Signal Flow in the terminal?

AE2 terminals remember their **visible key types**. A terminal created before the Star Signal Flow type was registered may hide it.

1. Open the **Configure visible types** button in the terminal's left toolbar (funnel icon).
2. Enable **Star Signal Flow**.
3. The stock list will now show star signal flow. Both the normal cell and the creative disk use the same key type.
