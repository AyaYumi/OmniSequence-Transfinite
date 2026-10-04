# Forge engine regressions / Forge 隔离游戏测试

Current source: 2.0.7-forge, Minecraft 1.20.1, Java 17.
[Dependency setup](../../libs/README.md)

| Init script | Coverage |
| --- | --- |
| `gametest.init.gradle` | Machine import JSON/reload, exact AEKey inputs, pattern ports, long-count providers, Nexus and protected multiblock spawning |
| `singularity.init.gradle` | Thirteen-block palette, construction, protection rollback, ME material priority, motion/carriage, docking, suspended/legacy migrations and cached miniature-hole capture |
| `maintenance.init.gradle` | Well/Sequence/Omni dismantling, protected decorations, saved queues and official 1.3.9 blueprint upgrades |
| `ae2-compat.init.gradle` | Storage-bus type-switch rechecks with all optimization combinations and paired-mod long batches |
| `aae.init.gradle` | Actual transformed AE2/AdvancedAE CPUs, native and optional batch providers, accepted ownership, exact counts and backpressure |

```powershell
.\gradlew.bat -I tools/gametest/gametest.init.gradle runGameTestServer --console=plain
.\gradlew.bat -I tools/gametest/singularity.init.gradle runGameTestServer --console=plain
.\gradlew.bat -I tools/gametest/aae.init.gradle runGameTestServer --console=plain
```

Default AE2 is 15.4.10. Add `-Pae2_uelm_version=15.5.4-uelm` to build and run
against UELM. Each classpath contains exactly one AE implementation and the
separately built Applied Enhancements 1.1.0-forge prerequisite in `libs/`.
The optional AAE suite resolves Forge AdvancedAE and GeckoLib through the
CurseMaven properties in `gradle.properties`; these are never packaged in Omni.

Each suite uses a disposable world under `build/` and checks its required-test
success marker. Runtime fixtures and worlds are excluded from the release JAR.
Config-changing pattern-port and spawn tests disable the disposable config file
watcher and copy its values into memory before testing. Their separate batches
avoid affecting other fixtures while force loading is temporarily disabled.
Assertions and required-test counts remain enabled; no failed tests are skipped.
Do not run fixtures in personal saves. Unit tests run with `build` and cover
logic/serialization boundaries. Engine tests exercise actual transformed
classes; they are not a full modpack TPS benchmark or client visual test.

## 中文

两种 AE 实现分别编译和运行；不能只看版本声明或单元测试。GameTest 必须全部
通过才算该套验收完成。测试会搭建大结构、强制加载测试区块并改写夹具配方，
只使用 build 下的独立世界。源码中的动画原图和测试夹具是维护资源，临时日志、
截图、生成文档和测试世界不进入正式 JAR。

修改配置的样板端口和刷怪保护测试先关闭隔离配置的文件监听，再使用内存副本，
避免自动保存／重载把临时页数和强制加载值改回。相关用例划分独立批次；原有断言
及必需测试数量继续保留，不跳过失败用例。正式游戏的配置热重载不受影响。

The AE compatibility suite includes native maximum-inventory/byproduct non-rejection, finite supply, exact infinite consumption and native task accumulation checks. Set `-Pae2CompatibilityRunName=ae2-compat-fresh-run` to a new name for a disposable directory directly under `build/`. Do not reuse old fixture worlds when comparing runs.

The paired AE suite now requires seven tests, including selected interception removal, unknown rewrite restoration, finite multiplier remainder repair, native fallback with the exact preference disabled, and finite-stock CRAFT_LESS output accounting.
