# 方块贴图动画第一版

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

GIF 展示平面贴图帧，不等同于游戏内立体模型、连接纹理和光照效果。

Windows 下可用 Java 21 运行隔离 GPU 检查：

```powershell
.\gradlew.bat --no-configuration-cache -I tools/textures/verify.init.gradle verifyTextureAnimations
```

检查使用隐藏的 OpenGL 窗口、Minecraft 原生贴图播放器及实际 `.mcmeta`，逐像素核对完整循环的上传与插值结果，
并检查动画方块面上的自发光分区。测试源和临时原生依赖不会打入模组 JAR。
