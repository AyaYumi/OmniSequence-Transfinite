# Development and release / 开发与发布

Current source: 2.0.7-forge, Java 17, Minecraft 1.20.1, Forge 47.4.20.
[API index](README.md) · [Dependency setup](../libs/README.md)

## Dependency input

The source requires `libs/appliedenhancements-1.1.0-forge.jar`, including
`com.appliedenhancements.api.AelisBatchExecutionContext`. Obtain the revised
AppliedEnhancements build and install the same version on both sides. Dependency
JARs are ignored and never embedded in OmniSequence.

CI builds the prerequisite from published AppliedEnhancements commit
`83c9052851487172fd75b4fd45dc3b168d05e74f`, which provides the 1.1.0-forge shared
API. Every push validates documents/resources and performs the Java build/test.
CI uses the same upstream-built prerequisite JAR with both AE implementations. The API is validated before compilation. Dependencies remain
separately installed and are never shaded into the mod.
CI uses published ExtendedAE 1.4.18; Project Infinity 0.1 validation uses 1.4.20 with EAEP 1.6.2, AdvancedAE 1.3.6 and Radium 0.14.2.
GuideME resolves from published Modrinth Maven coordinates without a local JAR.

## Build and API documentation

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat clean build apiJavadoc --no-configuration-cache
```

Default dependencies are in `gradle.properties`. To build against UELM, add `"-Pae2_uelm_version=15.5.4-uelm"`.

| Output | Purpose |
| --- | --- |
| `build/libs/omnisequence-transfinite-2.0.7-forge.jar` | Runtime mod |
| `build/docs/api/index.html` | Generated public Java API documentation |
| `build/reports/tests/test/index.html` | Unit test results |

`apiJavadoc` uses compiled main classes and documents public crafting/research
entry points. The human-readable Chinese/English SPI and data-pack contracts live
in `docs/`. Generated HTML is local output, not duplicated source.

## Isolated engine regressions

```powershell
.\gradlew.bat -I tools/gametest/gametest.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/singularity.init.gradle runGameTestServer --no-configuration-cache
.\gradlew.bat -I tools/gametest/aae.init.gradle runGameTestServer --no-configuration-cache
```

Tests use disposable directories below `build/`. Never point them at a player's
save. Optional test source sets and fixture recipes are not part of a normal JAR.
Details: [engine test guide](../tools/gametest/README.md). Unit tests remain under
`src/test`; they run with `test` and `build`.

The [paired client gate](../tools/client/README.md) creates a fresh disposable world
for real menu packets, eight screen pages and slot insertion/removal. Screenshots
stay below `build/` and need visual review. See [performance maintenance](performance.md)
for idle paths, cache invalidation and bounded tick work.

The [installed-pack runner](../tools/pack/README.md) copies a Forge pack's mods,
configuration and KubeJS into a fresh directory. It verifies production mappings,
world entry, the same real menus, 36 engine workflows and a separate sustained
workload under the actual mod mix.
No player saves or launcher credentials enter the isolated run. Test fixtures are
combined with main classes only in the verification archive, before reobfuscation;
this avoids module split packages and preserves inherited Minecraft method names.

## Release checks

Production JARs must pass `verifyReleaseMixins` (included in `check`/`build`). It
checks the Mixin configuration reference and packaged SRG method/field mappings;
a development client uses named classes and cannot establish production startup
compatibility.

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

构建使用修订版 AppliedEnhancements 1.1.0-forge，前置 JAR 不入库也不嵌入产物。远程
构建默认使用已发布的固定 Git 提交，用同一份原版 AE 构建的前置分别验证两套 AE 实现。不能将旧前置
改名当作新版 API。JavaDoc 可重新生成到 build/docs/api。

隔离测试全部使用 build 下的临时世界。正式资源、可编辑动画原图和可重复回归测试
保留；一次性截图代码、实验图片和机器相关测试报告移除。运行日志使用常量模板
全局限流，动态位置和 ID 只作为参数传入。

正式 JAR 必须通过 `verifyReleaseMixins`，已接入 `check`／`build`。它检查配置引用及
JAR 内 SRG 方法、字段映射；开发客户端使用命名类，不能代替正式包启动验收。
