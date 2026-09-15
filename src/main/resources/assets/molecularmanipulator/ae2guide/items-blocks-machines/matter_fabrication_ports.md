---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: Well Input and Output Ports
  icon: molecularmanipulator:matter_fabrication_item_input
  position: 1
item_ids:
- molecularmanipulator:matter_fabrication_item_input
- molecularmanipulator:matter_fabrication_item_output
- molecularmanipulator:matter_fabrication_fluid_input
- molecularmanipulator:matter_fabrication_fluid_output
---

# Well Input and Output Ports

<BlockImage id="molecularmanipulator:matter_fabrication_item_input" scale="5" />

The four ports handle item input, item output, fluid input and fluid output. Their crafting recipes use base AE2 materials and require no research. Hold a port to highlight valid sockets on nearby [wells](matter_fabrication_well.md).

<ItemGrid>
<ItemIcon id="molecularmanipulator:matter_fabrication_item_input" />
<ItemIcon id="molecularmanipulator:matter_fabrication_item_output" />
<ItemIcon id="molecularmanipulator:matter_fabrication_fluid_input" />
<ItemIcon id="molecularmanipulator:matter_fabrication_fluid_output" />
</ItemGrid>

## Buffers and pipes

| Port type | Buffer |
| --- | --- |
| Item ports | 16 ordinary slots |
| Fluid ports | 4 independent tanks, each holding up to 2,147,483,647 mB |

External automation may insert into input ports and extract from output ports. These fixed port buffers are separate from the [pattern assembly's](matter_fabrication_pattern_assembly.md) unrestricted-type buffers.

Supply item/fluid ingredients through the interface or pipes and provide output capacity for every result type.

## Return to ME and automatic output

When the controller's network is online, the input port's **Return to ME** button returns buffered materials to it. Anything the network cannot accept stays in the port.

Output ports have an **Auto Output** toggle:

| Setting | Behavior |
| --- | --- |
| Default state | Disabled, with no directions selected |
| When enabled | Choose at least one direction to send items or fluids into adjacent compatible containers |
| Directions | Up, down, north, south, west and east — world directions, independent of the controller's facing |
| Buttons | Show neighboring block icons; hover for names |

Multiple directions may be selected at the same time.

## Manual fluid containers

> Pick up a filled bucket or compatible container with the mouse and right-click the desired tank slot to empty it into that tank. Right-click with an empty container to fill it.

One container is handled per interaction. Incompatible fluids, insufficient fluid or insufficient space prevent the transfer. Both fluid input and output ports support this operation; results from stacked containers need room in the player inventory.

Normal port drops retain their item or fluid contents. Replace them in valid sockets to continue using them.

## Recipes

<Row>
<RecipeFor id="molecularmanipulator:matter_fabrication_item_input" fallbackText="This modpack has no available recipe for this item." />
<RecipeFor id="molecularmanipulator:matter_fabrication_item_output" fallbackText="This modpack has no available recipe for this item." />
</Row>

<Row>
<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_input" fallbackText="This modpack has no available recipe for this item." />
<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_output" fallbackText="This modpack has no available recipe for this item." />
</Row>
