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

Miniature black holes consume energy inside the hub to generate **Singularity Sequence Matter fluid**. The hub draws this matter from ME fluid storage to copy the item in its sample slot.

<ItemGrid>
<ItemIcon id="molecularmanipulator:black_hole" />
<ItemIcon id="molecularmanipulator:singularity_sequence_matter_bucket" />
</ItemGrid>

## Generating matter

1. Form an [Event Horizon Singularity Hub](event_horizon_singularity_hub.md) or connect a [Compact Hub](compact_singularity_hub.md).
2. Provide **ME fluid storage** that accepts Sequence Matter.
3. Insert up to **64 miniature black holes** in the black-hole slot on **Matter Duplication**.
4. Supply FE or AE energy. Matter is generated automatically and stored in ME.

Generation needs neither a sample nor active resource collection. Item storage cannot replace fluid storage; check that infinite storage also supports fluids.

| Black holes | Default output every 20 ticks |
| --- | --- |
| 1 | 20 mB |
| 16 | 320 mB |
| 64 | 1280 mB |

## Energy

By default, the hub draws FE from energy devices adjacent to the controller's six faces before using AE from ME. Adjacent devices must allow energy extraction.

| Energy | Cost per mB | Full production cycle with one black hole |
| --- | --- | --- |
| FE | 1000 FE | 20,000 FE |
| AE | 256 AE | 5120 AE |

Matter Duplication displays FE and AE consumption separately. The server's **Singularity Hub → Matter Duplication** configuration controls energy priority, costs, output and interval. Insufficient energy or fluid capacity reduces or pauses production.

## Copying items

Insert the desired item in the **sample slot** and leave ME item capacity for copies. Each copy costs **1000 mB (1 B)** of Sequence Matter. Copies enter ME; the original sample remains, and item component data is preserved.

Remove the sample to stop copying. Miniature black holes can continue generating matter. If delivery is interrupted, completed outputs and pending matter refunds are retained until the network recovers.

Overview's quantum slot is for Quantum Entangled Singularities, not samples or miniature black holes.
