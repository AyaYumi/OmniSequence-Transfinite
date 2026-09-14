# UI regression checks / 界面回归检查

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
