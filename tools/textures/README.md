# 方块贴图维护（2.0.3）

`static/` 保存这批动画的原始静态贴图；模组资源目录保存生成的 PNG 帧条及 `.png.mcmeta`。
修改动画时以原图重新生成，避免在上一次输出上叠加处理。

```powershell
python tools/textures/generate_animation.py
```

需要 Pillow。生成 20 张动画贴图，每张 24 帧、每帧 2 tick、启用帧间插值，周期 2.4 秒。
`build/texture-animation-v1/` 输出蓝紫系列与物质构筑井的 GIF 预览。

- 蓝紫系列：数据纠缠节点、无限并行矩阵、全知演算矩阵、两种晶体、万物演算核心的亮起正面及框架。
- 物质构筑井：线圈保留横向流动；稳定器使用轻呼吸，控制器和样板总成使用分段响应，接口使用间歇脉冲，减少重复的环形扫描。
- 所有帧保留原始透明度与非能量区域；构筑井的发光材质位置在所有帧中一致，继续使用局部自发光。
- 这是循环外观动画，不表示实际物品或流体吞吐。万物演算核心沿用原有 powered 模型切换。
- 原生图集动画不受模组的大型动态特效等级开关控制。

超限算枢的正式贴图直接维护在 `src/main/resources/assets/molecularmanipulator/textures/block/`
下的 `transfinite_compute_nexus.png`、`transfinite_compute_nexus_light.png` 和 `nexus_formed/`。
激活模型由 `NexusFormedGeometry` / `NexusFormedBakedModel` 组合绘制；这些资源参与运行，
不是临时预览。正式贴图不依赖个人 Downloads 路径或一次性复制脚本。

`static/` 中的 20 张静态原图用于重新生成动画，不能按“未被游戏引用”当作废弃资源删除。
模组 JAR 只包含正式资源；生成预览位于已忽略的 `build/` 中。

GIF 展示平面贴图帧，不等同于游戏内立体模型、连接纹理和光照效果。

Forge 1.20.1 使用 Java 17，可运行实际贴图和方块面的回归检查：

```powershell
.\gradlew.bat test --tests '*MatterGoldMaskTest' --tests '*MatterEmissiveQuadTest'
```

检查使用实际 PNG、`.mcmeta`、1.20.1 `TextureAtlasSprite` 和 `BakedQuad`，验证金／蓝／紫区域
生成全亮面、普通区域保持光照，以及裁切、镜像和连接面后的纹理坐标。测试不打开游戏或 OpenGL 窗口。

1.20.1 的 `getU/getV/getUOffset/getVOffset` 使用 0～16 纹理坐标；发光分割使用 0～1，
读写时必须换算。颜色掩码正确不等于实际方块面已经生成了发光区域。
