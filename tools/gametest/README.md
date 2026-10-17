# Isolated engine regressions / 隔离游戏测试

Current source: 2.0.9, Minecraft 1.21.1, NeoForge, Java 21. Test source sets are excluded from a normal runtime JAR.
[Dependency setup](../../libs/README.md)

## Suites

| Init script | Coverage |
| --- | --- |
| `auto-crafting.init.gradle` | Real powered Sequence Array and single-block passive crafting: capacity-limited same-tick ME/adjacent delivery, blocked probe counts, million-item batches, returned buckets/shared-capacity rollback, old long output retention and CPU pipeline preservation |
| `performance-regression.init.gradle` | Long-count providers, well AEKey inputs/codecs/queues, recipe lookup invalidation, research stock scan counts and admission conservation, dismantling, Nexus and actual storage-bus pattern access |
| `cpu-batch-compat.init.gradle` | Real ECO, Trinity and Thunderbolt API dispatch into a formed well, scaled input conservation, durable queues, adapter reload and optional-mod absence |
| `singularity.init.gradle` | Hub registry/palette, build/dismantle, old motion/embedding migrations, protection events and material conservation |
| `compact-singularity.init.gradle` | Single-block hub: six-face ME connections, tag collection, fluid production/copying, capacity waits and retained drops; collection batch/interval bounds, live changes, missed cycles and reload |
| `cosmic-holes.init.gradle` | Real hole item placement, optional FTB team ownership, nearest exit, multiple exits, item preservation, removal fallback, saved bindings and recipes |
| `aae.init.gradle` | Actual transformed Omni AE and AdvancedAE CPUs, native/optional batch providers, ownership, rejected/failing admissions, exact counts and per-tick backpressure |
| `ae2-compat.init.gradle` | AE2 extraction, native bypass, mixed exact tasks and long-count providers with Applied 1.1.0 |
| `paired-api.init.gradle` | Current Applied development API: mixed native rewriting, fallback and exact quantity integration; select its separate JAR with `applied_enhancements_api_build` |
| `fabrication-dispatch.init.gradle` | Finite-power Nexus deliveries at 20 billion and `Long.MAX_VALUE`, 8.1G energy-limit reproduction, durable ownership/refunds and hidden slot invariants |
| `cyclic-submission.init.gradle` | Terminal-style planning, summary, submission and completion on a powered Omni Nexus; ignored startup seed, missing seed/raw inputs, retry rollback and ordinary output exclusion |
| `applied-110.init.gradle` | Original Applied 1.1.0: native bypass, mixed exact local rewriting, AE2 extraction and long-count providers |
| `outer-wilds.init.gradle` | Six-direction gravity, field handoff, placement, motion, Ghost Matter damage, water suppression, expiry and real recipes |
| `gametest.init.gradle` | Combined engine suite, including legacy migrations and spawn protection |

```powershell
.\gradlew.bat build --no-configuration-cache
.\gradlew.bat -I tools/gametest/outer-wilds.init.gradle runGameTestServer --no-configuration-cache
```

Replace `outer-wilds` with the desired suite. Optional dependency JARs and runtime
selectors are configured in each init script; use matching NeoForge versions.
Every suite uses disposable worlds under `build/`. Never select a personal save.
A successful launch is not sufficient: all required GameTests must be discovered
and pass. Test fixtures, recipes and optional API declarations are not released.

`src/test` contains maintained unit tests executed by `test` and `build`.
