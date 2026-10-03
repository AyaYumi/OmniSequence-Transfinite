# Isolated engine regressions / 隔离游戏测试

Current source: 2.0.8. Test source sets are excluded from a normal runtime JAR.
[Development guide](../../docs/development.md) · [Dependency setup](../../libs/README.md)

## Suites

| Init script | Coverage |
| --- | --- |
| `performance-regression.init.gradle` | Long-count providers, well AEKey inputs/codecs/queues, recipe lookup invalidation, dismantling, Nexus and actual storage-bus pattern access |
| `singularity.init.gradle` | Hub registry/palette, build/dismantle, old motion/embedding migrations, protection events and material conservation |
| `aae.init.gradle` | Actual transformed Omni AE and AdvancedAE CPUs, native/optional batch providers, ownership, rejected/failing admissions, exact counts and per-tick backpressure |
| `ae2-compat.init.gradle` | Earlier/later AE2 extraction and paired-mod long-batch compatibility |
| `gametest.init.gradle` | Combined engine suite, including legacy migrations and spawn protection |

```powershell
.\gradlew.bat -I tools/gametest/performance-regression.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/singularity.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/aae.init.gradle "-PaaeJar=D:/mods/AdvancedAE-1.6.11-1.21.1.jar" "-PgeckoJar=D:/mods/geckolib-neoforge-1.21.1-4.9.2.jar" runGameTestServer --no-configuration-cache
```

Use the matching independent prerequisite in `libs/`. Optional AdvancedAE and
GeckoLib runtime JARs must be passed explicitly; they are not packaged with the mod.
AE2 version can be selected with `-Pae2_version=19.2.17` or the default in
`gradle.properties`.

## Worlds and assertions

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

`src/test` contains repeatable unit tests and runs with the ordinary `test`/`build`
tasks. Temporary client screenshot/GPU probe source sets and dated local reports
were removed; release checks use the maintained tests and resource validation.

## 中文

测试只操作 build 下的隔离世界，夹具配方和测试类型不进入正式 JAR。长数量、
保存/取消、实际 ME 存储总线、第三方能力适配和回压使用可重复测试；图形预览和
一次性探针已清理。检查 GameTest 全部必需用例通过，不能只看 Gradle 返回成功。

The AE compatibility suite also validates native smart-doubling bypass, mixed exact tasks, preserved wrapper identity and both optional submission overloads.

The AE compatibility suite includes native maximum-inventory/byproduct non-rejection, finite supply, exact infinite consumption and native task accumulation checks. Set `-Pae2CompatibilityRunName=ae2-compat-fresh-run` to a new name for a disposable directory directly under `build/`. Do not reuse old fixture worlds when comparing runs.
