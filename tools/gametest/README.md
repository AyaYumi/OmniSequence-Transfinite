# Forge 1.20.1 regression tests / 回归测试

Run from the repository root with Java 17 and the dependencies in [libs/README.md](../../libs/README.md):

```powershell
./gradlew.bat --no-configuration-cache -I tools/gametest/gametest.init.gradle runGameTestServer
```

| Test | Coverage |
| --- | --- |
| MatterAEKeyGameTests | Registered third-party keys, numeric long JSON, recipe/menu synchronization, complete input matching, batch partitioning, queued/active/refund NBT, ME returns and identical/proportional inputs producing different outputs |
| MatterRecipeLookupGameTests | Repeated lookup benchmark, candidate order, per-controller research permissions, research load/revoke, bonuses and both recipe reload paths |

Three GameTests run in `build/forge-regression-run`, an isolated world. Test key types, recipes and classes are not included in the distributable JAR. The existing Java/geometry/UI unit tests run through `gradlew test build`.

三项 GameTest 使用 `build/forge-regression-run` 隔离世界，不读取整合包存档。测试资源和自定义 AEKey 不打包进正式 JAR。原有 Java、几何和界面单元测试通过 `gradlew test build` 运行。

`MATTER_LOOKUP_BENCH` reports three rounds of 2,000 lookups after 2,000 warm-up calls, with 128 fabrication recipes and 32 research definitions. It measures lookup only, not whole-server TPS. Timing has no pass/fail threshold; functional assertions verify results. Recipe replacements are restored in the same server-thread call.

The Gradle run checks that all three required tests completed, because Forge may otherwise exit successfully after a startup failure. Wait for native AE2 node initialization and for both controller and assembly to become active before exercising the delivery path.

## AdvancedAE integration / AAE 集成验证

```powershell
.\gradlew.bat --no-configuration-cache '-Pextendedae_version=1.20-1.4.18-forge' -I tools/gametest/aae.init.gradle runGameTestServer
```

The separate suite resolves the Forge AAE version declared in `gradle.properties` and runs its real `AdvCraftingCPULogic.executeCrafting` against a real AE grid and Matter Fabrication Well. The test supplies the CPU shell and initial plan/inventory; terminal plan calculation is outside its scope. Covers 65,536 crafts in one dispatch, exact task/waiting amounts, persistence, cancellation, reservations, failure rollback, accepted-then-thrown ownership, backpressure, same-tick time slicing and long overflow headroom.

独立测试世界为 `build/aae-regression-run`，实际加载 Forge AAE 及 CPU Mixin；以 `AAE_BATCH_ALL_PASS` 和全部测试通过标记共同判定成功。AAE 依赖与测试类不打包进正式 JAR。
