# 成型引擎底纹回归基线

2026-10-25，加入用户确认的“同源熵纹底板”前保存：

- `controller_formed_{base,quantum,terminal}.png`：两像素边框、已延伸的管线和金属护边，无底纹。
- `controller_formed_{tier}_light.png`：原有 96 帧硬件光层，无底纹光。
- `concept_{tier}_front_48px.png`：用户已确认的原生 48×48 设计图，左接缝 mask=1、动画第 47 帧，底纹强度 0.52。它是纹理合成预览，不是游戏截图。

用户随后指出 0.52 倍亮度与旁边熵锚不一致，现要求直接复制锚的原始 RGBA，静态与发光层均不得衰减。历史 concept 图不覆盖，仅作为已确认的纹样布局依据。

`NegativeEntropyEnginePanelPatternAssetsTest` 使用这些固定基线独立计算全部 16 个连接掩码、三阶底纹及 96 帧发光，逐像素检查新图使用锚的原始颜色/alpha；第 47 帧另验证布局与历史 concept 一致但亮度按新要求为 1:1。不得从当前成品重新复制覆盖基线来让失败测试通过。此目录仅属于 test source set，不进入发布 JAR。
