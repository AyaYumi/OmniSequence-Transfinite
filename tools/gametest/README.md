# Multiblock regression tests / 多方块回归测试

Run with Java 21 from the repository root after preparing the separate
AppliedEnhancements dependency described in [libs/README.md](../../libs/README.md).
No locally installed modpack or personal world is required.

The 2.0.4 isolated server suite contains 14 tests. All fourteen passed, including the
nexus lifecycle and multiblock natural-spawn protection tests. For the separate client coordinate checks, see
[UI regression checks](../ui/README.md); those do not launch a Minecraft client.

```sh
./gradlew --no-configuration-cache -I tools/gametest/gametest.init.gradle runGameTestServer
```

On Windows, use `./gradlew.bat` with the same arguments. The test world and report
are generated under `build/`. These extra sources are not packaged in the mod JAR.

| Test | Coverage |
| --- | --- |
| MolecularLongBatchGameTests | Real powered single-block array and formed ExtendedAE matrix; both provider limits, 3 billion crafts / 12 billion outputs, NBT round trips, multiplication and buffer-addition overflow with unchanged inputs. Inputs are supplied to real providers; terminal planning is outside this fixture. |
| MolecularDismantleGameTests | Current and official 1.3.9 layouts, block ownership, layer order, capacity pause, saved queues and paused-upgrade recovery |
| OmniDismantleGameTests | Both layouts and orientations, exact block queue, per-tick budget, stale targets, capacity pause and NBT resume |
| MatterDismantleGameTests | Current well, holes and changed blocks, service modules, packed items/fluids/patterns and no duplicate drops |
| MatterAEKeyGameTests | Registered third-party AEKey inputs, recipe/menu codecs, exact matching, long batches, queued/active/refund persistence and return to ME |
| MatterRecipeLookupGameTests | Repeated lookup benchmark, ordered output candidates, live research permissions, separate controller progress, depth bonuses and data-pack/replaceRecipes invalidation |
| Legacy139MigrationGameTests | Four-direction 1.3.9 upgrades and save/reload, retained contents, stopped retired layouts and safe old-cursor handling |
| TransfiniteComputeNexusGameTests | Six real AE smart cables, adjacent CPU isolation, virtual jobs, long material counts, world reload, retained drops and power recovery |
| MultiblockSpawnGameTests (2 tests) | All three formed footprints, chunk edges and full height, animals/bats/monsters, spawner/egg/command exclusions, unload/reload, damage/repair/removal, construction/dismantling, overlapping owners and nexus exclusion |
| MolecularCenterPatternPortGameTests (2 tests) | Real ME storage bus, all controller faces, accepted/rejected pattern types, simulation and single-slot limits, cross-page transfers, private-slot exclusion, crystal updates, empty reload, crystal break/restore and dismantle lock |

共 14 项游戏测试。两项样板接口测试覆盖真实存储总线存取及水晶数据所有权。新增两项自然生成保护测试覆盖三种真实结构、区块边界和高度、全部自然生物类别、生成来源排除项及重载／施工／拆卸／重叠范围。测试类型与配方只存在于隔离测试环境，不打包进正式 JAR。另有 `src/test` 中的单元测试，随 `./gradlew test build` 运行。
先按 [libs/README.md](../../libs/README.md) 准备独立前置，游戏测试使用
`build/multiblock-regression-run` 隔离世界，不读取玩家整合包存档。
升级测试每次选取新坐标，避免旧施工状态影响结果；模板资源由本目录提供。

`MATTER_LOOKUP_BENCH` 日志记录 128 个构筑配方、32 个研究定义下的配方查找耗时：先预热 2,000 次，再测量 3 轮、每轮 2,000 次。比较相同机器与运行参数下的三轮中位数；这是查找环节的合成基准，不代表整个服务器 Tick 的加速比例。测试只校验查找结果，不对耗时设置易受环境影响的通过阈值。测试期间临时替换的配方会在同一服务端调用内恢复。

## AdvancedAE 批量派发集成测试

单独提供 AAE 与 GeckoLib JAR 路径，实际加载可选模组及 CPU Mixin；不将这些依赖或测试类打包进正式 JAR。

```powershell
.\gradlew.bat --no-configuration-cache -I tools/gametest/aae.init.gradle '-PaaeJar=D:/mods/AdvancedAE-1.6.11-1.21.1.jar' '-PgeckoJar=D:/mods/geckolib-neoforge-1.21.1-4.9.2.jar' runGameTestServer
```

测试世界为 `build/aae-regression-run`。使用真实 AAE `AdvCraftingCPULogic.executeCrafting`、真实 AE 网络与物质构筑井；测试 CPU 外壳连接到测试网络，计划和 CPU 库存由夹具初始化，不包含终端的计划计算阶段。覆盖 65,536 份充能配方一次派发、准确任务/待回计数、队列存档、取消任务、容量预留导致忙碌、接受前后异常、拒收回滚、跨 Tick 背压、逐份时间预算和 long 溢出边界。日志 `AAE_BATCH_ALL_PASS` 表示全部场景通过。

Both versions validate long recipe counts and pattern-only storage buses. The test matrix uses each loader's ExtendedAE registry namespace. Crystal fixtures remove previous crystal inventories, and the Forge construction fixture clears terrain from its target positions before asserting build ownership.

两个版本均覆盖 long 批量及主样板库专用存储总线。样板测试先清理旧水晶，Forge 施工测试清空目标位置地形，防止隔离世界重复运行的残留影响结果。
