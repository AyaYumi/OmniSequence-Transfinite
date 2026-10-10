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

Input and output ports supply the [Fabrication Well](matter_fabrication_well.md) and receive its products. All four types require no research and must be installed in the well's service positions.

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_item_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_item_output" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_output" scale="4" />
</Row>

## Port types

| Port | Use | Capacity |
| --- | --- | --- |
| Item input | Receive item ingredients | 16 slots |
| Item output | Hold item products | 16 slots |
| Fluid input | Receive fluid ingredients | 4 tanks |
| Fluid output | Hold fluid products | 4 tanks |

Each tank holds **2,147,483,647 mB**. Hold a port to see valid positions. The formed controller uses materials from its input ports to run unlocked recipes.

## Pipes and automatic output

Connect item pipes to item inputs and fluid pipes to fluid inputs. Install the appropriate outputs for every product, leave room for them, and extract products with pipes.

Outputs can also send products to adjacent containers. Enable **Auto Output** and select at least one direction. The six directions use world axes and allow multiple selections. Hover a direction button to identify its neighbor. Automatic output is disabled by default.

## Moving fluids by hand

Pick up a filled container with the cursor and **right-click a fluid slot** to fill it. Use an empty container to drain it. Each action handles one container and requires sufficient fluid or capacity.

## Returning materials

The input port's **Return to ME** button sends unused ingredients to the controller's ME network. Materials that do not fit remain in the port. Normal port removal carries its cache with the block.

If processing cannot start, check research unlocks, all required inputs and capacity for every output. Research materials must be in ME storage; input ports do not supply research.

## Recipes

<RecipeFor id="molecularmanipulator:matter_fabrication_item_input" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<RecipeFor id="molecularmanipulator:matter_fabrication_item_output" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_input" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_output" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
