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

Bring Omni Computation's ME crafting CPU capability into one block for compact crafting networks.

<Row>
<BlockImage id="molecularmanipulator:transfinite_compute_nexus" scale="4" />

<ItemImage id="ae2:fluix_glass_cable" scale="4" />

<ItemImage id="ae2:crafting_terminal" scale="4" />
</Row>

## Place, connect, request

1. Complete **Tier 2: Omni Computation** research and fabricate the nexus.
2. Place it; all six faces connect to ME cables.
3. Power the network and provide **one channel**.
4. Request a craft in ME. The nexus creates independent virtual CPU lanes and keeps a spare lane available.

| Property | Detail |
| --- | --- |
| Structure | Single block |
| Base power | Default 16,384 AE/t; configurable |
| Job monitoring | AE2 crafting status interface |
| Network | A real external ME connection is required |

Adjacent nexuses alone, with no cable or other external ME device, do not become operational.

## Work and relocation

Logical storage and parallelism match the Omni Computation Core. Actual throughput depends on ingredients, energy, output capacity, providers, and server performance.

Normal removal carries recoverable job state with the nexus. Place it again and restore an online ME connection to resume. It does not force-load chunks; keep its chunk loaded for continuous operation.

## Recipe

<RecipeFor id="molecularmanipulator:transfinite_compute_nexus" fallbackText="No recipe is available in this pack. Check JEI and research requirements." />
