---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: Well Research and Deep Research
  icon: ae2:engineering_processor
  position: 0
---

# Well Research and Deep Research

Research belongs to the [well controller](matter_fabrication_well.md). New controllers start at stage 0; each branch stores its own completion count.

## Default progression

| Research | Prerequisite | First-completion unlocks | Time per round | Research power |
| --- | --- | --- | --- | --- |
| Stage I: AE Material Fabrication | None | Charged Certus Quartz, Fluix Crystals, processors and other AE recipes; materials/intermediates for stage two; pattern assemblies | 30 seconds | 256 AE/t |
| Stage II: Sequence Array | Stage I completed once | Sequence Array components, Molecular Sequence Rewrite Array and Assembler Matrix Sequence Rewrite Core recipes | 30 seconds | 512 AE/t |
| Stage II: Omni-Computation | Stage I completed once | Omni-Computation Core and component recipes | 30 seconds | 1024 AE/t |

Both stage-two branches can run simultaneously. Without AdvancedAE, the Omni-Computation branch and its recipes are unavailable.
These are mod defaults; a pack may change prerequisites, costs, duration and unlocks.

## Start, pause and resume

1. Connect a formed well to a working AE Network with storage, then select a stage on the **Research** page.
2. The page shows **ME stock / Round requirement**. Put materials in that network; player inventory and input-port buffers are not research supplies.
3. With all materials and prerequisites available, start research. The server checks stock again and extracts the entire round's cost at once.
4. Keep supplying ME energy. Pausing, power loss, disconnection or structural damage preserves paid materials and progress; resuming never charges the same round twice.

Only one round of a given stage can run at a time. Different branches use independent timers and energy, and may run alongside normal production.
Active research retains the duration and material snapshot recorded when it started. New rounds use updated recipe definitions.

## Deep research

Each default stage supports nine completions including its first unlock. Subsequent rounds cost 2, 4, 8, 16, 32, 64, 128 and
256 times the base materials; every round still takes 30 seconds.

| Completions | Parallel limit | Processing time |
| --- | --- | --- |
| 1 | 1 | Base time |
| 2 | 256 | Base time / 2 |
| 3 | 65.54K | Base time / 4 |
| 4 | 16.78M | Base time / 8 |
| 5 | 4.29G | Base time / 16 |
| 6 | 1.10T | Base time / 32 |
| 7 | 281.47T | Base time / 64 |
| 8 | 72.06P | Base time / 128 |
| 9 | 9.22E | Fixed at 1 tick |

Processing time rounds up to at least one tick. The maximum parallel limit is 9,223,372,036,854,775,807.
Bonuses affect only the well recipes unlocked by that research. They do not accelerate another branch, research itself,
or the machines produced by those recipes. Ingredients, power and output capacity still limit actual batches.

## Pack customization

Data packs and KubeJS can add, replace or remove research recipes of type `molecularmanipulator:matter_research`.
`duration` remains freely configurable in ticks, with a default of 1200 ticks when omitted. The built-in 600-tick value
does not override custom durations. Prerequisite counts, round costs/counts, parallelism, processing speed and unlocks
are configurable; see the project's `docs/matter-research-api.md` for complete examples.

Custom stages automatically choose one of four constellation presets. A round keeps its appearance across pause/resume
and reloads. At most four constellations are displayed together; this visual limit does not limit research concurrency.
