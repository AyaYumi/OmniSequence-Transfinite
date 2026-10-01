# 太虚运动部件闪烁修复

环与塔柱的位置同步调用 `Entity.lerpTo`，随后 `setPos` 会通过 `makeBoundingBox`
重建包围盒。旧实体只在 tick 和同步数据更新时恢复组件包围盒，位置包在两个 tick
之间到达时，会暂时采用注册尺寸 1×1。11 个部件共享建筑原点，因而会同时被误裁剪；
方块实体光效继续显示，形成“结构忽隐忽现、光效还在”的现象。

`TaixuAssemblyEntity.makeBoundingBox` 现在在配置完成后直接计算完整组件的变换包围盒，
覆盖所有位置更新路径；构造期间同步数据尚未初始化时使用普通实体的占位包围盒。
tick 更新复用同一实现。静态 GPU 网格、材质、运动与存档协议沿用原实现。

验证使用隔离世界和整合包中的 Sodium、Iris、EntityCulling、ImmediatelyFast、
AcceleratedRendering、Sodium Extras，以及 Complementary Reimagined r5.8.1。
客户端每 tick 在实体更新后主动调用 `lerpTo`，依次开启、关闭、重新开启光影，
每段采集 16 帧。旧版纹理像素检查失败并重现实体消失；新版全部 48 帧通过，
最低塔柱纹理边缘比例为 0.226，检查阈值为 0.12。截图前采样的平均帧时间
分别为 16.76、16.82、16.70 ms，测试客户端上限为 60 FPS。

4 项服务端 GameTest 通过。运动测试新增全部 11 个组件在初始及运动相位下的
`setPos`、`lerpTo` 包围盒一致性断言，并保留玩家乘载、碰撞、归位及材料所有权检查。

本次交付以整合包原有 `omnisequence-transfinite-2.0.6-hotfix.jar` 为基线，仅替换
`com/atir/molecularmanipulator/entity/TaixuAssemblyEntity.class`。构建时工作区另有
尚未完成的合成接口改动，缺少 `AelisBatchExecutionContext` 前置 API，整项目编译
因此受阻。本修复采用 Java 21、项目实际编译依赖及基线 JAR 单独编译，测试运行也
使用基线代码加该修复；发行文件其余条目的内容逐一校验一致，测试类不进入发行包。
