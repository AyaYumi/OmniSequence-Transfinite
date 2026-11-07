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
| Assembly Overview | Structure and network status; construction, auto upload and one-click pattern migration |
| Auto Crafting | Restock using product targets and ingredient reserves |
| Colors | Set building colors and this controller's effect level |

Autocrafting supports nine independent patterns. See [Molecular Auto Crafter](molecular_auto_crafter.md) for settings.

## Native upload and migration

Enable **Auto upload** to receive newly encoded crafting, stonecutting and smithing patterns from terminals on the same network. No ExtendedAE Plus or upload core is required. Duplicate recipes return a blank pattern; processing patterns and uploads without an available destination stay in the terminal.

**Migrate patterns** moves existing supported patterns from same-network public physical pattern slots and online mounted storage cells into the main library. Private slots, read-only recipe rows, storage-bus proxies and processing patterns are excluded. NBT and duplicate physical items are preserved; no blank is spent or pattern re-encoded. Full-library leftovers remain at the source; interrupted runs retain completed transfers. Migration is synchronous on click, not continuous background collection, so large transfers can take server time.

New arrays default to auto upload off. The old upload-core slot is removed; any decodable saved core is returned through the controller's output buffer and preserves the old enabled behavior. An explicitly saved toggle takes priority.

On **Colors**, **Effects** cycles Off / Reduced / Full for this controller and all viewers, without changing production. Shift-click or Shift-Enter/Space restores each client's global setting.

## Quantum connection and recovery

The quantum slot accepts only a **Quantum Entangled Singularity**. Put its partner in a powered AE2 quantum bridge to connect a remote network, including for construction before formation. The link requires an additional **512 AE/t and one channel**.

**Dismantle** keeps the controller and returns materials to ME before your inventory. Recovery pauses when there is no room. Normal controller removal retains patterns, tasks and dedicated slot contents.

When enabled by the server, multiblock chunk loading keeps required chunks loaded while formed or under construction.

## Recipes

<RecipeFor id="molecularmanipulator:molecular_center_controller" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
