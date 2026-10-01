# UI regression checks / 界面回归检查

AE2 19.2.17/19.2.18 paired-mod CPU list rendering and server compatibility checks
are documented in [AE2 compatibility](../../docs/ae2-19.2.18-compatibility.zh-CN.md).
Use `ae2-compat.init.gradle` to run the real client capture in an isolated world.

Run `./gradlew test` for the standard unit suite. To check the responsive screen
and JEI integration with the dependency versions from a particular modpack, supply
its JEI and LDLib2 JARs explicitly:

```powershell
.\gradlew.bat --no-configuration-cache -I tools/ui/ui.init.gradle '-PjeiJar=D:/mods/jei.jar' '-PldlibJar=D:/mods/ldlib2.jar' testPackUi
```

Use Java 21 and prepare the separate [AppliedEnhancements dependency](../../libs/README.md).
The paths above are examples; no personal modpack path is built into the task.
Reports are written to `build/reports/tests/testPackUi/`.

The 12 focused tests cover fitted panel bounds, visible slot hit areas, injected
controls, raw mouse coordinates for JEI rendering, exclusion areas and render-state
restoration after success or failure. The foreground regression recreates the case
where JEI clears the container pose but receives logical mouse coordinates: the
visible hover area must agree with the click position.

Version 2.0.3 was checked with JEI 19.54.0.429 and LDLib2 2.2.39.a.
These tests execute the coordinate and wrapper code without opening Minecraft.
They do not validate Mixin application during a full client launch or replace an
in-game visual check.

## 中文

普通单元测试运行 `./gradlew test`。上面的独立任务通过 `jeiJar` 和 `ldlibJar`
指定整合包实际使用的前置，运行 12 项界面专项测试；示例路径需替换为自己的文件。
需要 Java 21 和独立的 AppliedEnhancements 前置，任务不读取玩家存档。

检查覆盖面板缩放、槽位命中、外部控件输入、JEI 原始鼠标坐标、避让区域以及异常后的
绘制状态恢复，重点验证“点击正确，但高亮和提示框偏移”的回归场景。
2.0.3 已使用 JEI 19.54.0.429 和 LDLib2 2.2.39.a 通过这些测试。

这些是代码和坐标验证，不启动游戏，也不等同于客户端 Mixin 加载或游戏内画面验收。

## 太虚造化天枢真实客户端检查

`taixu-client.init.gradle` 会启动真实客户端，在 `build/taixu-client-run/` 中验证
总览与材料界面、两档 GUI 缩放、JEI、世界投影、成型建筑、实体运动和玩家实际乘载。
它仅对测试运行补充 JEI 实现依赖，测试类不会打入发行 JAR。

先运行 `tools/gametest/taixu.init.gradle` 的 `runGameTestServer`，将生成的
`build/taixu-regression-run/world` 复制到 `build/taixu-client-run/saves/` 下一个新目录，
再把该目录名传给 `taixuUiWorld`。每轮使用新副本，避免上次运动中的建筑影响初始状态。

```powershell
.\gradlew.bat --no-configuration-cache -I tools/ui/taixu-client.init.gradle -PtaixuUiWorld=TaixuUIFresh runClient
```

截图写入 `build/taixu-palette/`，完成后自动退出；不读取或修改个人游戏存档。

### 渲染模组兼容与距离裁剪回归

支持通过 `taixuRenderMods` 指定整合包的 `mods` 目录，复制以下六个发行 JAR 到隔离运行目录：
Sodium、Iris、EntityCulling、ImmediatelyFast、AcceleratedRendering 和 Sodium Extras。
保留发行 JAR 的嵌套依赖结构，不通过 `localRuntime files(...)` 拆分装载。

```powershell
.\gradlew.bat --no-configuration-cache -I tools/ui/taixu-client.init.gradle `
  '-PtaixuRenderMods=D:/modpack/mods' `
  -PtaixuUiDirectory=build/taixu-compat-run `
  -PtaixuUiOutput=build/taixu-compat-captures `
  -PtaixuUiWorld=TaixuCompatFresh runClient
```

运行前将新世界副本放到指定运行目录的 `saves/TaixuCompatFresh`，并按需复制渲染模组配置。
Sodium Extras 的实体与方块实体距离裁剪应保持启用，水平阈值 4096、纵向阈值 32，
以覆盖“启动运动后消失”的回归条件。启动代码自动追加天枢的精确白名单；日志必须出现
`TAIXU_DISTANCE_CULLING_PASS drawableBodies=11`，只断言实体存在不足以识别这个问题。

完整检查还要求 `TAIXU_LIVE_RIDER_PASS` 和 `TAIXU_CLIENT_UI_PASS`，并保存真实昼夜截图。
夜景修改的是 day time，不能修改 game time，否则会影响按游戏 tick 计算的运动相位。
可把光影包复制进隔离目录的 `shaderpacks` 并修改该目录的 `config/iris.properties` 再运行，
不会改变个人整合包的光影设置。测试类始终不进入发行 JAR。

### 运动组件闪烁与光影切换

将已完成上述检查、仍在运动的测试世界复制为新世界，在同一隔离运行目录运行：

```powershell
.\gradlew.bat --no-configuration-cache -I tools/ui/taixu-client.init.gradle `
  '-PtaixuRenderMods=D:/modpack/mods' -PtaixuUiDirectory=build/taixu-compat-run `
  -PtaixuUiOutput=build/taixu-flicker-captures -PtaixuUiWorld=TaixuFlickerFresh `
  -PtaixuFlickerProbe=true runClient
python tools/ui/verify_taixu_flicker.py build/taixu-flicker-captures build/taixu-flicker.log
```

把客户端输出保存为最后一行指定的日志。此检查固定在近距离视角，依次开启、关闭、
重新开启光影，各保存 16 帧；验证脚本检查 48 帧里的实际塔柱纹理像素，避免仅凭实体
数量或截图生成成功判定渲染正常。像素阈值针对该测试视角和 Complementary Reimagined
r5.8.1，不能作为其他视角、分辨率或光影包的通用判定。

检查每个 tick 都在实体更新后调用实际的位置插值入口 `lerpTo`，模拟位置同步包在
绘制前到达，稳定复现“包围盒暂时缩成 1x1，下一 tick 才恢复”的旧问题。新版实体
在所有位置更新路径中立即根据真实组件重建包围盒。帧时间在截图前采样，避免 PNG
写入影响结果；原有静态 GPU 网格与运动动画保持不变。

### 太虚天仪特效预览

`-PtaixuTianyiProbe=true` 在复制的运动世界中重置测试建筑的运动进度，预热区块后采集
33 帧启动演出，并拍摄昼夜全景、晶体近景、暂停、恢复及光影关闭 / 重载后的画面。
测试仅修改指定的隔离世界。该模式与 `taixuFlickerProbe` 分开运行。

工作区有其他功能正在开发时，可用 `taixu-effects.init.gradle` 对已安装 JAR 编译预览补丁：

```powershell
.\gradlew.bat --no-configuration-cache -I tools/ui/taixu-effects.init.gradle `
  '-PtaixuBaselineJar=build/taixu-tianyi-baseline.jar' test assembleTaixuEffectsPatch
.\gradlew.bat --no-configuration-cache -I tools/ui/taixu-effects.init.gradle `
  -I tools/ui/taixu-client.init.gradle '-PtaixuBaselineJar=build/taixu-tianyi-baseline.jar' `
  '-PtaixuUiDirectory=build/taixu-compat-run' '-PtaixuUiWorld=TaixuTianyiPreview' `
  '-PtaixuUiOutput=build/taixu-tianyi-final' -PtaixuTianyiProbe=true runClient
python tools/ui/verify_taixu_tianyi.py build/taixu-tianyi-final build/taixu-tianyi-final.log `
  --baseline build/taixu-tianyi-baseline.jar `
  --patch build/libs/omnisequence-transfinite-2.0.7-taixu-tianyi-preview.jar
```

验证脚本检查实际场景区域、晶体颜色像素及发行包变更范围，并生成启动动图。
补丁只更新太虚的六个类条目，其他条目内容与基线逐一核对一致；不能省略基线校验。
