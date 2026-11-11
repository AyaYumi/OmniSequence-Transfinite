---
navigation:
  parent: omnisequence-index.md
  title: "Transfinite Compute Nexus"
  icon: molecularmanipulator:transfinite_compute_nexus
  position: 1025
item_ids:
- molecularmanipulator:transfinite_compute_nexus
---

# Transfinite Compute Nexus

## Purpose: a whole computation core's order handling in one block

The Transfinite Compute Nexus is a single-block **crafting CPU**, the device that accepts terminal orders and arranges crafting steps. It provides the [Omni Computation Core](omni_computation_core.md)'s order handling without the large structure. It does not store recipes or make products itself.

<Row>
<BlockImage id="molecularmanipulator:transfinite_compute_nexus" scale="4" />
<ItemImage id="ae2:fluix_glass_cable" scale="4" />
<ItemImage id="ae2:crafting_terminal" scale="4" />
</Row>

Shown above: the nexus, ME cable and the terminal used to place orders. The network also needs item storage and machines that perform the recipes. **Patterns** are encoded recipe cards kept in a Sequence Array, rewrite machine or Pattern Provider; ingredients and products belong in ME storage.

## Prepare

- Complete one round of **Tier VI: Transfinite Integration** in the [Fabrication Well](matter_fabrication_research.md).
- In the Research page of an **L3** [Star Computation Engine](star_computation_engine.md), actively unlock **Single-Block Integration** for **0 bit**. By default, this node gates only the nexus's and the Molecular Sequence Rewrite Array's fabrication recipes. Reaching the level does not unlock it automatically.
- The required **AdvancedAE** materials. The default nexus recipe is available only when AdvancedAE is installed.
- A powered ME network with a terminal, item storage, patterns and actual crafting machines. Leave **one free channel** for the nexus: a network connection allowance for the device.
- Power to sustain its default **16,384 AE/t base idle consumption**, plus the rest of the network and crafting.

<RecipeFor id="molecularmanipulator:transfinite_compute_nexus" fallbackText="Check JEI for the Fabrication Well recipe, AdvancedAE materials and research requirements." />

## Steps: connect it and try one order

1. Place the nexus and connect ME cable to **any face**. All six faces accept cable connections.
2. Put the terminal, ME storage and actual crafting machines on the same network. Check that the nexus has power and a channel.
3. Put an “oak log → oak planks” pattern in a rewrite machine or a Sequence Array's main library. Store at least **4 logs** in ME.
4. Request **16 planks** from the terminal. Choose the Transfinite Compute Nexus as the CPU in crafting confirmation, then confirm.
5. Watch progress in AE2 Crafting Status and retrieve the planks from ME when it finishes.

The nexus needs cable or another external ME device. A disconnected group made only of adjacent nexuses cannot operate.

## What success looks like

- The crafting confirmation screen offers the nexus as a CPU.
- The test order completes, turning 4 logs from ME into 16 planks.
- The nexus manages the order, crafting machines make it and ME storage receives it. No patterns or ingredients need to go into the nexus.

### Compact does not mean unlimited by default

The nexus has the same limits as the Omni Computation Core. **Crafting storage** is workspace for orders, not item-cell capacity. **Recipe parallelism** is not the number of independent orders.

| Star Computation Engine state | Nexus capabilities |
| --- | --- |
| No active, valid engine | **1 MiB crafting storage, recipe parallelism 16**, one independent order at a time. |
| L1–L6, normal operation | Increased crafting storage and recipe parallelism; still one independent order at a time. |
| L7–L9, normal operation | Multiple independent orders share this machine's allowance. Normal L9 still has finite limits. |
| L9, overclock enabled and continuously paid | Unlimited crafting-storage and recipe-parallelism limits. Ingredients, power, machine speed and output space still determine actual production. |

L1–L8 overclocking continuously consumes **star signal flow** from the same network and doubles crafting storage and recipe parallelism. Multiple orders begin at L7, independently of overclocking. Insufficient signal flow ends overclocking and restores finite normal capabilities. See the [Omni Computation Core](omni_computation_core.md) for details and supported machine integrations.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| No usable CPU after placing the nexus | Check external ME connection, power and channel. Adjacent nexuses alone do not provide a network connection. |
| The network repeatedly loses power | Cover the nexus's base idle consumption first, then the other devices and crafting. |
| A CPU is available, but the product is missing | Put the pattern in an actual crafting machine or provider. The nexus has no pattern library. |
| A second order cannot start | Check whether the effective engine has reached L7. Base parallelism 16 does not mean 16 independent orders. |
| A large order lacks storage or keeps waiting | Test a smaller order. Check Crafting Status for ingredients, machine issues and output space. More item cells do not enlarge crafting storage. |
| The nexus recipe is missing or locked | Check that AdvancedAE is installed, Transfinite Integration is completed in the well and Single-Block Integration permission is available. Keep an engine supplying borrowed network permission online. |
| Crafting stops when you leave the base | The nexus **does not force-load chunks**. Keep its chunk loaded for continuous operation. |

## Next steps

- Need recipes or a machine to make them: set up a [Molecular Sequence Rewrite Array](molecular_sequence_rewrite_array.md) or [Sequence Array](sequence_array_controller.md).
- Want everyday products replenished automatically: use a [Molecular Auto Crafter](molecular_auto_crafter.md), without repeatedly placing CPU orders.
- Need more simultaneous or larger orders: improve the [Star Computation Engine](star_computation_engine.md) on the same network, along with item storage, energy and crafting machines.

Settle orders before relocating. Normal removal retains recoverable tasks and contents; place the nexus again and restore an online ME connection to continue.
