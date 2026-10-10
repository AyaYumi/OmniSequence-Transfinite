# Forge 1.20.1 isolated game tests

Current version: **2.0.8-forge**, Minecraft **1.20.1**, Forge **47.4.20**, Java **17**.
Test sources and optional API declarations are excluded from release JARs.
[Prerequisites](../../libs/README.md)

Run one suite at a time; every suite uses a disposable directory under `build/`.
A completed server with zero discovered tests is not a passing run.

```powershell
.\gradlew.bat build apiJavadoc --no-configuration-cache
.\gradlew.bat -I tools/gametest/outer-wilds.init.gradle runGameTestServer --no-configuration-cache
```

| Init script | Coverage |
| --- | --- |
| `outer-wilds.init.gradle` | Six-direction gravity, placement, motion, wall walking, jumping, aim, drops, metadata, field handoff; Ghost Matter damage, water, dispersed mist, expiry and real recipes |
| `compact-singularity.init.gradle` | Six-face ME wiring, collection, exact fluid/energy capacity, sample copying, world saves and portable drops |
| `cosmic-holes.init.gradle` | Bound ownership, multiple nearest exits, reload, item preservation and chunk tickets |
| `catalyst-sharing.init.gradle` | Shared catalysts, recipe JSON/network sync, pattern menus and durable ownership |
| `fabrication-upload.init.gradle` | Packet boundaries, real menu validation, research/team filtering, alternatives, blank ownership and full/detached providers |
| `cpu-batch-compat.init.gradle` | Native counted provider, 1026-craft batches, scaling, stale admissions, persistence and refunds; optional real Forge API contracts or legacy fallback |
| `performance-regression.init.gradle` | Recipe indexing, Forge fluid field mapping with exact NBT, arbitrary AEKeys, long-count providers, stock snapshot reads/admission/refunds, dismantling, Nexus and pattern ports |
| `cyclic-submission.init.gradle` | Cyclic planning, submission, retry rollback, completion and seed/raw-input rules |
| `byproduct-submission.init.gradle` | Processing secondary outputs, mixed plans and ownership; `-PplanningFallbackChecks` adds transfer cleanup checks |
| `applied-110.init.gradle` / `ae2-compat.init.gradle` | AppliedEnhancements 1.1.0-forge and upstream AE2 integration, native bypass, exact tasks and long-count providers |
| `singularity.init.gradle` | Construction, motion, embedding, suspended mode and cached black-hole scans |
| `gametest.init.gradle` | Existing Forge recipe/buffer/multiblock regressions and spawn protection |
| `maintenance.init.gradle` | Saved state, protected dismantling and legacy migration |
| `aae.init.gradle` | Matching Forge AdvancedAE and GeckoLib; real CPU admission/backpressure |

Replace `outer-wilds` in the command with the desired suite name. To test AE2 UELM,
add `-Pae2_uelm_version=15.5.4-uelm`. Logs are in the suite directory's `logs/latest.log`.

FTB integration requires **matching Forge 1.20.1** FTB Teams, FTB Library and
Architectury jars in a separate dependency directory:

```powershell
.\gradlew.bat -I tools/gametest/cosmic-holes.init.gradle -PcosmicFtbTests "-PftbPackMods=<Forge dependency directory>" runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/fabrication-upload.init.gradle -PuploadFtbTests "-PftbPackMods=<Forge dependency directory>" runGameTestServer --no-configuration-cache
```

Optional CPU testing accepts explicit Forge jars plus their dependencies, separated
by semicolons in `-PcpuCompatJars=<jar1>;<jar2>`. Do not supply newer NeoForge jars.
If an installed Forge addon lacks the newer public batch API, the production mod
keeps ordinary AE dispatch and the test checks that fallback. Compilation uses
Java 17 ABI declarations in `src/optionalCpuApi`; these are never runtime mods.
The optional tests report contract admission separately from engine execution.

Cyclic automatic planning can be selected with `-PcyclicAutomatic`; mixed byproduct
automatic planning with `-PbyproductAutomatic`. Use `cyclicRunName`, `byproductRunName`
or `cpuCompatRunName` to keep separate dependency/configuration variants isolated.
