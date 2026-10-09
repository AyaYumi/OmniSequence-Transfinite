# Isolated engine regressions / 隔离游戏测试

Current source: 2.0.8. Test source sets are excluded from a normal runtime JAR.
[Dependency setup](../../libs/README.md)

## Suites

| Init script | Coverage |
| --- | --- |
| `performance-regression.init.gradle` | Long-count providers, well AEKey inputs/codecs/queues, recipe lookup invalidation, research stock scan counts and admission conservation, dismantling, Nexus and actual storage-bus pattern access |
| `cpu-batch-compat.init.gradle` | Real ECO, Trinity and Thunderbolt API dispatch into a formed well, scaled input conservation, durable queues, adapter reload and optional-mod absence |
| `singularity.init.gradle` | Hub registry/palette, build/dismantle, old motion/embedding migrations, protection events and material conservation |
| `compact-singularity.init.gradle` | Single-block hub: six-face ME connections, tag collection, fluid production/copying, capacity waits and retained drops |
| `cosmic-holes.init.gradle` | Real hole item placement, optional FTB team ownership, nearest exit, multiple exits, item preservation, removal fallback, saved bindings and recipes |
| `aae.init.gradle` | Actual transformed Omni AE and AdvancedAE CPUs, native/optional batch providers, ownership, rejected/failing admissions, exact counts and per-tick backpressure |
| `ae2-compat.init.gradle` | AE2 extraction, native bypass, mixed exact tasks and long-count providers with Applied 1.1.0 |
| `paired-api.init.gradle` | Current Applied development API: mixed native rewriting, fallback and exact quantity integration; select its separate JAR with `applied_enhancements_api_build` |
| `cyclic-submission.init.gradle` | Terminal-style planning, summary, submission and completion on a powered Omni Nexus; ignored startup seed, missing seed/raw inputs, retry rollback and ordinary output exclusion |
| `applied-110.init.gradle` | Original Applied 1.1.0: native bypass, mixed exact local rewriting, AE2 extraction and long-count providers |
| `gametest.init.gradle` | Combined engine suite, including legacy migrations and spawn protection |

```powershell
.\gradlew.bat -I tools/gametest/performance-regression.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/cpu-batch-compat.init.gradle -PcpuCompatRunName=cpu-batch-baseline runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/applied-110.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/cyclic-submission.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/cyclic-submission.init.gradle -PcyclicAutomatic -PcyclicRunName=cyclic-automatic runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/singularity.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/cosmic-holes.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/cosmic-holes.init.gradle -PcosmicFtbTests runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/aae.init.gradle "-PaaeJar=libs/AdvancedAE-1.6.11-1.21.1.jar" "-PgeckoJar=libs/geckolib-neoforge-1.21.1-4.9.2.jar" runGameTestServer --no-configuration-cache
```

Native fabrication CPU compatibility uses the real optional JARs listed in
`libs/README.md`. Run `cpu-batch-compat.init.gradle` with
`"-PcpuCompatMods=eco,thunderbolt,data,lightning"` and
`"-PcpuCompatPackMods=<pack mods directory>"` to include ECO, Thunderbolt,
Data Energistics and AE2 Lightning Tech. Use a distinct `cpuCompatRunName` for
each isolated runtime. The suite exercises the actual ECO planner, Thunderbolt
batch executor and Trinity registry against a formed well, checking 1026-craft
input/output accounting, long resource capacity, failed pushes, scaled Omni
deliveries, NBT retention, refunds, adapter lifecycle and local doubling bypass.
Without `cpuCompatMods`, it verifies loading and ordinary/local dispatch with
every optional CPU absent. Test resources and API dependencies are not packaged.

Use the matching independent prerequisite in `libs/`. Optional AdvancedAE and
GeckoLib runtime JARs must be passed explicitly; they are not packaged with the mod.
AE2 version can be selected with `-Pae2_version=19.2.17` or the default in
`gradle.properties`.

The cosmic-hole suite runs once without FTB Teams (personal ownership fallback)
and once with `-PcosmicFtbTests` (real FTB party members). That option adds FTB
Teams, FTB Library and Architectury only to the isolated test runtime.

## Worlds and assertions

Gravity Crystal and Ghost Matter gameplay plus real client OBJ baking:

```powershell
.\gradlew.bat -I tools/gametest/outer-wilds.init.gradle '-Pae2_version=19.2.17' runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/outer-wilds.init.gradle '-Pae2_version=19.2.17' -PouterWildsClientModels runClient --no-configuration-cache
.\gradlew.bat -I tools/gametest/outer-wilds.init.gradle '-Pae2_version=19.2.17' -PghostMatterClient runClient --no-configuration-cache
.\gradlew.bat -I tools/gametest/outer-wilds.init.gradle '-Pae2_version=19.2.17' -PgravityWalkingClient runClient --no-configuration-cache
```

These verify real six-face item placement, empty-hand switching, redstone pause,
bounded item/living motion, solid-wall blocking, state reload, water suppression,
damage and item safety, loaded recipes/research and catalyst pattern exclusion.
Ghost Matter checks also cover natural flow across a 3×3 deposit, retained flow
levels, drainage without crystal loss or new sources, unmineable supported shards,
support-loss dispersion, a persisted 1200-tick deadline, square field corners and
expiry without drops. The dedicated integrated-client run verifies real health
packets, silent exposure, no knockback/flinch, GPU shader rendering, flowing-water
suppression and drainage, deadline synchronization and fading after leaving.
Player tests cover all six gravity directions, rotated bounds/eyes, actual surface
walking/jumping, synchronized metadata and range/redstone/removal recovery. The
client walking check creates a disposable flat world, drives real keyboard input
on a LocalPlayer, verifies server/client agreement and all three camera modes,
switches perspectives repeatedly, captures first/third-person screenshots and
checks redstone recovery before exiting. Block-overlay queries cover standing and
crouching in all six gravity directions, both clear eyes above a supporting block
and genuine head obstruction with the correct block state and position.
The fifth server test covers mounting-plane preference, corner hysteresis,
confirmed/interrupted crossings, direct source handoff and rear-side exclusion.
Client checks cover continuous camera entry/exit, intermediate orientations,
settled aiming/eyes, mirrored perspective during a turn, all 32 atlas frames and
placed Ghost Matter mist/water screenshots. Shared test chunks stay loaded until
the disposable server exits so one batch cannot unload another batch's fixtures.
The client exits automatically after checking every block-state model, both item
models, complete OBJ materials, vertex bounds and missing textures. Test-only
source sets and worlds remain below `build/` and are excluded from release JARs.

Shared catalysts (36-slot menu, separate recipe JSON/network codecs, cross-assembly
stock, isolation between wells, overlapping ingredients, 1026-craft native batches,
stale admission, pause/resume, saved active work and portable drops):

```powershell
.\gradlew.bat -I tools/gametest/catalyst-sharing.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/catalyst-sharing.init.gradle '-PcatalystPackMods=E:\MC\PCL\.minecraft\versions\New Age Science and Technology\mods' runGameTestServer --no-configuration-cache
```

The second run also imports the pack's real Lightning Tech Crystal Catalyzer recipes
and checks their retained catalysts, fixed water cost and lightning input.

Fabrication encoding/upload and placement-time team binding:

```powershell
.\gradlew.bat -I tools/gametest/fabrication-upload.init.gradle runGameTestServer
.\gradlew.bat -I tools/gametest/fabrication-upload.init.gradle -PfabricationFtbTests runGameTestServer
```

These suites cover complete long AE quantities, merged fluid inputs, multiple outputs,
bounded packets, invalid/stale requests, real AE2 terminal blank consumption, duplicate/full/missing
destinations, controller placement and persistence, legacy migration and real FTB party membership.

Every suite creates/uses a disposable directory below `build/`. Never use a
personal save. Fixtures can replace recipes, construct large structures and force
test chunks. Reset the disposable world before comparing layouts from different
revisions; old fixture blocks can obstruct motion outside the current blueprint.
They restore or retain ownership according to the asserted contract.
Test resources include only fixture recipes and small empty templates.

A successful Gradle launch alone is insufficient: inspect the GameTest required
test result and each assertion. CPU tests construct plan/inventory fixtures and
run actual transformed dispatch; they do not cover terminal planning/UI clicks.
Lookup timing is diagnostic and is not a full-modpack TPS benchmark.

The cyclic submission suite additionally calculates real plans through the crafting
service, checks the terminal summary's simulation flag, submits to a powered Nexus
CPU and completes processing with delayed provider returns. The default run uses
Omni's explicit AELIS API with Applied automatic planning disabled; `cyclicAutomatic`
tests Applied's automatic path. Both verify actual shortages and output exclusion.

The byproduct suite constructs separate real storage/provider grids and powered
Nexus CPUs. It requests 32 diamonds through a two-output intermediate recipe and
a second ingredient branch. Redstone, lapis and water byproducts must finish with
exact input/output conservation; a fourth test rejects a genuine one-coal shortage
without extracting inventory, and a fifth introduces an unknown task wrapper and
completes the restored job. Both the terminal summary and real submission are checked.

```powershell
.\gradlew.bat -I tools/gametest/byproduct-submission.init.gradle runGameTestServer --no-configuration-cache
```

For the original mixed-wrapper reproduction, supply the real optional addons:

```powershell
.\gradlew.bat -I tools/gametest/byproduct-submission.init.gradle `
  '-PbyproductUselessJar=D:\test-mods\useless_mod-1.21.1-2.4.5.6.jar' `
  '-PbyproductEapJar=D:\test-mods\extendedae_plus-1.5.5.jar' `
  '-PbyproductAaeJar=D:\test-mods\AdvancedAE-1.6.11-1.21.1.jar' `
  '-PbyproductGeckoJar=D:\test-mods\geckolib-neoforge-1.21.1-4.9.2.jar' `
  runGameTestServer --no-configuration-cache
```

These JARs are runtime dependencies only; no pack configuration or existing world
is loaded. EAP scales the byproduct recipe with a seven-operation limit, while an
actual Useless smart-provider marker scales the other two recipes. The suite checks
that native wrapper identities survive reconciliation and all five required tests pass.
Add `-PbyproductAutomatic` for Applied's automatic path or
`-Papplied_enhancements_api_build=1.1.1` for the newer prerequisite. Choose distinct
disposable directories with `-PbyproductRunName=NAME` when comparing runs.
`-PbyproductAppliedJar=ABSOLUTE_JAR_PATH` selects a patched prerequisite only for
the isolated run. Add `-PplanningFallbackChecks` with the patched Applied build
to verify idempotent projected-transfer cleanup as a sixth required test.
The cyclic suite accepts `-PcyclicAppliedJar=ABSOLUTE_JAR_PATH` in the same way.

`src/test` contains repeatable unit tests and runs with the ordinary `test`/`build`
tasks. Temporary client screenshot/GPU probe source sets and dated local reports
were removed; release checks use the maintained tests and resource validation.

## 中文

测试只操作 build 下的隔离世界，夹具配方和测试类型不进入正式 JAR。长数量、
保存/取消、实际 ME 存储总线、第三方能力适配和回压使用可重复测试；图形预览和
一次性探针已清理。检查 GameTest 全部必需用例通过，不能只看 Gradle 返回成功。
