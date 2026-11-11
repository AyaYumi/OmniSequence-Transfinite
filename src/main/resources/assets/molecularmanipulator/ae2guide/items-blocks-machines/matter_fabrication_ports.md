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

## Before you start

All four ports can be crafted without research. You need a complete well, a powered ME connection and an unlocked processing recipe.

| Port | Put here / collect here | Capacity |
| --- | --- | --- |
| Item input | Solid ingredients | 16 slots |
| Item output | Solid products | 16 slots |
| Fluid input | Fluid ingredients | 4 tanks |
| Fluid output | Fluid products | 4 tanks |

Each tank holds **2,147,483,647 mB**. Choose ports for **every input and output** in your recipe; a recipe with a fluid product also needs a fluid output port.

## Run a recipe

1. **Find an installation position.** Hold a port in either hand. Replace casing marked by a cyan outline; an orange outline marks an occupied position.
2. **Install inputs and outputs.** Ports use the same 44 positions as Pattern Assemblies. They do not work when simply placed beside the well.
3. **Add the complete recipe quantities.** Put items into the item input, and fill the fluid input if needed. Pipes can insert the same way.
4. **Leave output space and power the well.** The controller automatically finds an unlocked recipe and begins processing.
5. **Collect the products.** Take items from the output port, or use item/fluid pipes to extract them.

**Research does not use these inputs.** Research materials must be stored in ME. Recipes that consume Star Signal Flow need a [Pattern Assembly](matter_fabrication_pattern_assembly.md), because signal cannot be put into an item or fluid port.

### Move fluids by hand

Pick up a filled fluid container with your cursor, then **right-click a tank** to fill it. Right-click with an empty container to take fluid out. Each click handles one container and needs enough fluid or free tank space.

### Let an output port empty itself

1. Place a suitable receiving container beside the output port.
2. Open the port and change **Auto: OFF** to **Auto: ON**.
3. Open **Output sides · select** and enable the side facing that container. You can select several sides.

The direction diagram follows the port's facing: the center is front and the lower-right is back. Hover a side to check the pictured neighboring block. Auto output is off by default and only sends what the destination can accept.

## What success looks like

The port reports **Controller AE network connected**. When a batch completes, input quantities fall and products appear in the matching outputs. With automatic output enabled, those products move into the selected adjacent container.

To clear unused input materials, select **All to AE** in the input port. Materials that do not fit in ME remain in the port. Normal removal keeps the port's contents with the block.

## If it does not work

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

## Next steps

Use a [Pattern Assembly](matter_fabrication_pattern_assembly.md) to order products from ME, or open [Fabrication Research](matter_fabrication_research.md) to unlock more processing recipes.
