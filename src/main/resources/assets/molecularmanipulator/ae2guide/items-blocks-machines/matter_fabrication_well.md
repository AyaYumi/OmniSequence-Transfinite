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

The starting point for material processing, research, and ME autocrafting. Build the well, then research the machines that follow.

## Prepare the site

<ItemGrid>
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_casing" />
<ItemIcon id="molecularmanipulator:matter_fabrication_glass" />
<ItemIcon id="molecularmanipulator:matter_fabrication_coil" />
<ItemIcon id="molecularmanipulator:matter_fabrication_stabilizer" />
<ItemIcon id="molecularmanipulator:matter_fabrication_core" />
</ItemGrid>

| Preparation | Requirement |
| --- | --- |
| Space | 41 × 41 blocks, 27 blocks tall |
| Starting tier | Tier 0; controller and basic ports need no research |
| Materials | JEI structure page and controller projection |

## Build the well

1. Place the controller facing your work area. Enable **Projection** to find missing blocks and obstructions.
2. Gather the JEI material list, clear obstructions, and select **Build**.
3. Construction takes player inventory materials first, then materials from the connected ME network.
4. Install [input and output ports](matter_fabrication_ports.md) in highlighted service positions. Power the completed well through ME.

There are **44 service positions**: 24 on the front steps and four groups of five around the central platform. Hold a port to see them.

## Choose a workflow

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_item_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_pattern_assembly" scale="4" />
</Row>

| Work | Start here |
| --- | --- |
| [Processing](matter_fabrication_ports.md) | Supply item/fluid inputs and leave output capacity |
| [Research](matter_fabrication_research.md) | Select a branch; put its materials in the controller's ME network |
| [ME autocrafting](matter_fabrication_pattern_assembly.md) | Unlock the Pattern Assembly at tier 1 and insert matching processing patterns |

Research and processing can run together. JEI and the live recipe panels show the current ingredients, duration, power, and research requirements.

## Remote access and recovery

<ItemGrid>
<ItemIcon id="ae2:quantum_entangled_singularity" />
<ItemIcon id="ae2:quantum_ring" />
<ItemIcon id="ae2:quantum_link" />
</ItemGrid>

Insert one **Quantum Entangled Singularity** in the quantum slot and its partner in a powered AE2 quantum bridge. The connection also works for construction before formation. It costs an additional **512 AE/t and one channel**.

**Dismantle** recovers the structure from top to bottom and keeps the controller. Returns go to ME first, then the player; recovery pauses when both are full.

| Removed block | Contents carried with it |
| --- | --- |
| Controller | Research, accepted jobs, and quantum slot |
| Ports | Their item or fluid caches |
| Pattern Assembly | Patterns, ingredients, and processing batches |

Restore the structure, network, and power to resume. The multiblock chunk-loading setting keeps required chunks loaded while formed or under construction. Occupied chunks block natural spawning; spawners, breeding, and existing mobs are unaffected.

## Controller recipe

<RecipeFor id="molecularmanipulator:matter_fabrication_controller" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />

## Related pages

<SubPages icons={true} />
