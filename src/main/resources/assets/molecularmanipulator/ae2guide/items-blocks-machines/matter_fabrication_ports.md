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

Ports let you put ingredients into the [Fabrication Well](matter_fabrication_well.md) and collect finished products, by hand or with pipes.

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_item_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_item_output" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_output" scale="4" />
</Row>

From left to right: item input, item output, fluid input, fluid output.

## Requirements

All four ports can be crafted without research. You need a complete well, a powered ME connection and an unlocked processing recipe.

| Port | Put here / collect here | Capacity |
| --- | --- | --- |
| Item input | Solid ingredients | 16 slots |
| Item output | Solid products | 16 slots |
| Fluid input | Fluid ingredients | 4 tanks |
| Fluid output | Fluid products | 4 tanks |

Each tank holds 2,147,483,647 mB. Install a port for every input and output type in your recipe, including a fluid output port for fluid products.

## Run a recipe

1. Hold a port in either hand. Replace casing marked by a cyan outline; orange marks an occupied position.
2. Install the inputs and outputs your recipe needs. Ports share 44 positions with Pattern Assemblies and must replace casing at those positions. A connected port reports Controller AE network connected.
3. Put the full recipe quantities into the item and fluid inputs. Pipes can also insert ingredients.
4. Leave output space and power the well. The controller automatically starts a matching unlocked recipe.
5. When a batch finishes, its ingredients are consumed and products appear in the matching outputs. Collect them by hand or extract them with item/fluid pipes.

Research takes materials from ME storage. Recipes that consume Star Signal Flow need a [Pattern Assembly](matter_fabrication_pattern_assembly.md), because signal cannot go into an item or fluid port.

### Move fluids by hand

Pick up a filled fluid container with your cursor, then right-click a tank to fill it. Right-click with an empty container to take fluid out. Each click handles one container and needs enough fluid or free tank space.

### Automatic output

1. Place a suitable receiving container beside the output port.
2. Open the port and change Auto: OFF to Auto: ON.
3. Open Output sides · select and enable the side facing that container. You can select several sides.

The direction diagram follows the port's facing: the center is front and the lower-right is back. Hover a side to check the pictured neighboring block. Auto output is off by default and only sends what the destination can accept.

## ME connection and returning inputs

The right sidebar's Remote connection manages the well controller's wireless binding. It does not give the port a separate link. See [Sequence Wireless Hub setup](sequence_array_controller.md).

To clear unused inputs, select All to AE. Materials that do not fit in ME stay in the port. Normal removal keeps the port's contents with the block; the wireless binding belongs to the controller and is retained through its normal recovery or building packaging.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Port reports disconnected | Check the well's structure and ME connection, and that the port replaced highlighted casing. |
| Ingredients sit in the input | Check research, exact ingredient types and quantities, power, and room for all products. |
| Auto output moves nothing | Turn it on, select an output side and check that the neighbor accepts that item or fluid. |
| Fluid container does nothing | Hold it with the cursor and right-click the tank; check fluid type and available capacity. |
| All to AE leaves materials behind | Free compatible ME storage and restore the network connection. |

## Port recipes

<RecipeFor id="molecularmanipulator:matter_fabrication_item_input" />

<RecipeFor id="molecularmanipulator:matter_fabrication_item_output" />

<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_input" />

<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_output" />

## ME orders and research

Use a [Pattern Assembly](matter_fabrication_pattern_assembly.md) to order products from ME. [Fabrication Research](matter_fabrication_research.md) unlocks more processing recipes.
