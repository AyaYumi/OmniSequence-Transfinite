---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: "Fabrication Input and Output Ports"
  icon: molecularmanipulator:matter_fabrication_item_input
  position: 1
item_ids:
- molecularmanipulator:matter_fabrication_item_input
- molecularmanipulator:matter_fabrication_item_output
- molecularmanipulator:matter_fabrication_fluid_input
- molecularmanipulator:matter_fabrication_fluid_output
---

# Fabrication Input and Output Ports

Feed the well and move its products into pipes or containers. All four ports use basic AE2 materials and need no research.

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_item_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_item_output" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_output" scale="4" />
</Row>

## Choose a port

| Port | Pipe access | Cache |
| --- | --- | --- |
| Item input | Insert ingredients | 16 slots |
| Item output | Extract products | 16 slots |
| Fluid input | Insert fluids | 4 tanks |
| Fluid output | Extract fluids | 4 tanks |

Each fluid tank holds **2,147,483,647 mB**. Hold a port to see valid positions. The formed [controller](matter_fabrication_well.md) runs the processing recipes.

## Supply and collect

1. Supply each recipe's item and fluid ingredients to the correct inputs.
2. Reserve output space for every product type.
3. Extract with pipes, or enable **Auto Output** and select adjacent destinations.

| Auto Output setting | Behavior |
| --- | --- |
| Default | Disabled; all six directions unselected |
| To start | Enable the switch and select at least one direction |
| Destinations | Up, down, north, south, west, east; multiple selections allowed |

Directions follow the world axes. Hover a direction button to identify its neighboring block.

## Move fluids by hand

<ItemGrid>
<ItemIcon id="minecraft:bucket" />
<ItemIcon id="minecraft:water_bucket" />
</ItemGrid>

Carry a filled container with the cursor and **right-click a fluid slot** to fill it. Carry an empty container to drain it. Each action handles one container; insufficient fluid, incompatible fluid, or insufficient space prevents the transfer.

## Return and relocate

**Return to ME** sends input cache contents into the controller's network. Unaccepted amounts remain in the port. Removing a port normally preserves its contents in the dropped block.

## Recipes

<Row>
<RecipeFor id="molecularmanipulator:matter_fabrication_item_input" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<RecipeFor id="molecularmanipulator:matter_fabrication_item_output" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
</Row>

<Row>
<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_input" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_output" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
</Row>
