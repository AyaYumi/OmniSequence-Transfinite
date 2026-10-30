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

The Matter Fabrication Well is a multiblock machine for material processing and research. Input and output ports handle supplied ingredients, while Pattern Assemblies let it execute ME autocrafting requests.

<ItemGrid>
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_casing" />
<ItemIcon id="molecularmanipulator:matter_fabrication_glass" />
<ItemIcon id="molecularmanipulator:matter_fabrication_coil" />
<ItemIcon id="molecularmanipulator:matter_fabrication_stabilizer" />
<ItemIcon id="molecularmanipulator:matter_fabrication_core" />
</ItemGrid>

## Structure

The well occupies **41 × 41 blocks** and is **27 blocks tall**. Its controller, basic structure blocks and ports require no research. JEI's structure page lists all required materials.

1. Place the controller and enable **Projection** to check missing blocks and obstructions.
2. Gather the listed materials, clear obstructions and select **Build**. Construction uses your inventory before the connected ME storage.
3. Install the required [ports](matter_fabrication_ports.md) in service positions. Hold a port to see valid positions.
4. Connect ME and power, then check formation and network status in the controller.

There are **44 service positions**: 24 on the front steps and four groups of five around the central platform. [Pattern Assemblies](matter_fabrication_pattern_assembly.md) also use these positions.

## Operation

| Workflow | How to use it |
| --- | --- |
| Material processing | Supply items and fluids through input ports; collect products from output ports |
| [Research](matter_fabrication_research.md) | Store research materials in ME, select a branch and start it |
| ME autocrafting | Complete tier 1, install a Pattern Assembly and insert matching processing patterns |

Research and processing can run together. If work cannot start, check the structure, power, research requirements and output capacity.

## Machine recipe compatibility

The well can also run recipes from these AE machines. A machine appears in research only when it is installed and at least one of its recipes can be imported; each machine gets its own branch.

| Machine | Supported recipes | Notes |
| --- | --- | --- |
| AE2 Inscriber | Inscriber recipes | INSCRIBE mode treats both presses as reusable catalysts; PRESS mode consumes every listed item |
| AE2 Charger | Charger recipes | — |
| ExtendedAE Extended Inscriber | The same AE2 inscriber recipes | Shares AE2's recipe type, but has its own research branch |
| ExtendedAE Extended Charger | The same AE2 charger recipes | Shares AE2's recipe type, but has its own research branch |
| ExtendedAE Circuit Slicer | Circuit Slicer recipes | Keeps exact item counts and mB fluid amounts |
| AdvancedAE Reaction Chamber | Reaction Chamber recipes | Item and fluid amounts are kept for inputs and outputs; pack-modified recipes included |

**How to unlock.** Complete tier 1 AE fabrication first, then start the machine's branch from [Research](matter_fabrication_research.md). Researching a branch spends one of that machine, takes 30 seconds (600 ticks) at 512 AE/t, and unlocks that machine's recipes. Later rounds follow the usual depth progression for more parallelism and speed.

**Inscriber presses.** In INSCRIBE mode the top and bottom presses are reusable catalysts. Put them on the [Pattern Assembly](matter_fabrication_pattern_assembly.md) Catalysts page instead of encoding them in the processing pattern. In PRESS mode every listed item, including presses, is consumed and must be encoded.

**Not imported.** The Crystal Fixer in this Forge version repairs world blocks by randomly consuming charged quartz. It has no fixed recipe, so its repair operation is not imported and random repair is not supported here. The older Crystal Assembler belongs to another game version and is not present.

## Quantum connection

Place one **Quantum Entangled Singularity** in the controller's quantum slot and its partner in a powered AE2 quantum bridge to connect remote ME storage. The link requires an additional **512 AE/t and one channel** and can supply construction materials before the well is formed.

## Dismantling and relocation

**Dismantle** keeps the controller and returns structure materials to ME first, then your inventory. If both destinations are full, clear space to resume recovery.

Normal controller removal retains research progress, accepted work and the quantum slot. Ports retain their caches, and assemblies retain patterns and processing materials. Restore the structure, network and power to continue.

When the server enables multiblock chunk loading, required chunks stay loaded during construction and while formed. Occupied chunks prevent natural mob spawning; spawners, breeding and existing creatures are unaffected.

## Recipes

<RecipeFor id="molecularmanipulator:matter_fabrication_controller" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

<SubPages icons={true} />
