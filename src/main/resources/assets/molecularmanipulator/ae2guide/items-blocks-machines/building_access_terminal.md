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

The Building Access Terminal is a handheld directory for machine control panels. Sneak-right-click a controller to bind it, then open its panel from anywhere in the same dimension while its chunk is loaded. Each terminal remembers up to 64 targets, including multiple machines of the same type.

<Row>
<ItemImage id="molecularmanipulator:building_access_terminal" scale="5" />

<BlockImage id="molecularmanipulator:matter_fabrication_controller" scale="4" />

<BlockImage id="molecularmanipulator:event_horizon_singularity_hub" scale="4" />
</Row>

## Requirements

- Craft a Building Access Terminal using the recipe below.
- Visit the controller you want to bind. It must have a usable control panel; a Star Computation Engine must be formed.
- Keep the target's chunk loaded. The terminal does not load chunks.

## Bind and open a building

1. Hold the terminal and sneak-right-click the controller. A binding message confirms that it has joined this terminal's list.
2. Right-click the air with the terminal to open the directory. It lists each building's name, dimension and coordinates. Browse with the mouse wheel or right-side scrollbar. Hover an abbreviated name or location for full details.
3. Select an available building to open its original panel. You do not need to be near its controller, and client render distance does not limit access.
4. To switch machines, select Buildings at the top-left of the panel.

Slots and buttons operate the original machine. Its ME, energy, research, formation and player permission requirements still apply.

Remove deletes only this terminal's record. It does not dismantle the machine, clear its contents or change another terminal's bindings. Keep the terminal in its original inventory slot while operating; moving it closes the current connection.

## Supported buildings

<ItemGrid>
<ItemIcon id="molecularmanipulator:molecular_manipulator" />
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:star_computation_engine_core" />
<ItemIcon id="molecularmanipulator:omni_computation_controller" />
<ItemIcon id="molecularmanipulator:compact_singularity_hub" />
</ItemGrid>

- Molecular Sequence Rewrite Array, Sequence Array and Molecular Auto Crafter.
- Matter Fabrication Well, with its four item/fluid service ports and Pattern Assembly also bindable separately.
- Star Computation Engine / Star Orrery: a formed central casing, observation port or valid Star Anchor can bind its core.
- Multiblock Omni Computation Core, and both multiblock and compact Singularity Hubs.

Ordinary decoration, structural parts without their own panel and the Transfinite Compute Nexus are not separate targets.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Empty directory | First sneak-right-click a supported controller with the terminal. A normal right-click does not bind it. |
| Building unavailable | Check the dimension, loaded target chunk, building condition and your permissions. |
| Panel closes immediately | Keep the terminal in place. Check whether the controller was removed or its chunk unloaded. |
| Old record stopped working after relocation | Visit and bind the new position, then remove the old record. |
| Already at 64 records | Remove an unused target before binding another building. |

## Crafting recipe

<RecipeFor id="molecularmanipulator:building_access_terminal" />
