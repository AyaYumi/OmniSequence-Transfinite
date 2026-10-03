---
navigation:
  parent: items-blocks-machines/event_horizon_singularity_hub.md
  title: "Singularity Matter Duplication"
  icon: molecularmanipulator:singularity_sequence_matter_bucket
  position: 1
item_ids:
- molecularmanipulator:singularity_sequence_matter_bucket
---

# Singularity Matter Duplication

**Sequence Matter is a fluid.** Hover its JEI fluid entry or bucket and hold G to open this guide.

Miniature black holes consume energy to generate **Singularity Sequence Matter**. ME fluid storage holds it until the sample slot uses it for duplication.

<ItemGrid>
<ItemIcon id="molecularmanipulator:black_hole" />
<ItemIcon id="molecularmanipulator:singularity_sequence_matter_bucket" />
</ItemGrid>

## Generate matter first

1. Form the hub and connect an online ME network.
2. Provide **ME fluid storage** that can accept Sequence Matter.
3. Insert up to **64 miniature black holes** in the black-hole slot on **Matter Duplication**.
4. Supply accessible FE or AE energy. Matter is generated and inserted into ME automatically.

Miniature black holes alone enable generation. No sample or active resource collection is required. Item storage cannot replace fluid storage; an infinite-storage item must also support fluids.

| Miniature black holes | Default matter every 20 ticks |
| --- | --- |
| 1 | 20 mB |
| 16 | 320 mB |
| 64 | 1280 mB |

## Energy usage

| Energy | Default cost per mB | Full cycle with one black hole |
| --- | --- | --- |
| FE | 1000 FE | 20,000 FE |
| AE | 256 AE | 5120 AE |

FE from extractable energy capabilities on the controller’s six adjacent faces is used before AE from the connected grid by default. Sources are consumed in priority order; the page displays FE and AE consumption separately.

The server configuration's **Singularity Hub → Matter Duplication** group controls priority, energy cost per mB, output per black hole, and interval. Available storage and energy limit actual production.

## Insert a sample to copy it

<Row>
<BlockImage id="molecularmanipulator:event_horizon_singularity_hub" scale="4" />
</Row>

1. Insert the desired item in the **sample slot** and leave ME item capacity available.
2. Each copy consumes **1000 mB (1 B)** of stored Sequence Matter.
3. The copy goes directly to ME. The original sample stays in place, and item components are preserved.
4. Remove the sample to stop copying. Miniature black holes can continue generating matter.

| Slot | Accepted contents |
| --- | --- |
| Quantum (Overview) | Quantum Entangled Singularity only; network connection |
| Sample | Item to duplicate |
| Black hole | Miniature black holes, up to 64 |

Completed outputs and pending matter refunds are retained and retried if delivery is interrupted.
