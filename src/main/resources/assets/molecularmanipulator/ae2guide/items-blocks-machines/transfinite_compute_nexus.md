---
navigation:
  parent: omnisequence-index.md
  title: Transfinite Compute Nexus
  icon: molecularmanipulator:transfinite_compute_nexus
  position: 1025
item_ids:
- molecularmanipulator:transfinite_compute_nexus
---

# Transfinite Compute Nexus

<BlockImage id="molecularmanipulator:transfinite_compute_nexus" scale="8" />

The Transfinite Compute Nexus is the single-block AE2 crafting CPU for the Omni-Computation branch. It needs no multiblock structure and has no machine screen.

| Property | Value |
| --- | --- |
| Structure | None — place it like a normal block |
| Machine screen | None |
| Channel | 1 |
| Idle power | 16,384 AE/t by default |
| Quantum slot | None |

## Unlocking and recipe

The Nexus is unlocked by **Stage II: Omni-Computation** research and is made in the [Matter Fabrication Well](matter_fabrication_well.md). A modpack can change or remove the research and recipe.

<RecipeFor id="molecularmanipulator:transfinite_compute_nexus" fallbackText="This recipe is unavailable when the research or recipe is disabled." />

> The default well recipe takes 1,200 ticks at 4,096 AE/t. The live recipe panel above is the authority when a modpack changes these values.

## Using the Nexus

1. Place the Nexus as a normal block. Its facing only controls the front texture and can be rotated with an AE2 wrench.
2. Connect one or more ME cables. All six faces can connect to the network; the network must provide power and one channel.
3. Submit a crafting request from an AE2 terminal. The Nexus creates independent virtual CPU lanes as jobs require them and keeps one spare lane available.

When online, the Nexus provides the same effectively unlimited logical crafting storage and parallelism as the Omni-Computation Core. Actual throughput is still limited by ingredients, energy, output capacity, provider acceptance, and server tick time. The accelerated planner can be used when its normal conditions are met.

The idle power cost is configurable under `transfinite_compute_nexus.idle_power`. The block remains inactive when its ME network is unpowered, has no channel, or loses every external network connection.

> The Nexus has no quantum slot, pattern library, multiblock construction queue, or chunk-loading footprint.
> Adjacent Nexus blocks may connect to one another as part of the same AE grid, but each block owns and schedules its own virtual CPU lanes. A group made only of Nexus blocks still needs a cable or another external ME device before it becomes an active network.

## Persistence

Active and queued jobs, their CPU lane state, and retained crafting contents are saved with the block.

* Breaking a Nexus with recoverable state creates a portable Nexus item carrying that state; place it again and reconnect it to an online ME network to resume processing.
* A clean Nexus drops normally.
