---
navigation:
  parent: omnisequence-index.md
  title: Matter Fabrication Well
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

<BlockImage id="molecularmanipulator:matter_fabrication_controller" scale="8" />

The Matter Fabrication Well combines material processing, AE autocrafting and research. It starts this mod's progression:
craft its controller, structural blocks and ports with base AE2 materials, then research advanced materials and machines.
The [Pattern Assembly](matter_fabrication_pattern_assembly.md) is produced in the well after the first research unlock.

## Construction and connection

1. Reserve a 41×41 footprint and 27 blocks of height for the current pearl-white fabrication chamber. Face the controller toward the operating side.
2. Open the controller and enable its projection to inspect missing or conflicting blocks. The JEI structure page provides layers and a material list.
3. Clear conflicts and use **Build**. Construction takes materials from the player's inventory first, then the connected ME Network.
4. Install [ports](matter_fabrication_ports.md) or pattern assemblies in valid service sockets. Holding one highlights permitted positions on nearby wells, including the usable front terrace row.
5. Connect the formed controller to a powered ME Network. Processing uses ports or pattern assemblies; research draws materials from the controller's network.

The quantum slot accepts one half of a paired entangled singularity; place the other half in a powered AE2 Quantum Ring.
Before formation, the link can retrieve remote construction materials. After formation, it can connect the working network.
The link adds 512 AE/t of power use and requires a channel. Resolve conflicting wired and remote networks before using the link.

## Processing and research

A new controller starts at stage 0. The [first research](matter_fabrication_research.md) unlocks AE material processing,
materials used by later research, and the pattern assembly recipe. Stage two has separate Sequence Array and Omni-Computation
branches. By default, one completion of stage one is sufficient; both branches may run together.

For ordinary processing, supply the appropriate item/fluid input ports and provide output ports for the results.
For AE autocrafting, insert matching processing patterns into a pattern assembly. JEI and guide recipe panels show the
research that unlocks each processing recipe. Research permissions and bonuses belong to this controller.

## Operation and persistence

Production displays rotating star rings, converging material streams and manufacturing scan layers. Independent research
constellations appear above them, including simultaneous branches. Paused, prerequisite-blocked, offline or unpowered research
stops sending light pulses. Custom stages receive an automatic visual preset.

Only the current well blueprint is retained; experimental development layouts are no longer recognized.
The controller force-loads the required chunks while formed or during construction and dismantling.
Tickets are released when no valid structure or operation needs them. Chunk loading does not supply materials or energy.

**Dismantle** recovers the structure from top to bottom while keeping the controller. Materials go to ME first and then the
player's inventory; full destinations pause the saved operation. Normal block drops retain stored contents: the controller
keeps its research and owned tasks, ports keep their buffers, and assemblies keep their own patterns and batches.
Replaced machines still need a valid structure, network and power to resume.

## Controller recipe

<RecipeFor id="molecularmanipulator:matter_fabrication_controller" fallbackText="This modpack has no available recipe for this item. Check JEI or the pack's instructions." />

## Further reading

<SubPages />
