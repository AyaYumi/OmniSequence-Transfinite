---
navigation:
  parent: omnisequence-index.md
  title: "Matter Fabrication Well"
  icon: molecularmanipulator:matter_fabrication_controller
  position: 900
item_ids:
- molecularmanipulator:matter_fabrication_controller
- molecularmanipulator:matter_fabrication_casing
- molecularmanipulator:matter_fabrication_glass
- molecularmanipulator:matter_fabrication_coil
- molecularmanipulator:matter_fabrication_stabilizer
- molecularmanipulator:matter_fabrication_core
---

# Matter Fabrication Well

The Matter Fabrication Well makes advanced materials from items and fluids. Build it, research the recipes you need, then feed ingredients through ports or request products through a Pattern Assembly.

<ItemGrid>
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_casing" />
<ItemIcon id="molecularmanipulator:matter_fabrication_glass" />
<ItemIcon id="molecularmanipulator:matter_fabrication_coil" />
<ItemIcon id="molecularmanipulator:matter_fabrication_stabilizer" />
<ItemIcon id="molecularmanipulator:matter_fabrication_core" />
</ItemGrid>

## Requirements

- Reserve 41 × 41 blocks, 27 blocks tall for the complete building.
- Craft the controller below. The controller, basic building parts and input/output ports need no research.
- Check JEI's Complete Matter Fabrication Well page for the full building-material list.
- Prepare a powered ME network with storage and an available channel for the controller. It uses 16 AE/t at idle; processing and research need additional power.

<RecipeFor id="molecularmanipulator:matter_fabrication_controller" />

## Build it

1. Place the controller. Open it and select Project to see the building's position. Clear blocks marked as conflicts.
2. Keep building materials in your inventory or ME storage connected to the controller. Select Build; it uses your inventory first, then ME.
3. Wait for construction to finish. Reopen the controller and use Scan to find missing or conflicting blocks.
4. Hold an [input or output port](matter_fabrication_ports.md) to highlight valid positions, then replace the highlighted casing. Install the input and output types your first recipe needs.
5. Check that the structure count is complete and the controller shows Online before starting research or processing.

There are 44 places for ports or Pattern Assemblies: 24 along the front steps and four groups of five around the central platform. Replace casing at these positions to connect them.

## Make your first product

1. Follow [Fabrication Research](matter_fabrication_research.md) to complete Tier I: AE Foundation. Put research materials in ME storage; your inventory and input ports cannot supply them.
2. To make a Pattern Assembly, put 4 Pattern Providers, 4 Blank Patterns and 1 Crafting Unit into an item input port. Leave room in an item output port.
3. Processing starts automatically. The controller reports Fabricating matter and shows progress. When it finishes, collect 1 Pattern Assembly from the output port.
4. Install the assembly to begin [ME autocrafting](matter_fabrication_pattern_assembly.md). Assembly jobs return products to ME; recipes supplied through ports deliver to their matching outputs.

Research and material processing can run at the same time if you can power both.

## Troubleshooting

| What you see | What to check |
| --- | --- |
| Construction stops | Read the missing-material message, clear projection conflicts and keep the whole building area loaded. |
| Structure incomplete | Use Project and Scan; check that ports replaced highlighted casing positions. |
| AE network offline / insufficient power | Check the selected ME connection, channel and energy supply. A cable or saved wireless binding alone does not guarantee power or an online link. |
| No processing recipe | Check the recipe's research requirement and every ingredient quantity. Research materials belong in ME, processing materials in inputs. |
| Output blocked | Empty the appropriate item/fluid output ports, or free ME storage for assembly jobs. |

## Connections and controls

For wireless ME access, use Remote connection in the right sidebar to bind the well to a Sequence Wireless Hub. Ports and Pattern Assemblies manage this same controller binding. Follow the [wireless setup](sequence_array_controller.md) for connection requirements.

You can also use a Quantum Entangled Singularity in the controller with its partner in a powered AE2 quantum bridge. This alternative adds 512 AE/t and needs a channel. It can supply building materials before the well is complete.

Choose off, reduced or full Effects for this well. This changes visuals only. Shift-click restores each viewer's global setting.

To move the building, click Reclaim Package, then confirm within three seconds. Finish or cancel external ME crafting jobs first. The package retains the building's contents and wireless binding. See [Folded Buildings and Relocation](folded_buildings.md).

Normal removal retains research, accepted work, port contents, assembly patterns and materials, and the controller's wireless binding. Restore the building, ME connection and power to continue. If the wireless hub was moved or replaced, rebind the well.

## Research and production guides

- [Fabrication Research](matter_fabrication_research.md): unlock your first recipes.
- [Input and Output Ports](matter_fabrication_ports.md): feed the well and collect products.
- [Pattern Assembly](matter_fabrication_pattern_assembly.md): request products from an ME terminal.
- [Star Marrow Crystals](star_marrow_crystals.md): start the crystal and star-material supply line.
