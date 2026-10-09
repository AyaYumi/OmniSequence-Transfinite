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

The Transfinite Compute Nexus provides the Omni Computation Core's crafting CPU functions in a single block. It can manage multiple ME crafting requests at once.

<Row>
<BlockImage id="molecularmanipulator:transfinite_compute_nexus" scale="4" />

<ItemImage id="ae2:fluix_glass_cable" scale="4" />

<ItemImage id="ae2:crafting_terminal" scale="4" />
</Row>

## Network connection

1. Complete **Tier 2: Omni Computation** in the well and fabricate the nexus.
2. Place it and connect ME cables to any of its six faces.
3. Provide **one channel** and sufficient ME power. Default base consumption is **16,384 AE/t**.
4. Request crafting from an ME terminal and monitor it through AE2's crafting status interface.

The nexus needs a cable or another external ME device. A group of adjacent nexuses with no external connection does not operate.

## Operation

The nexus provides the same crafting storage and parallelism as the [Omni Computation Core](omni_computation_core.md). Pattern Providers and machines still execute recipes. Ingredients, power, machines and output acceptance determine the actual rate.

## Relocation

Normal removal retains recoverable tasks and contents. Place it again and restore an online ME connection to continue.

The nexus does not force-load chunks. Keep its chunk loaded for continuous crafting.

## Recipes

<RecipeFor id="molecularmanipulator:transfinite_compute_nexus" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
