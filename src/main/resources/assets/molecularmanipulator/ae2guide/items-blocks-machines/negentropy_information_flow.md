---
navigation:
  parent: omnisequence-index.md
  title: "Information Flow Storage"
  icon: molecularmanipulator:negentropy_information_cell
  position: 1220
item_ids:
- molecularmanipulator:negentropy_information_cell
- molecularmanipulator:creative_negentropy_information_cell
---

# Information Flow Storage

**Information Flow** is an AE resource used by the negative entropy series. It is not a normal item or fluid. It is measured in **bits** and can be stored in ME, written into patterns and used by autocrafting like any other AE key.

<Row>
<ItemImage id="molecularmanipulator:negentropy_information_cell" scale="6" />

<ItemImage id="molecularmanipulator:creative_negentropy_information_cell" scale="6" />
</Row>

## Information Flow Cell

Insert the **Information Flow Cell** into an ME Drive or ME Chest to store information flow. It has **1 type** and **4096 bytes** of capacity, suitable for early use.

It has no survival recipe in the current version. Use the creative inventory, or add a recipe through KubeJS in a modpack.

## Creative Negentropy Information Flow Disk

The **Creative Negentropy Information Flow Disk** contains unlimited information flow and is available only in creative mode. When inserted into an ME Drive, the terminal shows about **2.1G** and treats it as an unlimited supply.

## Cannot see Information Flow in the terminal?

AE2 terminals remember their **visible key types**. A terminal created before the Information Flow type was registered may hide it.

1. Open the **Configure visible types** button in the terminal's left toolbar (funnel icon).
2. Enable **Information Flow**.
3. The stock list will now show information flow. Both the normal cell and the creative disk use the same key type.

## Current recipe

<RecipeFor id="molecularmanipulator:negentropy_information_cell" fallbackText="No recipe is available in this pack; use the creative inventory in the current version." />
