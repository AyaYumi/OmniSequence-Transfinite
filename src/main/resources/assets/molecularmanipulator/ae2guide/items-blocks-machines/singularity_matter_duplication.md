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

Copying has two steps: **spend energy with miniature black holes to generate Sequence Matter fluid, then spend that matter to copy a sample item.** The sample is retained and copies go straight into ME. Resource Collection is a separate function and does not need to be running.

<Row>
<ItemImage id="molecularmanipulator:black_hole" scale="5" />

<ItemImage id="molecularmanipulator:singularity_sequence_matter_bucket" scale="5" />

<ItemImage id="minecraft:diamond" scale="5" />
</Row>

**Miniature black holes generate matter → matter pays for sample copies.** The diamond is only an example sample. The bucket identifies the fluid; you do not insert buckets into the hub's slots.

![Energy and black holes make matter, ME fluid storage holds it, and sample copies enter ME item storage](../assets/diagrams/singularity-copy-en_us.png)

The crystal pictured is another sample example, not a fixed ingredient. Copies follow the item you put in the sample slot.

## What you need

- A formed [Event Horizon Singularity Hub](event_horizon_singularity_hub.md), or a connected [Compact Hub](compact_singularity_hub.md).
- An **online ME network** with **fluid storage and item storage**: fluid space holds matter; item space accepts copies. Item capacity alone is not enough. Check that infinite storage supports fluids too.
- At least one [Miniature Black Hole](black_and_white_holes.md), energy for generation and the sample you want to copy. With matter already stored, you can copy without black holes installed.

## Step 1: generate matter

1. Open **Matter Duplication** on the hub.
2. Insert miniature black holes in the **Matter generator** slot, up to **64**. Do not use Overview's quantum slot.
3. Supply FE or AE from the ME network and leave ME fluid capacity available.
4. Leave the sample empty for this test. Wait for a cycle and check that Sequence Matter increases in ME. Generation is automatic; you do not need to start collection.

Default output is shown below. **20 ticks are about 1 second at normal speed.**

| Black holes | Matter per cycle |
| --- | --- |
| 1 | 20 mB |
| 16 | 320 mB |
| 64 | 1280 mB |

Black holes are not consumed as ingredients. More of them increase generation capacity and its energy cost, not the amount of matter required per copy.

### How FE and AE are selected

Default priority is **FE → AE**. FE is drawn from energy-extractable devices **adjacent to any of the controller's six faces**. AE comes from the connected ME network. Even with FE generation, ME itself must stay powered and online.

| Using this energy alone | Per 1 mB generated | Full cycle with 1 black hole (20 mB) |
| --- | --- | --- |
| FE | 1000 FE | 20,000 FE |
| AE | 256 AE | 5120 AE |

**One cycle does not combine FE and AE to make up a shortfall.** If the preferred source can pay for only part of the output, only that part is produced. For example, FE sufficient for 10 mB produces 10 mB, without AE topping it up to 20 mB. The hub tries AE only when it cannot draw any FE. A trace of FE below the cost of 1 mB produces nothing that cycle.

The interface shows each energy cost and the previous cycle's actual use separately. Servers can change priority, costs, output and interval; use the values displayed in your game.

## Step 2: insert a sample and copy

1. Keep at least **1000 mB (1 bucket)** of matter in ME, with item space for copies.
2. Put the target item in the **sample slot**. The hub draws fluid automatically; no fluid buckets need to be delivered.
3. Check your ME terminal for copies. Each costs **1000 mB**. The original stays in its slot, and item details such as names and enchantments are copied.
4. Remove the sample to stop copying. Remove the black holes to stop generation. Neither action replaces the other; stopping Resource Collection stops neither.

With enough matter and item space, copying is limited to **1 item per tick**. By default, 64 black holes generate 1280 mB per second, enough for a long-term average of about **1.28 copies per second**. A large matter reserve can support faster copying temporarily. More black holes do not raise the per-tick copying limit, and collection speed settings do not affect copying.

Servers may prohibit copying certain items; the default blocks none. A prohibited sample remains retrievable and spends no matter. This does not stop generation or Resource Collection.

## How to tell it worked

- **Generating only:** with no sample, Sequence Matter stock rises each cycle in ME.
- **Copying:** the sample stays in its slot, copies appear in ME and matter is spent. A fluid stock that does not keep rising does not necessarily mean generation stopped.
- If the network temporarily cannot accept delivery, completed copies and matter awaiting refund are retained until it recovers.

## Quick fixes

| Problem | What to check |
| --- | --- |
| Item space is available but matter stays at zero | Provide ME **fluid** storage that accepts matter, black holes and enough energy. |
| Very little FE, but ample AE is not taking over | A cycle does not mix sources. Supply enough FE, disconnect the FE source so AE takes over, or ask the server owner to change priority. |
| Fluid stock never rises | Remove the sample to test; copying may be spending newly generated matter. |
| Sample inserted, but no copies | Check for 1000 mB of matter, ME item space and server copying restrictions. |
| Energy use continues after stopping collection | Generation is independent. Remove the black holes to stop it. |

Select **Guide** on the machine's duplication page to return here.
