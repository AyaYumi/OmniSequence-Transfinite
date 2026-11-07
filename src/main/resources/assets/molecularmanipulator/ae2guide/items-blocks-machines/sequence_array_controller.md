---
navigation:
  parent: omnisequence-index.md
  title: "Sequence Array Controller"
  icon: molecularmanipulator:molecular_center_controller
  position: 1030
item_ids:
- molecularmanipulator:molecular_center_controller
- molecularmanipulator:molecular_center_casing
- molecularmanipulator:molecular_center_glass
- molecularmanipulator:molecular_center_coil
- molecularmanipulator:molecular_center_stabilizer
- molecularmanipulator:molecular_center_core
---

# Sequence Array Controller

The Sequence Array is a multiblock machine for pattern management and automatic restocking. It provides a large pattern inventory and nine independent autocrafting positions.

<ItemGrid>
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:molecular_center_casing" />
<ItemIcon id="molecularmanipulator:molecular_center_glass" />
<ItemIcon id="molecularmanipulator:molecular_center_coil" />
<ItemIcon id="molecularmanipulator:molecular_center_stabilizer" />
<ItemIcon id="molecularmanipulator:molecular_center_core" />
</ItemGrid>

## Structure and construction

Complete **Tier 2: Sequence Array** in the well to fabricate its parts. The structure occupies **61 × 61 blocks**, is **29 blocks tall**, and uses a central controller with no solid foundation.

1. Check JEI's structure material list, place the controller and enable **Block Projection**.
2. Clear obstructions, gather materials and select **Build**. Construction uses your inventory before ME storage.
3. Connect ME power and channels, then verify formation.

## Pattern inventory

The default inventory has **200 pages of 36 slots**, totaling 7200 patterns. Server configuration allows up to 300 pages. The formed structure's **14 quantum crystals** hold its patterns, which can be managed through a Pattern Access Terminal.

Storage Buses access only the main pattern inventory. Autocrafting and quantum slots have separate uses.

External pattern editing pauses during construction, dismantling and quantum crystal recovery. Finish these operations before rearranging patterns.

## Controller interface

| Page | Use |
| --- | --- |
| Assembly Overview | Structure and network status; projection, construction, whole-machine reclaim, auto upload and pattern migration |
| Auto Crafting | Restock using product targets and ingredient reserves |
| Colors | Set colors and this machine's effects level |

Autocrafting supports nine independent patterns. See [Molecular Auto Crafter](molecular_auto_crafter.md) for settings.

Enable **Auto upload** in the overview to receive newly encoded crafting, stonecutting and smithing patterns from native AE2 terminals on the same network. New arrays default to off. Duplicate recipes return a blank pattern; if the array is full or unavailable, the encoded item stays in the terminal. Processing patterns are not uploaded.

Click **Migrate patterns** to move existing crafting, stonecutting and smithing patterns from public pattern inventories and mounted storage cells on the same network. This is a one-click operation, not continuous collection. Physical duplicates and all item components are preserved; private inventories, read-only recipe lists, Storage Bus proxies and processing patterns stay untouched. Full-library leftovers remain at their source, and the result reports moved counts or partial completion.

The old upload-core slot is removed. A decodable saved core is returned through the controller's output buffer and retains the old enabled behavior when no explicit switch setting is saved. A saved switch choice takes precedence.

The Colors page's **Effects** button selects 0 off, 1 reduced or 2 full for this controller and all viewers, without changing production. Shift-click restores each client's global setting.

## Quantum connection and recovery

The quantum slot accepts only a **Quantum Entangled Singularity**. Put its partner in a powered AE2 quantum bridge to connect a remote network, including for construction before formation. The link requires an additional **512 AE/t and one channel**.

**Reclaim Package** seals the controller, installed structure, pattern library and internal contents into a lightweight whole-machine package. Click once, then confirm within three seconds; settle external ME crafting jobs first. Full data stays in this world's archive. See [Folded Buildings and Relocation](folded_buildings.md). Normal controller removal retains patterns, tasks and dedicated slot contents.

When enabled by the server, multiblock chunk loading keeps required chunks loaded while formed or under construction.

## Recipes

<RecipeFor id="molecularmanipulator:molecular_center_controller" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
