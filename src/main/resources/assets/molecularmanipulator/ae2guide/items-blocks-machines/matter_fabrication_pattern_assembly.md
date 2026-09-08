---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: Matter Fabrication Pattern Assembly
  icon: molecularmanipulator:matter_fabrication_pattern_assembly
  position: 2
item_ids:
- molecularmanipulator:matter_fabrication_pattern_assembly
---

# Matter Fabrication Pattern Assembly

<BlockImage id="molecularmanipulator:matter_fabrication_pattern_assembly" scale="8" />

The pattern assembly submits AE autocrafting work to the [Matter Fabrication Well](matter_fabrication_well.md).
Its well recipe unlocks after the first completion of [stage-one research](matter_fabrication_research.md).

## Installation and patterns

1. Hold the assembly to preview valid positions and install it in a well service socket.
2. When the controller is formed and connected, the assembly automatically joins its network. AE power and channel requirements still apply.
3. Insert AE2 processing patterns matching well recipes, including all required resource inputs and outputs. There are 36 pattern slots.
4. After the relevant research unlock, request results from an ME terminal. Crafting, smithing and stonecutting patterns are not used by this assembly.

Rename assemblies to distinguish them in the AE pattern terminal. Pattern changes update the available crafting entries.

## Input and output buffers

The interface has **Patterns**, **Input Buffer** and **Output Buffer** tabs. Inputs support every registered AE2 resource
type, including items, fluids and third-party AE keys. Recipes must declare the required resources; custom recipes can
add generic inputs with `ae_inputs` and supply them through an assembly. Buffers have no fixed type-slot limit; each AE
key can hold up to 9,223,372,036,854,775,807 units, about 9.22E. Different resource types or component data create distinct
keys. Available memory still limits the practical number of types.

The assembly owns incoming materials and queues their batches. Completed outputs automatically return to ME. If the
network cannot accept them, the assembly retains them and retries. **Return Queued Ingredients** refunds batches that
have not started processing; it does not discard or falsely complete the active batch.

Patterns, tasks, input/output buffers and pending refunds survive saves and normal block removal. Restore the structure
and network after replacing the assembly. Removing only the controller does not transfer other assemblies' contents into it.

## Parallelism and research

The assembly uses its controller's research permissions and per-branch production bonuses. Buffer capacity is not a
guaranteed batch size: research limits, materials, per-key output capacity and power still apply. Deep research improves
only the well recipes unlocked by that branch.

## Recipe

<RecipeFor id="molecularmanipulator:matter_fabrication_pattern_assembly" fallbackText="This modpack has no available recipe for this item. Check JEI or the research configuration." />
