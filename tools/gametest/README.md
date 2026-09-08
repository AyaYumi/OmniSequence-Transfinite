# Multiblock regression tests / 多方块回归测试

Run with Java 21 from the repository root after preparing the separate
AppliedEnhancements dependency described in [libs/README.md](../../libs/README.md).
No locally installed modpack or personal world is required.

```sh
./gradlew --no-configuration-cache -I tools/gametest/gametest.init.gradle runGameTestServer
```

On Windows, use `./gradlew.bat` with the same arguments. The test world and report
are generated under `build/`. These extra sources are not packaged in the mod JAR.

| Test | Coverage |
| --- | --- |
| MolecularDismantleGameTests | Current and official 1.3.9 layouts, block ownership, layer order, capacity pause, saved queues and paused-upgrade recovery |
| OmniDismantleGameTests | Both layouts and orientations, exact block queue, per-tick budget, stale targets, capacity pause and NBT resume |
| MatterDismantleGameTests | Current well, holes and changed blocks, service modules, packed items/fluids/patterns and no duplicate drops |
| MatterAEKeyGameTests | Registered third-party AEKey inputs, recipe/menu codecs, exact matching, long batches, queued/active/refund persistence and return to ME |
| MatterRecipeLookupGameTests | Repeated lookup benchmark, ordered output candidates, live research permissions, separate controller progress, depth bonuses and data-pack/replaceRecipes invalidation |
| Legacy139MigrationGameTests | Four-direction 1.3.9 upgrades and save/reload, retained contents, stopped retired layouts and safe old-cursor handling |

共 8 项游戏测试。新增 AEKey 测试类型与配方只存在于隔离测试环境，不打包进正式 JAR。另有 `src/test` 中的单元测试，随 `./gradlew test build` 运行。
先按 [libs/README.md](../../libs/README.md) 准备独立前置，游戏测试使用
`build/multiblock-regression-run` 隔离世界，不读取玩家整合包存档。
升级测试每次选取新坐标，避免旧施工状态影响结果；模板资源由本目录提供。

`MATTER_LOOKUP_BENCH` 日志记录 128 个构筑配方、32 个研究定义下的配方查找耗时：先预热 2,000 次，再测量 3 轮、每轮 2,000 次。比较相同机器与运行参数下的三轮中位数；这是查找环节的合成基准，不代表整个服务器 Tick 的加速比例。测试只校验查找结果，不对耗时设置易受环境影响的通过阈值。测试期间临时替换的配方会在同一服务端调用内恢复。
