---
navigation:
  title: "OmniSequence: Transfinite"
  icon: molecularmanipulator:matter_fabrication_controller
  position: 80
---

# OmniSequence: Transfinite

Start with **one processing well**, then build an AE2 base that prepares ingredients, crafts in batches and keeps supplies stocked. This guide puts your next action before machine details. You do not need to memorize every machine name first.

## First time? Start here

![Connect the well, finish Tier I research, then install a Pattern Assembly for ME orders](assets/diagrams/first-steps-en_us.png)

1. **Get a working ME warehouse.** Connect power, cable, storage and a terminal, then store your ingredients. New to AE2? Start with [AE2 Getting Started](ae2:getting-started.md).
2. **Build a Matter Fabrication Well.** Craft its controller and reserve **41×41 blocks, 27 blocks high**. Use projection to find missing blocks, prepare the listed materials, build, and check formation and ME status. [Build your first well](items-blocks-machines/matter_fabrication_well.md).
3. **Complete Tier I: AE Foundation.** Inspect the research cost, store materials in the connected ME network, and start. **Your inventory and input ports do not supply research materials.** [First research steps](items-blocks-machines/matter_fabrication_research.md).
4. **Let the well accept ME orders.** Install a Pattern Assembly, encode processing patterns for well recipes and insert them. Autocrafting also needs ingredients, an available CPU and space for output. [Use a Pattern Assembly](items-blocks-machines/matter_fabrication_pattern_assembly.md).

One research round is enough to start; you do not need all nine rounds first. AdvancedAE is optional for launching the mod, but default Quantum Materials, Computation and later research need it. Check that your pack includes it if you want the full route.

## Patterns, CPUs and storage: who does what?

![The terminal requests an order, the CPU plans steps, the pattern guides a crafting machine, and ME stores ingredients and products](assets/diagrams/me-crafting-en_us.png)

- **ME storage** is the warehouse for ingredients and products.
- A **pattern** is a recipe card saying what goes in and what comes out. Make it in a Pattern Encoding Terminal. Different machines accept different pattern types.
- A **crafting CPU** accepts orders and arranges steps. It cannot create missing ingredients or replace the machine doing the work.
- A **channel** is a device connection allowance. A cabled machine may still be offline if it has no channel.

The diagram shows roles, not placement dimensions. The Sequence Array combines a pattern library with actual crafting in one building. Follow each machine's page for its connections.

## Choose a machine by the problem you want to solve

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_controller" scale="4" />

<BlockImage id="molecularmanipulator:molecular_center_controller" scale="4" />

<BlockImage id="molecularmanipulator:omni_computation_controller" scale="4" />

<BlockImage id="molecularmanipulator:event_horizon_singularity_hub" scale="4" />
</Row>

Left to right: well, Sequence Array, computation core and Singularity Hub controllers. These buildings have different jobs; they are not four versions you must replace in order.

| I want to… | Read this |
| --- | --- |
| Research and process new materials | [Fabrication Well](items-blocks-machines/matter_fabrication_well.md), [Research route](items-blocks-machines/matter_fabrication_research.md) |
| Pipe items/fluids or encode a well recipe from JEI | [Input and Output Ports](items-blocks-machines/matter_fabrication_ports.md), [Pattern Assembly](items-blocks-machines/matter_fabrication_pattern_assembly.md) |
| Store many patterns and keep products stocked | [Sequence Array](items-blocks-machines/sequence_array_controller.md), [Molecular Auto Crafter](items-blocks-machines/molecular_auto_crafter.md) |
| Craft, smith and stonecut in batches from one block | [Molecular Sequence Rewrite Array](items-blocks-machines/molecular_sequence_rewrite_array.md) |
| Add batch crafting to an ExtendedAE Assembler Matrix | [Matrix Rewrite Core](items-blocks-machines/assembler_matrix_sequence_rewrite_core.md) |
| Accept larger orders, and multiple orders later | [Omni Computation Core](items-blocks-machines/omni_computation_core.md), [Transfinite Compute Nexus](items-blocks-machines/transfinite_compute_nexus.md) |
| Boost machines on the same network | [Star Computation Engine: Nine-Ring Orrery](items-blocks-machines/star_computation_engine.md) |
| Grow crystals, produce and store Star Signal Flow | [Star Marrow Crystals](items-blocks-machines/star_marrow_crystals.md), [Star Sea Calculation Mainframe](items-blocks-machines/star_sea_calculation_mainframe.md), [Signal Storage](items-blocks-machines/star_signal_flow.md) |
| Generate raw materials or copy a sample item | [Singularity Hub](items-blocks-machines/event_horizon_singularity_hub.md), [Resource Collection](items-blocks-machines/singularity_resource_collection.md), [Matter Duplication](items-blocks-machines/singularity_matter_duplication.md) |
| Try the hub in a small creative-mode machine | [Compact Singularity Hub](items-blocks-machines/compact_singularity_hub.md) |
| Transfer dropped items and non-player creatures | [Black and White Holes](items-blocks-machines/black_and_white_holes.md) |
| Deploy a whole building or move a working machine | [Folded Buildings and Relocation](items-blocks-machines/folded_buildings.md), [Building Access Terminal](items-blocks-machines/building_access_terminal.md) |
| Walk on walls, make lifts or explore a personal simulation | [Gravity Crystals and Ghost Matter](items-blocks-machines/nomai_materials.md), [Simulation Lantern](items-blocks-machines/simulation_lantern.md) |

## Two different kinds of research

**Well research** unlocks that well's processes. Later rounds increase how many recipe copies it can process together. Machine integration research needs **three rounds of each Tier III branch**. Crystallization and Exotic Matter are optional branches.

**Star research** is a separate team permission. Bring a complete orrery to **L3**, then explicitly unlock free **Single-Block Integration** on Recipe Tree. By default it adds a gate only to the Rewrite Array and Compute Nexus. The orrery is not a standalone 3×3×3 cube: it needs both the complete center and foundation.

## Finding things in this guide

Hover an inventory item or JEI entry and press the guide key, default **G**; associated blocks have entries too. Click item pictures to find related descriptions and recipes. Drag structure diagrams to rotate them. If G does nothing, check the guide binding under **Options → Controls**.

This book describes default gameplay. Packs can change costs and requirements; use JEI, research screens and machine status for your world's current rules. If something stops, start with that page's troubleshooting table.

<SubPages icons={true} />
