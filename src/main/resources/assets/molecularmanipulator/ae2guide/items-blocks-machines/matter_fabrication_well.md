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

The Matter Fabrication Well combines material processing, AE autocrafting and research, and it starts this mod's progression.
Craft its controller, structural blocks and ports from base AE2 materials, then research advanced materials and machines.

<ItemGrid>
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_casing" />
<ItemIcon id="molecularmanipulator:matter_fabrication_glass" />
<ItemIcon id="molecularmanipulator:matter_fabrication_coil" />
<ItemIcon id="molecularmanipulator:matter_fabrication_stabilizer" />
<ItemIcon id="molecularmanipulator:matter_fabrication_core" />
<ItemIcon id="molecularmanipulator:matter_fabrication_item_input" />
<ItemIcon id="molecularmanipulator:matter_fabrication_item_output" />
<ItemIcon id="molecularmanipulator:matter_fabrication_fluid_input" />
<ItemIcon id="molecularmanipulator:matter_fabrication_fluid_output" />
<ItemIcon id="molecularmanipulator:matter_fabrication_pattern_assembly" />
</ItemGrid>

> The [Pattern Assembly](matter_fabrication_pattern_assembly.md) is produced in the well after the first research unlock.

## At a glance

| Property | Value |
| --- | --- |
| **Footprint** | 41 × 41, 27 blocks tall |
| **Starts at** | Stage 0 — no research required |
| **Service sockets** | 24 front terrace + 20 central collar |
| **Supplies research with** | Items in the controller's ME network |

## Construction and connection

1. Reserve a 41×41 footprint and 27 blocks of height for the current pearl-white fabrication chamber. Face the controller toward the operating side.
2. Open the controller and enable its projection to inspect missing or conflicting blocks. The JEI structure page provides layers and a material list.
3. Clear conflicts and use **Build**. Construction takes materials from the player's inventory first, then the connected ME Network.
4. Install [ports](matter_fabrication_ports.md) or pattern assemblies in valid service sockets. Holding one highlights permitted positions on nearby wells, including the usable front terrace row.
5. Connect the formed controller to a powered ME Network. Processing uses ports or pattern assemblies; research draws materials from the controller's network.

### Service sockets

In 2.0.3 a service block fits **24 front terrace** positions and **20 central collar** positions (five on each of four sides) — 44 in total.

| | Count | Where |
| --- | --- | --- |
| Front terrace | 24 | The row in front of the chamber |
| Central collar | 20 | Five positions on each of the four collar sides |
| Former outer positions | 0 | The nine old outer service positions are no longer accepted |

> Upgrading an older well? Move those service blocks and restore the vacated positions according to the projection.

### Quantum link

The quantum slot accepts one half of a paired entangled singularity; place the other half in a powered AE2 Quantum Ring.

* Before formation, the link can retrieve remote construction materials.
* After formation, it can connect the working network.
* The link adds 512 AE/t of power use and requires a channel.

Resolve conflicting wired and remote networks before using the link.

## Processing and research

A new controller starts at stage 0. The [first research](matter_fabrication_research.md) unlocks AE material processing, materials used by later research, and the pattern assembly recipe. Stage two has separate Sequence Array and Omni-Computation branches. By default, one completion of stage one is sufficient, and both branches may run together.

| Use case | How to feed it |
| --- | --- |
| Ordinary processing | Appropriate item/fluid input ports, plus output ports for the results |
| AE autocrafting | Matching processing patterns in a pattern assembly |

JEI and guide recipe panels show the research that unlocks each processing recipe. Research permissions and bonuses belong to this controller.

## Operation and persistence

Production displays rotating star rings, converging material streams and manufacturing scan layers. Independent research constellations appear above them, including simultaneous branches.

> Paused, prerequisite-blocked, offline or unpowered research stops sending light pulses. Custom stages receive an automatic visual preset.

* Only the current well blueprint is retained; experimental development layouts are no longer recognized.
* The controller force-loads the required chunks while formed or during construction and dismantling. Tickets are released when no valid structure or operation needs them.
* Chunk loading does not supply materials or energy.

**Dismantle** recovers the structure from top to bottom while keeping the controller. Materials go to ME first and then the player's inventory; full destinations pause the saved operation.

Normal block drops retain stored contents:

| Block | Keeps |
| --- | --- |
| Controller | Research progress and owned tasks |
| Ports | Their buffers |
| Pattern assemblies | Their own patterns and batches |

Replaced machines still need a valid structure, network and power to resume.

## Natural spawning protection

While formed or performing construction, dismantling or a structure update, this multiblock blocks natural spawning throughout the full height of its occupied chunks, including monsters, animals, aquatic mobs and bats. Patrol and reinforcement spawns are also blocked.

Spawners, spawn eggs, breeding, commands and existing mobs are unaffected. Protection does not require AE power; when no structure or operation owns a chunk, spawning there returns to normal.

## Controller recipe

<RecipeFor id="molecularmanipulator:matter_fabrication_controller" fallbackText="This modpack has no available recipe for this item. Check JEI or the pack's instructions." />

## Further reading

<SubPages icons="true" />
