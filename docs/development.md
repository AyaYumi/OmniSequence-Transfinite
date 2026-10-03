# Development and release / 开发与发布

Current source: 2.0.8, Java 21, Minecraft 1.21.1, NeoForge 21.1.220.
[API index](README.md) · [Dependency setup](../libs/README.md)

## Dependency input

The source requires `libs/appliedenhancements-1.1.1.jar`, including
`AelisBatchExecutionContext` and `AelisSmartDoublingApi`. Obtain the revised
AppliedEnhancements build and install the same version on both sides. Dependency
JARs are ignored and never embedded in OmniSequence.

CI builds the prerequisite from published AppliedEnhancements commit
`b42bbfaf53ecb15add0a88f8d036ce750bdc381b`, which provides the 1.1.1 shared
API. Every push validates documents/resources and performs the Java build/test.
Repository variable `APPLIED_ENHANCEMENTS_JAR_URL` or workflow-dispatch input
`applied_enhancements_jar_url` can override the pinned source with a trusted
matching artifact. The API is validated before compilation. Dependencies remain
separately installed and are never shaded into the mod.

## Build and API documentation

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean build apiJavadoc --no-configuration-cache
```

Default dependencies are in `gradle.properties`. To verify the earlier supported
AE2 minor version, add `-Pae2_version=19.2.17`.

| Output | Purpose |
| --- | --- |
| `build/libs/omnisequence-transfinite-2.0.8.jar` | Runtime mod |
| `build/docs/api/index.html` | Generated public Java API documentation |
| `build/reports/tests/test/index.html` | Unit test results |

`apiJavadoc` uses compiled main classes and documents public crafting/research
entry points. The human-readable Chinese/English SPI and data-pack contracts live
in `docs/`. Generated HTML is local output, not duplicated source.

## Isolated engine regressions

```powershell
.\gradlew.bat -I tools/gametest/performance-regression.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/singularity.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/aae.init.gradle "-PaaeJar=D:/mods/AdvancedAE-1.6.11-1.21.1.jar" "-PgeckoJar=D:/mods/geckolib-neoforge-1.21.1-4.9.2.jar" runGameTestServer --no-configuration-cache
```

Tests use disposable directories below `build/`. Never point them at a player's
save. Optional test source sets and fixture recipes are not part of a normal JAR.
Details: [engine test guide](../tools/gametest/README.md). Unit tests remain under
`src/test`; they run with `test` and `build`.

## Release checks

Run the unit/engine tests appropriate to the change. Inspect the normal JAR for
unexpected probe/test classes, private paths, unused assets or nested dependency
packages. Compare the installed JAR hash with the built artifact. Back up the old
runtime JAR outside `mods/` before replacing it, and restart the instance.

Documentation links must resolve locally. JSON resources and animation metadata
must parse. Current sprite strips are runtime assets; static editable animation
originals are maintenance sources. Check item/fluid frames without regenerating
author artwork in place.

## Asset maintenance

[Texture sources](../tools/textures/README.md) document the maintained generators.
Keep actual models, shaders and images in `src/main/resources`, source originals
under `tools/textures/static`, and generated previews under ignored `build/`.
One-off client/GPU probes, shader-pack experiments, initial redraw generators and
dated local result reports were removed. Do not restore a deleted generator as a
release dependency.

## Logging

Use `RateLimitedLog` with a constant message template in any runtime/reload path
that can repeat. It shares a one-minute category window across machines/CPUs.
Use parameters for positions, IDs and exception data; never concatenate them into
the category key. Normal operation needs no per-tick or per-item success logging.
Profiler output is optional and aggregates globally. Startup migration messages
may describe a real file change.

## 中文

构建使用修订版 AppliedEnhancements 1.1.1，前置 JAR 不入库也不嵌入产物。远程
构建默认使用已发布的固定 Git 提交，可通过受信任的 JAR 地址覆盖。不能将旧前置
改名当作新版 API。JavaDoc 可重新生成到 build/docs/api。

隔离测试全部使用 build 下的临时世界。正式资源、可编辑动画原图和可重复回归测试
保留；一次性截图代码、实验图片和机器相关测试报告移除。运行日志使用常量模板
全局限流，动态位置和 ID 只作为参数传入。
