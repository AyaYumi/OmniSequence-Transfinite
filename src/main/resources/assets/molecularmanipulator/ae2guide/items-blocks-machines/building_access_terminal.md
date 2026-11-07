---
navigation:
  parent: omnisequence-index.md
  title: "Building Access Terminal"
  icon: molecularmanipulator:building_access_terminal
  position: 1005
item_ids:
- molecularmanipulator:building_access_terminal
---

# Building Access Terminal

One handheld directory for this mod's real building control panels. Each terminal remembers up to **64 buildings**, including multiple machines of the same type.

## Use

1. Hold the terminal and **sneak-right-click a controller** to bind its building. Material service ports and pattern assembly ports can be bound individually. A formed entropy engine shell, observation port or valid anchor also binds its internal core.
2. **Right-click the air with the terminal** to open its directory. The list shows building names, dimensions, coordinates and current access status. Browse with the mouse wheel or drag the scrollbar on the right; there are no pages.
3. Select a building to open its original control panel. Use **Buildings** at the top-left to return to the directory and select another building.
4. **Remove** deletes only that terminal's binding. It does not dismantle the building or clear its inventories.

The directory uses the same paper cards and teal styling as the machine panels. Five complete building records are visible on the left. Scroll one row at a time with the mouse wheel or drag the scrollbar to move through the list. Binding totals, ready/unavailable counts and usage tips appear on the right. Long names or locations are abbreviated; hover a building for its full details. The removal button is separate from the opening area.

Supported: Molecular Sequence Rewrite Array, Sequence Array, Sequence Auto Crafter, Matter Fabricator and its four material service ports and pattern assembly port, Star Computation Engine / nine-ring star orrery, Omni Computation Core, and both multiblock and compact Event Horizon Singularity Hub variants. Decoration, structural members without their own panel, and the Transfinite Compute Nexus are not separate directory targets.

## Access requirements

- The player and building must be in the **same dimension**, and the building's **server chunk must already be loaded**. The terminal does not force-load chunks.
- No controller proximity or client render-distance requirement.
- Original ME, energy, formation, research, payment and player permission checks remain in effect. The entropy engine must still be formed before its panel can be opened.
- Inventories, telemetry and actions use the original server menu. Nothing is duplicated and no second machine is created. Moving the terminal, removing the controller or unloading its chunk closes the active connection.

## Crafting

<RecipeFor id="molecularmanipulator:building_access_terminal" />
