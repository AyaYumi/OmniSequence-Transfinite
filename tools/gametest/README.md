# Forge 1.20.1 regression tests / 回归测试

Run from the repository root with Java 17 and the dependencies in [libs/README.md](../../libs/README.md):

```powershell
./gradlew.bat --no-configuration-cache -I tools/gametest/gametest.init.gradle runGameTestServer
```

| Test | Coverage |
| --- | --- |
| MolecularCenterPatternPortGameTests (2 tests) | Real ME storage bus, Forge item capability, all faces, pattern restrictions, cross-page transfers, immediate crystal updates, empty reload, crystal recovery and dismantle locks |
| MolecularLongBatchGameTests | Real powered single-block array and formed ExtendedAE matrix; both provider limits, 3 billion crafts / 12 billion outputs, NBT round trips, multiplication and buffer-addition overflow with unchanged inputs. Inputs are supplied to real providers; terminal planning is outside this fixture. |
| MatterAEKeyGameTests | Registered third-party keys, numeric long JSON, recipe/menu synchronization, complete input matching, batch partitioning, queued/active/refund NBT, ME returns and identical/proportional inputs producing different outputs |
| MatterRecipeLookupGameTests | Repeated lookup benchmark, candidate order, per-controller research permissions, research load/revoke, bonuses and both recipe reload paths |
| TransfiniteComputeNexusGameTests | Six real AE cables, adjacent CPU isolation, virtual jobs, long counts, world reload, BlockEntityTag portable recovery and power loss/reconnection |
| MultiblockSpawnGameTests (2 tests) | All three formed footprints, chunk edges and full height, animals/bats/monsters, spawner/egg/command exclusions, unload/reload, damage/repair/removal, construction/dismantling, overlapping owners and nexus exclusion |

Nine GameTests run in `build/forge-regression-run`, an isolated world. Test key types, recipes and classes are not included in the distributable JAR. The existing Java/geometry/UI unit tests run through `gradlew test build`.

九项 GameTest 使用 `build/forge-regression-run` 隔离世界，不读取整合包存档。新增两项禁刷测试覆盖真实三种多方块、全部自然生物类别、区块边界／高度、刷怪笼等排除项、施工拆卸、重叠范围及重载释放。测试资源和自定义 AEKey 不打包进正式 JAR。原有 Java、几何和界面单元测试通过 `gradlew test build` 运行。

`MATTER_LOOKUP_BENCH` reports three rounds of 2,000 lookups after 2,000 warm-up calls, with 128 fabrication recipes and 32 research definitions. It measures lookup only, not whole-server TPS. Timing has no pass/fail threshold; functional assertions verify results. Recipe replacements are restored in the same server-thread call.

The Gradle run checks that all nine required tests completed, because Forge may otherwise exit successfully after a startup failure. Wait for native AE2 node initialization and for both controller and assembly to become active before exercising the delivery path.

The nexus fixture uses a fresh chunk on each run and waits for the restored network/CPU
to become ready before continuing. It checks that the nexus recipe is absent without
AdvancedAE; the separate AAE suite checks its ingredients, output, power/time and research unlock.

See [UI checks](../ui/README.md) for the separate JEI 15 suite.

## AdvancedAE integration / AAE 集成验证

```powershell
.\gradlew.bat --no-configuration-cache '-Pextendedae_version=1.20-1.4.18-forge' -I tools/gametest/aae.init.gradle runGameTestServer
```

The separate suite resolves the Forge AAE version declared in `gradle.properties` and runs its real `AdvCraftingCPULogic.executeCrafting` against a real AE grid and Matter Fabrication Well. The test supplies the CPU shell and initial plan/inventory; terminal plan calculation is outside its scope. Covers 65,536 crafts in one dispatch, exact task/waiting amounts, persistence, cancellation, reservations, failure rollback, accepted-then-thrown ownership, backpressure, same-tick time slicing and long overflow headroom.

独立测试世界为 `build/aae-regression-run`，实际加载 Forge AAE 及 CPU Mixin；以 `AAE_BATCH_ALL_PASS` 和全部测试通过标记共同判定成功。AAE 依赖与测试类不打包进正式 JAR。

Both versions validate long recipe counts and pattern-only storage buses. The test matrix uses each loader's ExtendedAE registry namespace. Crystal fixtures remove previous crystal inventories, and the Forge construction fixture clears terrain from its target positions before asserting build ownership.

两个版本均覆盖 long 批量及主样板库专用存储总线。样板测试先清理旧水晶，Forge 施工测试清空目标位置地形，防止隔离世界重复运行的残留影响结果。
