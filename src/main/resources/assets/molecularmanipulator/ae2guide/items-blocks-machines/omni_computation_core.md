---
navigation:
  parent: omnisequence-index.md
  title: "Omni Computation Core"
  icon: molecularmanipulator:omni_computation_controller
  position: 1020
item_ids:
- molecularmanipulator:omni_computation_controller
- molecularmanipulator:omni_computation_casing
- molecularmanipulator:omni_computation_glass
- molecularmanipulator:infinite_parallel_matrix
- molecularmanipulator:infinite_crafting_storage
- molecularmanipulator:universal_pattern_matrix
- molecularmanipulator:computation_data_entangler
- molecularmanipulator:computation_energy_stabilizer
- molecularmanipulator:computation_output_node
- molecularmanipulator:computation_crystal_pylon
---

# Omni Computation Core

The Omni Computation Core is a large crafting CPU. After you confirm a terminal order, it arranges the crafting steps, waits for ingredients and collects results. With a high-level Star Computation Engine, one core can handle multiple independent orders at once.

<Row>
<ItemImage id="ae2:crafting_terminal" scale="4" />
<BlockImage id="molecularmanipulator:omni_computation_controller" scale="4" />
<BlockImage id="molecularmanipulator:molecular_manipulator" scale="4" />
</Row>

Left to right: a terminal for placing orders, the core for managing them and a rewrite machine for making items. Keep encoded patterns in a Sequence Array, rewrite machine or Pattern Provider, and store ingredients and products in ME.

![A terminal requests a job, the CPU plans its steps, a patterned machine uses ME ingredients, and products return to ME](../assets/diagrams/me-crafting-en_us.png)

## Requirements

- Complete one round of Tier III: Computation Architecture in the [Fabrication Well](matter_fabrication_research.md), then fabricate the controller and structure parts. Some parts require Sequence Engineering research too. This route uses ExtendedAE and AdvancedAE materials.
- Clear 65 × 65 blocks, 35 blocks tall. Facing the controller's front, allow 32 blocks to each side, 17 above and below, 22 in front and 42 behind. Check the projection after placement.
- Prepare an ME network with item storage, a terminal, at least one encoded recipe and a machine that can perform it.
- Leave one free channel and supply enough ME power. The core's default idle draw is 8192 AE/t, with additional energy needed for crafting.

Check JEI's structure page for the full quantities of these parts, then build at the positions shown by the projection.

<ItemGrid>
<ItemIcon id="molecularmanipulator:omni_computation_controller" />
<ItemIcon id="molecularmanipulator:omni_computation_casing" />
<ItemIcon id="molecularmanipulator:omni_computation_glass" />
<ItemIcon id="molecularmanipulator:infinite_parallel_matrix" />
<ItemIcon id="molecularmanipulator:infinite_crafting_storage" />
<ItemIcon id="molecularmanipulator:universal_pattern_matrix" />
<ItemIcon id="molecularmanipulator:computation_data_entangler" />
<ItemIcon id="molecularmanipulator:computation_energy_stabilizer" />
<ItemIcon id="molecularmanipulator:computation_output_node" />
<ItemIcon id="molecularmanipulator:computation_crystal_pylon" />
</ItemGrid>

<RecipeFor id="molecularmanipulator:omni_computation_controller" fallbackText="Check JEI for the controller's Fabrication Well recipe and the structure material list." />

## Build the core and place an order

1. Place the controller, open its interface and enable Projection. Clear conflicts and check world height limits.
2. Gather the JEI materials in your inventory or an ME network connected to the controller. Select Build. Construction takes materials from your inventory first, then ME.
3. Wait for construction to finish and check formation and network status. Connect the terminal, storage and crafting machine to the same ME network.
4. Put an oak log to oak planks pattern in a rewrite machine or a Sequence Array's main library. Store at least 4 logs in ME.
5. Request 16 planks from the terminal. Choose the Omni Computation Core in crafting confirmation and confirm the order. If it is missing, check formation, power, channel and network connection.
6. Follow the order in AE2 Crafting Status, then collect the 16 planks from ME. Patterns and ingredients belong in the crafting machines and ME storage.

## Crafting capacity and order limits

Crafting storage is workspace for orders and is separate from ME item storage. Recipe parallelism determines how much crafting work can advance within orders. The number of independent orders is a separate limit.

| Star Computation Engine on the same network | Order and work limits |
| --- | --- |
| No active, valid engine | 1 MiB crafting storage and recipe parallelism 16; one independent order per core. |
| L1 to L6, normal operation | Crafting storage and recipe parallelism grow with level; still one independent order. |
| L7 to L9, normal operation | Multiple independent orders share the core's crafting storage and work allowance. Normal L9 still has finite limits. |
| L9, overclock enabled and continuously paid | Unlimited crafting-storage and recipe-parallelism limits. Actual production still needs ingredients, power, machines and output space. |

L1 to L8 overclocking continuously consumes star signal flow from the same network and doubles crafting storage and recipe parallelism. Multiple independent orders still require L7. If signal flow runs short, overclocking ends and the engine returns to its level's finite normal capabilities. When several engines share a network, only the strongest currently valid engine supplies benefits.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| The terminal cannot find this CPU | Check formation, same-network connection, power and channel. First make sure you can sustain its base idle consumption. |
| A CPU is available, but the recipe is not | Put its encoded pattern in a Sequence Array, rewrite machine or Pattern Provider. The core does not store recipes. |
| Not enough crafting storage | Split the order, wait for current orders to finish, or raise the connected engine's level. More ordinary storage cells do not enlarge CPU crafting storage. |
| No second order below L7 | This is the single-order stage. Upgrade the effective engine to L7, or add another CPU to the network. |
| An order stalls at an external machine | Check missing ingredients, Pattern Providers and machine input/output. Cyclic recipes also need an initial supply of the ingredient that starts the cycle. |
| Products are made, but the order is waiting | Free space in ME and machine outputs. Check that output returns to the same network, with room for tools and returned containers too. |
| L9 suddenly has finite limits again | Check the overclock switch and star signal flow balance. Reaching L9 does not automatically grant unlimited capabilities. |

External machines work at the rate they can accept. The core adapts to that capacity, and an order's final single craft can run without filling a batch. Supported reusable tools continue into later crafts with normal durability changes.

### Compatibility with other mods

- NeoECOAE is optional. Version 20.4.2+ supports ordinary AE crafting. For native batch crafting, use a Forge release with 20.5.0+ batch support.
- Storage-depot support targets compatible Data Energistics 3.3.0 / 3.3.5 releases. Depots still need free storage space.
- AdvancedAE Quantum Crafter and Reaction Chamber research requirements are supported for Forge 1.3.6-1.20.1. The core's default research also requires AdvancedAE materials.

## Connections and relocation

For wireless ME access, bind the core through Remote connection in the right sidebar. See [Sequence Wireless Hub setup](sequence_array_controller.md).

A paired Quantum Entangled Singularity is another remote option while the wireless hub is disconnected. Put one in the quantum slot and its partner in a powered AE2 quantum bridge. It needs an additional 512 AE/t and one channel.

Before relocating, finish or cancel and settle orders, then use Reclaim Package. The package retains machine contents and the wireless binding; see [Folded Buildings and Relocation](folded_buildings.md). If the wireless hub was moved or replaced, rebind the core. Structure damage or chunk unloading retains tasks and materials; restore operating conditions to continue. When enabled by the server, multiblock chunk loading keeps the required operation and construction chunks loaded.

For the same CPU functions in one block, continue to the [Transfinite Compute Nexus](transfinite_compute_nexus.md). To keep supplies stocked without terminal orders, use a [Molecular Auto Crafter](molecular_auto_crafter.md).
