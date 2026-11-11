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

The Matter Fabrication Well turns ingredients into advanced materials; its research page unlocks more recipes, and a Pattern Assembly lets your ME network order those products automatically.

<ItemGrid>
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_casing" />
<ItemIcon id="molecularmanipulator:matter_fabrication_glass" />
<ItemIcon id="molecularmanipulator:matter_fabrication_coil" />
<ItemIcon id="molecularmanipulator:matter_fabrication_stabilizer" />
<ItemIcon id="molecularmanipulator:matter_fabrication_core" />
</ItemGrid>

## Before you start

- Reserve **41 × 41 blocks, 27 blocks tall**. This is a building, not just the controller block.
- Craft the controller below. The controller, basic building parts and input/output ports need no research.
- Use JEI's **Complete Matter Fabrication Well** page for the full building-material list.
- Prepare a powered ME network with storage and an available channel for the controller. It uses **16 AE/t** at idle; processing and research need additional power.

<RecipeFor id="molecularmanipulator:matter_fabrication_controller" />

## Build it

1. **Place the controller.** Open it and select **Project** to see the building's position. Remove blocks marked as conflicts before construction.
2. **Supply building materials.** Keep them in your inventory or in ME storage connected to the controller. Select **Build**; it uses your inventory first, then ME.
3. **Wait for construction to finish.** Reopen the controller and use **Scan** to check any missing or conflicting blocks.
4. **Install ports.** Hold an [input or output port](matter_fabrication_ports.md) to highlight valid positions, then replace the highlighted casing blocks. Install the input and output types your first recipe needs.
5. **Check the network.** The controller must show **Online** before you research or process materials.

There are **44 places for ports or Pattern Assemblies**: 24 along the front steps and four groups of five around the central platform. These devices replace casing at those positions; placing them beside the building does not connect them.

## Make your first product

1. Follow [Fabrication Research](matter_fabrication_research.md) to complete **Tier I: AE Foundation**. **Research takes materials from ME storage, not your backpack or the input ports.**
2. For a first processing test, put **4 Pattern Providers, 4 Blank Patterns and 1 Crafting Unit** into an item input port. Leave room in an item output port.
3. The well automatically processes them into **1 Pattern Assembly**. No separate processing-start button is needed.
4. Install the assembly to begin [ME autocrafting](matter_fabrication_pattern_assembly.md), or keep using ports for item/fluid recipes.

Research and material processing can run at the same time, provided you can power both.

## What success looks like

The structure count is complete and the network shows **Online**. While processing, the controller reports **Fabricating matter** and its progress advances. The test assembly appears in an item output port; later, assembly-driven jobs return their products to ME.

## If it does not work

| What you see | What to check |
| --- | --- |
| Construction stops | Read the missing-material message, clear projection conflicts and keep the whole building area loaded. |
| Structure incomplete | Use **Project** and **Scan**; check that ports replaced highlighted casing positions. |
| AE network offline / insufficient power | Check the controller's cable, channel and ME energy supply. A connected cable alone does not guarantee enough power. |
| No processing recipe | Check the recipe's research requirement and every ingredient quantity. Research materials belong in ME, processing materials in inputs. |
| Output blocked | Empty the appropriate item/fluid output ports, or free ME storage for assembly jobs. |

## Useful controls

- **Remote building materials:** put one Quantum Entangled Singularity in the controller and its partner in a powered AE2 quantum bridge. The link adds **512 AE/t** and needs a channel. Before the well is complete, it can already supply building materials.
- **Effects:** choose off, reduced or full effects for this well. This does not change production. Shift-click restores each viewer's global setting.
- **Reclaim Package:** click once, then confirm within three seconds to pack the building and its contents. Finish or cancel external ME crafting jobs first. See [Folded Buildings and Relocation](folded_buildings.md).

Normal removal retains the controller's research and accepted work, ports' contents, and assemblies' patterns and materials. Restore the building, network and power to continue.

## Next steps

- [Fabrication Research](matter_fabrication_research.md) — unlock your first recipes.
- [Input and Output Ports](matter_fabrication_ports.md) — feed the well and collect products.
- [Pattern Assembly](matter_fabrication_pattern_assembly.md) — request products from an ME terminal.
- [Star Marrow Crystals](star_marrow_crystals.md) — start the crystal and star-material supply line.
