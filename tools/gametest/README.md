# Isolated game tests / 隔离游戏测试

These scripts run gameplay regressions in disposable worlds or clients. Choose the
suite for the behavior you changed; test classes and fixture resources are excluded
from the normal runtime JAR. Current source: 2.0.14.

测试入口统一在本页。使用 Java 21，在仓库根目录选择一套测试运行；只用 `build/`
下的隔离目录，不要指向个人存档。必须检查全部必需用例和对应通过标记，不能只看启动成功。

## Quick start

Use JDK 21 and prepare the dependencies in [libs/README.md](../../libs/README.md).
Run one init script at a time, serially with other builds:

```powershell
.\gradlew.bat -I tools/gametest/auto-crafting.init.gradle runGameTestServer --no-configuration-cache
```

Replace the init script with a suite below and add its properties before the task.
AE2 defaults to the version in `gradle.properties`; add `-Pae2_version=19.2.17` to
select that runtime. `applied_enhancements_api_build` selects the compile API JAR in
`libs/`; the supported dependency floor is documented there.

Use a fresh disposable directory when comparing runs. Fixtures may replace recipes,
force chunks and build large structures; old fixture blocks can obstruct later runs.
Inspect `logs/latest.log` in the suite's run directory and the Gradle console:
**all registered required tests must pass**, with the counts and markers below.
A successful Gradle launch or server startup alone is insufficient.

## Server suites

All entries use `runGameTestServer` unless the client section says otherwise.

| Init script | Coverage / required gate |
| --- | --- |
| `auto-crafting.init.gradle` | Passive crafting, reserves, chained outputs, long counts, recipe reload and machine removal: 12 tests; 13 with the depot opt-in |
| `performance-regression.init.gradle` | Long-count providers, recipe/research lookups, well queues, dismantling, Nexus and storage-bus access |
| `cpu-batch-compat.init.gradle` | Optional CPU batch APIs, input/output conservation, queues, refunds and optional-mod absence: 1 test |
| `processing-dispatch.init.gradle` | ExtendedAE/Lightning providers, smelting/infusion and fallback backpressure: 12 tests |
| `eco.init.gradle` | ECO CPU scheduling, dispatch, output, admission and cancellation: 28 tests and `MATTER_ECO_API_PASS` |
| `performance-backport.init.gradle` | Ownership, native batches, fluids, migration and Hub motion with unavailable chunks: 8 tests |
| `singularity.init.gradle` | Hub registration, build/dismantle, saved-state migration, protection and material conservation |
| `singularity-quantum.init.gradle` | AE2 frequency ownership, retuning, delayed registration, reload and rebuild |
| `singularity-menu-sync.init.gradle` | Large resource-list transfer, cache invalidation and collection settings: 3 tests; see below |
| `pattern-migration.init.gradle` | Array migration, native encoding/upload, old-core recovery and controller effect persistence: 15 tests plus opt-ins; see below |
| `fabrication-network-upload.init.gradle` | Well upload using ME blanks, renamed targets and optional EAEP/team integration: 2 or 5 tests; see below |
| `compact-singularity.init.gradle` | Compact Hub connections, collection/duplication, capacity waits and retained drops |
| `cosmic-holes.init.gradle` | Hole placement, ownership, routing, item retention, removal fallback and saved bindings |
| `aae.init.gradle` | AdvancedAE CPU/provider integration, ownership, counts and backpressure; requires explicit JARs |
| `ae2-compat.init.gradle` | AE2 extraction, native bypass, mixed exact tasks and long-count providers with Applied 1.1.0 |
| `paired-api.init.gradle` | Paired Applied API integration, native rewriting and exact quantities: 7 tests |
| `fabrication-dispatch.init.gradle` | Finite-power Nexus deliveries, long counts, durable queues and refunds: 2 server tests |
| `cyclic-submission.init.gradle` | Real cyclic planning, summary, submission, shortages and completion: 4 tests |
| `byproduct-submission.init.gradle` | Planning/submission with secondary outputs and shortage rejection: 5 tests; 6 with fallback checks |
| `applied-110.init.gradle` | Original Applied 1.1.0 compatibility fixture: 3 tests |
| `outer-wilds.init.gradle` | Gravity Crystal and Ghost Matter placement, motion, water, damage and persistence: 7 server tests |
| `catalyst-sharing.init.gradle` | Catalyst codecs, shared stock, admission, pause/resume and saved work: 2 tests |
| `fabrication-upload.init.gradle` | Encoding/upload quantities, invalid requests, destinations and placement-time team binding |
| `gametest.init.gradle` | Combined engine regressions, including legacy migrations and spawn protection |

## Array migration and native upload

```powershell
.\gradlew.bat -I tools/gametest/pattern-migration.init.gradle -PpatternMigrationRunName=pattern-migration-check runGameTestServer --no-configuration-cache
.\gradlew.bat test --tests 'com.atir.molecularmanipulator.crafting.PatternUploadIndexTest' --no-configuration-cache
```

The default runtime requires **15** GameTests: public-container/cell migration,
actual migration-button handling, capacity and callback recovery, native terminal
encoding, duplicate refunds, operational/ownership gates, old-core returns and
saved controller settings. `PatternUploadIndexTest` requires all **9** unit tests.

For a formed ECO cluster and real AE Omni Cells 1.1.6 disks, add both opt-ins:

```powershell
.\gradlew.bat -I tools/gametest/pattern-migration.init.gradle `
  -PpatternMigrationRunName=pattern-migration-addons -PpatternMigrationCluster `
  '-PcpuCompatPackMods=<dependency-directory>' -PcpuCompatEcoJar=neoecoae-21.2.13.jar `
  '-PpatternMigrationStorageJar=<absolute-path>/ae2omnicells-1.21.1-neoforge-1.1.6.jar' `
  runGameTestServer --no-configuration-cache
```

Each opt-in adds one required test: **17** with both, **16** with either alone.
Cluster tests need ECO >= 21.2.10 and one LDLib2 `all` JAR >= 2.2.40 in the selected
directory; omit `cpuCompatEcoJar` to use `libs/neoecoae-21.2.10.jar`.
The storage-cell fixture needs the actual AE Omni Cells 1.1.6 JAR.

Require `PATTERN_MIGRATION_NETWORK_PASS`, `PATTERN_MIGRATION_BUTTON_PASS` and
`PATTERN_MIGRATION_HOST_RECOVERY_PASS` in `build/<patternMigrationRunName>/logs/latest.log`.
The opt-ins additionally require `PATTERN_MIGRATION_CLUSTER_NETWORK_PASS` and
`PATTERN_MIGRATION_ADDON_CELL_PASS`, respectively. The init script enforces these
markers and the exact required-test count.

## Well network blanks and renamed targets

```powershell
.\gradlew.bat -I tools/gametest/fabrication-network-upload.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/fabrication-network-upload.init.gradle `
  '-PfabricationEaepJar=<absolute-path>/extendedae_plus-1.6.4.1.jar' -PfabricationFtbTests `
  '-PfabricationNetworkUploadDirectory=build/fabrication-upload-eaep-ftb' `
  runGameTestServer --no-configuration-cache
```

Require **2** tests by default or **5** with EAEP. The two base cases use default
and renamed assemblies with terminal blanks = 0 and ME blanks = 1: one upload
must consume one blank, retain all recipe quantities and create exactly one pattern;
a second click must not create another. The optional cases exercise EAEP's actual
selector/uploader, stale-name retention, foreign-team/detached rejection and explicit
provider-name selection. Direct upload targets loaded same-team wells; EAEP provider
tests additionally require the terminal's ME grid. Neither covers client clicks or
encode-request transport.

Use the NeoForge **EAEP 1.6.4.1** JAR; these integration hooks target that exact
version. `fabricationFtbTests` adds real FTB party-member coverage without changing
the test count; without it, tests use personal ownership. The default directory is
`build/fabrication-network-upload-run`; choose a fresh directory for each variant.

## Hub resource-list synchronization

```powershell
.\gradlew.bat test --tests '*SingularityCollection*Test' --no-configuration-cache
.\gradlew.bat -I tools/gametest/singularity-menu-sync.init.gradle -PsingularityMenuSyncRunName=hub-menu-check runGameTestServer --no-configuration-cache
```

Require all **3** GameTests and all selected unit tests. Markers:
`SINGULARITY_MENU_LARGE_SYNC_PASS`, `SINGULARITY_RESOURCE_CACHE_PASS`,
`SINGULARITY_COLLECTION_CONFIG_PASS` and `SINGULARITY_COLLECTION_LIVE_PASS`.
The large-list fixture must exceed **35,822** equivalent CSV characters, deliver
the complete ordered list in chunks of at most **128** IDs, encode scalar menu
state, and avoid steady-state resends. Cache checks cover tag/config changes;
collection checks cover batch/interval changes, storage waits and saved reloads.
Use a different `singularityMenuSyncRunName` when selecting another AE2 version.

## Optional runtimes and run directories

Only explicitly selected optional JARs are staged; do not copy a whole modpack
into a test runtime. JAR paths below are local inputs, not bundled dependencies.

| Suite | Properties / dependencies |
| --- | --- |
| Auto crafting | `-PautoCraftingRegressionDirectory=build/<name>`; depot test needs both `-PautoCraftingDataJar=<JAR>` (Data Energistics 3.3.0 or 3.3.5) and `-PautoCraftingLdlibJar=<LDLib2-2.2.40+-all-JAR>` |
| Compact Hub | `-PcompactSingularityRunName=<name>`; add `-PcompactSingularityCollectionOnly` for collection batch/interval checks only |
| Hub / quantum | `-PsingularityRegressionDirectory=build/<name>` / `-PsingularityQuantumRunName=<name>` |
| Performance / combined | `-PperformanceRegressionDirectory=build/<name>` / `-PmultiblockRegressionDirectory=build/<name>` |
| AdvancedAE | `-PaaeJar=<AdvancedAE-1.6.11-JAR>` and `-PgeckoJar=<GeckoLib-4.9.2-JAR>` |
| Hole ownership / fabrication upload | Add `-PcosmicFtbTests` / `-PfabricationFtbTests` for real FTB Teams; also run without them for personal ownership |
| Catalysts | `-PcatalystSharingRunName=<name>`; optional `-PcatalystPackMods=<directory>` needs `ae2lt-2.1.0-beta.6.jar` and `thunderbolt-2.0.0-beta.4.jar` for real recipe imports |
| Cyclic submission | `-PcyclicRunName=<name>`; run both default explicit planning and `-PcyclicAutomatic`; `-PcyclicAppliedJar=<absolute-JAR-path>` selects a runtime override |
| Byproduct submission | `-PbyproductRunName=<name>`, `-PbyproductAutomatic`, `-PbyproductAppliedJar=<absolute-JAR-path>`; `-PplanningFallbackChecks` needs a patched Applied runtime and raises the gate to 6 tests |
| Byproduct wrappers | Supply `-PbyproductUselessJar=<JAR>` (2.4.5.6), `-PbyproductEapJar=<JAR>` (EAEP 1.5.5), `-PbyproductAaeJar=<JAR>` (1.6.11) and `-PbyproductGeckoJar=<JAR>` (4.9.2) for mixed native wrappers |
| Fabrication dispatch | `-PfabricationDispatchRunName=<name>`; `-PfabricationAppliedJar=<absolute-JAR-path>` selects a runtime override |
| Paired API / original Applied | `-Papplied_enhancements_api_build=<build>` and `-Pae2CompatibilityRunName=<name>` / `-Papplied110RunName=<name>` |

### Optional CPU APIs

`cpu-batch-compat.init.gradle` without `cpuCompatMods` checks optional-mod absence.
For real adapters, add `'-PcpuCompatMods=eco,thunderbolt,data,lightning'` and
`'-PcpuCompatPackMods=<dependency-directory>'`. Use `-PcpuCompatRunName=<name>`
for separate worlds and staging directories. ECO/Data/Lightning need one LDLib2
`all` JAR >= 2.2.40 in that directory. Dependency defaults are in [libs/README.md](../../libs/README.md).

Select filenames from that directory with `cpuCompatEcoJar`, `cpuCompatThunderboltJar`,
`cpuCompatLightningJar`, `cpuCompatAppliedJar` and `cpuCompatExtendedJar`; use
`cpuCompatExtraMods` for a comma-separated list of additional JAR filenames.
ECO must be >= **21.2.10**. The ECO suite enables ECO by default; require **28**
tests (`-PecoCompatExpectedTests=28`) and `MATTER_ECO_API_PASS`.
To exercise the Applied runtime floor, select `-PcpuCompatAppliedJar=appliedenhancements-1.1.2.jar`
from the dependency directory; the backport suite also accepts these selectors.

### External processing dispatch

The factory fixture needs Mekanism **and Mekanism Generators**, Mekanism Extras,
Applied Mekanistics, EAEP and BatchCraft in addition to the selected providers.
Use actual filenames from one dependency directory, for example:

```powershell
.\gradlew.bat -I tools/gametest/processing-dispatch.init.gradle `
  '-PcpuCompatPackMods=<dependency-directory>' '-PcpuCompatRunName=processing-check' `
  '-PcpuCompatMods=thunderbolt,lightning' `
  '-PcpuCompatThunderboltJar=thunderbolt-2.0.3.jar' '-PcpuCompatLightningJar=ae2lt-2.1.2.jar' `
  '-PcpuCompatAppliedJar=appliedenhancements-1.1.4-remainder-fix.jar' `
  '-PcpuCompatExtendedJar=ExtendedAE-1.21-2.2.38-neoforge.jar' `
  '-PcpuCompatExtraMods=Mekanism-1.21.1-10.7.19.85.jar,MekanismGenerators-1.21.1-10.7.19.85.jar,mekanism_extras-1.21.1-1.4.1.jar,extendedae_plus-1.6.4.1.jar,ae2-batchcraft-1.21.1-1.0.3-neoforge.jar,Applied-Mekanistics-1.6.3.jar' `
  runGameTestServer --no-configuration-cache
```

Require **12** tests, including 10,000-product completion, exact item/chemical
accounting, empty final queues and per-input backpressure. To include ECO, add it
to `cpuCompatMods` and select `-PcpuCompatEcoJar=neoecoae-21.2.13.jar`.

## Client checks

Use a working OpenGL desktop. Replace `runGameTestServer` with `runClient` and
add the option from the table; check the named completion marker in the run log.
For example:

```powershell
.\gradlew.bat -I tools/gametest/singularity-lens.init.gradle -PsingularityLensRunName=lens-check runClient --no-configuration-cache
```

| Init script | Client option | Required marker / coverage |
| --- | --- | --- |
| `singularity-lens.init.gradle` | Optional `-PsingularityLensRunName=<name>` | `LENS_BOBBING_CLIENT_PASS`: three effects, no bob plus eight walking phases, visible cores and foreground-depth occlusion; title-screen GPU check |
| `fabrication-dispatch.init.gradle` | `-PfabricationMenuClient` | `MATTER_ASSEMBLY_MENU_CLIENT_PASS`: 12 network page changes and all pattern/catalyst slots in one open screen |
| `outer-wilds.init.gradle` | `-PouterWildsClientModels` | `OUTER_WILDS_MODELS_PASS`: block/item models, materials, bounds and missing textures |
| `outer-wilds.init.gradle` | `-PghostMatterClient` | `GHOST_MATTER_CLIENT_PASS`: health sync, water suppression, expiry, fading and rendering |
| `outer-wilds.init.gradle` | `-PgravityWalkingClient` | `GRAVITY_WALKING_CLIENT_PASS`: walking/jumping, server/client agreement, camera modes and recovery |
| `antarchy-gravity.init.gradle` | `-PantarchyModsDir=<directory>` containing Antarchy 2.2.1 and its dependencies | `ANTARCHY_GRAVITY_CLIENT_PASS`: camera/gravity handoff, collision and ceiling walking |

Ordinary unit tests run with `.\gradlew.bat test --no-configuration-cache`.
