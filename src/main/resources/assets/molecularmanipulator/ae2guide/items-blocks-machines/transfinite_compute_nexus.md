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

The Transfinite Compute Nexus is a single-block crafting CPU with the [Omni Computation Core](omni_computation_core.md)'s order-handling functions. Connect it to ME and select it when placing a terminal order. Separate machines store patterns and make the products.

<Row>
<BlockImage id="molecularmanipulator:transfinite_compute_nexus" scale="4" />
<ItemImage id="ae2:fluix_glass_cable" scale="4" />
<ItemImage id="ae2:crafting_terminal" scale="4" />
</Row>

Connect the nexus to ME cable and use a terminal to place orders. The network also needs item storage and machines to perform recipes. Keep encoded patterns in a Sequence Array, rewrite machine or Pattern Provider, and store ingredients and products in ME.

## Requirements

- Complete one round of Tier VI: Transfinite Integration in the [Fabrication Well](matter_fabrication_research.md).
- Bring a [Star Computation Engine](star_computation_engine.md) to L3, open Recipe Tree and click Unlock for Single-Block Integration. It costs 0 bit. Reaching L3 alone does not unlock it. By default, this permission is required only for the nexus's and Molecular Sequence Rewrite Array's fabrication recipes.
- Install AdvancedAE and gather its required materials. The default nexus recipe is available only with AdvancedAE installed.
- Prepare a powered ME network with a terminal, item storage, patterns and crafting machines. Leave one free channel for the nexus.
- Supply its default idle draw of 16,384 AE/t, plus power for the rest of the network and crafting.

<RecipeFor id="molecularmanipulator:transfinite_compute_nexus" fallbackText="Check JEI for the Fabrication Well recipe, AdvancedAE materials and research requirements." />

## Connect the nexus and place an order

1. Place the nexus and attach ME cable to any of its six faces.
2. Put the terminal, ME storage and crafting machines on the same network. Check that the nexus has power and a channel.
3. Put an oak log to oak planks pattern in a rewrite machine or a Sequence Array's main library. Store at least 4 logs in ME.
4. Request 16 planks from the terminal. Choose the Transfinite Compute Nexus in crafting confirmation, then confirm.
5. Follow the order in AE2 Crafting Status. The crafting machine turns 4 logs into 16 planks and sends them to ME. The nexus manages the order; patterns and ingredients stay in their usual machines and storage.

The nexus needs cable or another external ME device. A disconnected group made only of adjacent nexuses cannot operate. It does not support Sequence Wireless Hub binding; that option is available on the [multiblock core](omni_computation_core.md).

## Crafting capacity and order limits

The nexus has the Omni Computation Core's limits. Crafting storage provides workspace for orders, separate from ME item storage. Recipe parallelism controls work within orders; it does not set the number of independent orders.

| Star Computation Engine state | Nexus capabilities |
| --- | --- |
| No active, valid engine | 1 MiB crafting storage, recipe parallelism 16, one independent order at a time. |
| L1 to L6, normal operation | Increased crafting storage and recipe parallelism; still one independent order at a time. |
| L7 to L9, normal operation | Multiple independent orders share this machine's allowance. Normal L9 still has finite limits. |
| L9, overclock enabled and continuously paid | Unlimited crafting-storage and recipe-parallelism limits. Ingredients, power, machine speed and output space still determine actual production. |

L1 to L8 overclocking continuously consumes star signal flow from the same network and doubles crafting storage and recipe parallelism. Multiple orders begin at L7, independently of overclocking. Insufficient signal flow ends overclocking and restores finite normal capabilities. See the [Omni Computation Core](omni_computation_core.md) for details and supported machine integrations.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| No usable CPU after placing the nexus | Check external ME connection, power and channel. Adjacent nexuses alone do not provide a network connection. |
| The network repeatedly loses power | Cover the nexus's base idle consumption first, then the other devices and crafting. |
| A CPU is available, but the product is missing | Put the pattern in an actual crafting machine or provider. The nexus has no pattern library. |
| A second order cannot start | Check whether the effective engine has reached L7. Base parallelism 16 does not mean 16 independent orders. |
| A large order lacks storage or keeps waiting | Split the order into smaller quantities. Check Crafting Status for ingredients, machine issues and output space. More item cells do not enlarge crafting storage. |
| The nexus recipe is missing or locked | Check that AdvancedAE is installed, Transfinite Integration is completed in the well and Single-Block Integration permission is available. Keep an engine supplying borrowed network permission online. |
| Crafting stops when you leave the base | The nexus does not force-load chunks. Keep its chunk loaded for continuous operation. |

## Related machines

Use a [Molecular Sequence Rewrite Array](molecular_sequence_rewrite_array.md) or [Sequence Array](sequence_array_controller.md) to store patterns and perform recipes. A [Molecular Auto Crafter](molecular_auto_crafter.md) keeps everyday products stocked without terminal orders.

For more simultaneous or larger orders, upgrade the [Star Computation Engine](star_computation_engine.md) on the same network. Keep enough item storage, energy and crafting capacity for those orders.

Settle orders before relocating. Normal removal retains recoverable tasks and contents. Place the nexus again and restore its external ME connection to continue.
