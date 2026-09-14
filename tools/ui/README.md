# Forge UI regression checks / Forge 界面回归检查

Use Java 17 and the [Forge dependency setup](../../libs/README.md). The full unit
suite runs with `./gradlew test`. For just the 14 responsive-screen and JEI tests:

```powershell
.\gradlew.bat -I tools/ui/ui.init.gradle testForgeUi
```

The configured baseline is JEI 15.49.0.188. To use another available Minecraft
1.20.1 JEI 15 build, add `-Pjei_version=<version>`; ForgeGradle deobfuscates it for
the development classpath. LDLib2 is not required by the native Forge screens.
Reports are written to `build/reports/tests/testForgeUi/`.

Tests cover fitted panel bounds, visible slot hit areas, external controls,
JEI foreground mouse coordinates, exclusion areas, nested rendering and state
restoration after failure. The Forge-specific test retains the container origin
that JEI 15 removes internally and checks its result against the raw click position.
Runtime method signatures are also checked against the configured JEI JAR.

These tests do not launch a client or replace a full in-game visual/Mixin check.
The isolated server tests are documented in [the GameTest guide](../gametest/README.md).

## 中文

使用 Java 17，按 Forge 前置说明准备依赖。`./gradlew test` 运行完整单元测试，
上述任务单独运行 14 项界面与 JEI 检查，默认使用 JEI 15.49.0.188。
可用 `-Pjei_version=<版本>` 指定另一份可获取的 1.20.1 JEI 15 构建，由 ForgeGradle
完成开发环境反混淆；Forge 原生界面不需要 LDLib2。

检查覆盖缩放边界、槽位命中、外部控件、前景坐标、避让区域和绘制状态恢复，
额外验证 JEI 15 自行移除容器原点的行为及实际方法签名。测试不打开游戏，
不等同于客户端 Mixin 加载或实际画面验收。
