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

Combine a large pattern library with passive stock-maintenance crafting in the Frost Feather Crown.

## Build the array

<ItemGrid>
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:molecular_center_casing" />
<ItemIcon id="molecularmanipulator:molecular_center_glass" />
<ItemIcon id="molecularmanipulator:molecular_center_coil" />
<ItemIcon id="molecularmanipulator:molecular_center_stabilizer" />
<ItemIcon id="molecularmanipulator:molecular_center_core" />
</ItemGrid>

| Preparation | Requirement |
| --- | --- |
| Unlock | Tier 2: Sequence Array in the well |
| Space | 61 × 61 blocks, 29 blocks tall |
| Layout | Central controller, no solid base |
| Library | Default 200 pages × 36 slots; configurable up to 300 pages |

1. Gather the JEI material list, place the controller, and enable **Block Projection**.
2. Clear highlighted obstructions and select **Build**. Player inventory is used before ME.
3. Supply power and a channel. Verify the completed structure to use patterns and automatic crafting.

## Three interface pages

| Page | Purpose |
| --- | --- |
| Assembly overview | Status, projection, construction, and dismantling |
| Auto crafting | Nine independent patterns with stock targets and ingredient reserves |
| Colors | Customize the building's appearance |

See [Molecular Auto Crafter](molecular_auto_crafter.md) for passive-crafting settings.

## Pattern library

<Row>
<BlockImage id="molecularmanipulator:molecular_center_core" scale="4" />

<ItemImage id="ae2:pattern_access_terminal" scale="4" />

<ItemImage id="ae2:storage_bus" scale="4" />
</Row>

After formation, **14 quantum crystals** share the main library, which can be managed through a Pattern Access Terminal. A storage bus accesses only the main encoded-pattern library. Passive-crafting, quantum, and upload-core slots remain dedicated slots.

Construction, dismantling, and crystal recovery pause external pattern changes. Resume pattern management after recovery completes.

## Remote access and recovery

The quantum slot accepts only a **Quantum Entangled Singularity**. Place its partner in a powered AE2 quantum bridge to access the remote network, including construction materials before formation. The connection costs **512 AE/t and one channel** extra.

The optional ExtendedAE Plus upload core has its own slot. On a powered, formed array connected to the encoding terminal's ME network, encoded crafting, stonecutting and smithing patterns upload automatically into the main library. Duplicate encoding returns a blank pattern; processing patterns keep their normal terminal behavior. Both older EAEP and the 1.6.x upload API are supported.

**Dismantle** recovers from the top, returning to ME first and the player second; full destinations pause recovery.

Normal controller removal preserves patterns, jobs, and dedicated inventory contents. With multiblock chunk loading enabled, the formed structure and construction keep required chunks loaded.

## Controller recipe

<RecipeFor id="molecularmanipulator:molecular_center_controller" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
