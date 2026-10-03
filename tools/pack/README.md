# Installed Forge pack verification / 实际整合包验证

This optional runner tests production-mapped classes with an installed Minecraft
1.20.1 Forge pack. Build the normal AppliedEnhancements 1.1.0-forge JAR first and
copy it into Omni's `libs/`. Build Omni's normal release and the verification archive:

```powershell
.\gradlew.bat build apiJavadoc --console=plain
.\gradlew.bat -I tools/pack/pack.init.gradle packVerificationJar --console=plain
python tools/pack/run_pack.py --pack-dir '<instance-directory>' --launch-bat '<launcher-batch>' --applied-jar '<rebuilt-Applied-JAR>'
```

Use Java 17 for Gradle. The Windows launcher batch must contain the selected pack's
complete Java invocation. Its Java runtime, libraries and assets are reused;
account fields are replaced with offline test values. No account command is printed
or saved. Mods, config, defaultconfigs, KubeJS and scripts are copied into a NEW
directory directly under `build/`. Saves are never copied. The client creates its
own flat world, checks real menus and then runs the selected GameTests.

The test archive combines main and fixture classes before reobfuscation to retain
Minecraft inheritance mappings and avoid module split packages. The runner merges
that archive with the release's nested dependencies. Install neither verification
archive in normal packs; normal `build` outputs contain no fixtures.

Optional `--run-name` selects a new build directory; existing directories are
rejected. `--tests sustained` selects the sustained workload only, after all client
pages. An empty filter runs every registered flow. Completion requires both
`PAIRED_CLIENT_ALL_PASS` and `PACK_FLOW_ALL_PASS`; process launch alone is not success.
Review all eight screenshots, check failure/fatal markers and inspect logs for
crashes or repeated failures. The client exits after the suite.

| Coverage | Evidence |
| --- | --- |
| Production startup and world entry | Packaged refmap, optional KubeJS/Radium hooks, script recipe JSON sync |
| Eight controller pages | Actual menu opening packets; quantum filtering, 64 black holes, Shift transfer, normal insertion/extraction |
| Applied planning | Three order modes, exact long/BigInteger plans, finite inventory, malformed/duplicate patterns, complete native crafting job |
| EAEP smart doubling | Installed enabled/disabled interfaces, private multiplier, preserved wrapper and exact remainder, Omni bypass |
| Well research | Prerequisites, maximum batch, reserved materials, stop/refund, restart/save; real AE dependent-material orders complete with one click |
| Hub | Tags and blacklist, simultaneous resources, fluid generation, 64-hole scaling, 1000 mB copies, FE priority/AE fallback, full-storage no-draw |
| Quantum | Real grid merge/power, conflicting ownership, removal disconnect and release |
| Providers and CPUs | AE2 and AdvancedAE dispatch, exact long outputs, independent Nexus jobs, backpressure, rejection and durable material ownership |
| Structures | Build/dismantle, player/ME priority, protection, recovery, legacy migration, palette/tickers, spawn protection |
| Radium movement | Ring/tower carriage, collision, jumping, resume, obstacle freeze, docking, protected rollback and recovery |
| Sustained workload | 600 idle ticks, then 600 real ticks with research, resource collection, 64-hole generation, item copies and moving bodies together |

Project Infinity 0.1 validation used 366 top-level mod JARs, Forge 47.4.20,
AE2 15.4.10, ExtendedAE 1.4.20, EAEP 1.6.2, AdvancedAE 1.3.6, GuideME 20.1.15
and Radium Re-Reforged 0.14.2. Its KubeJS removes the default Nexus recipe;
the default-recipe assertion is explicitly reported as a pack override while
native CPU behavior is still tested. Separate paired engine regressions check
upstream AE2 15.4.10 and UELM 15.5.4 with the same upstream-built Applied JAR.

`PACK_FLOW_MSPT` records server START-to-END timing and includes sample counts,
mean, p95 and maximum. Manual test actions and full blueprint setup run later in
the END handler and are outside that timer. The sustained test has separate idle
and active phases with real intervening server ticks. Client rendering cost,
world generation and other workloads need their own profiling. This is a defined
regression workload, not proof of every third-party combination or sustained TPS
for an existing player's world.

The release validation passed 36 broad flows plus the separate sustained flow.
Sustained active timing: 600 samples, mean 7.93 ms, p95 21.06 ms, maximum 32.54 ms;
idle timing: 600 samples, mean 4.89 ms, p95 8.11 ms, maximum 84.94 ms. The active
phase collected all 189 configured types and returned 37 copies to AE storage.
These figures use that pack's existing generation and energy settings, not
temporarily increased production values from unit tests.

## 中文

先构建前置和正常模组，再构建验证专用包。启动器复用指定实例的 Java、库和资源，
把模组、配置与脚本复制到 build 下全新的目录，并替换账号参数。不会复制存档、
不会打印账号命令；客户端自行创建测试世界。验证包包含重映射后的真实主类和测试
夹具，不能装进正式整合包；正常发行 JAR 不包含测试代码。

八个真实页面通过后执行游戏流程，必须检查两种全部通过标记和截图。600 tick 空闲
与 600 tick 同时研究、采集、64 黑洞生产、复制和运动用于观察持续运行；单次同步
测试计时与大量搭建夹具引起的卡顿不能代表日常 TPS。原版 AE／UELM 的独立回归分别
运行，实际整合包结果只涵盖记录的版本、配方和负载。
