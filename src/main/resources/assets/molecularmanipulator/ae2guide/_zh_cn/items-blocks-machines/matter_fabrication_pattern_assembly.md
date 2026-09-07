---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: 物质构筑井样板总成
  icon: molecularmanipulator:matter_fabrication_pattern_assembly
  position: 2
item_ids:
- molecularmanipulator:matter_fabrication_pattern_assembly
---

# 物质构筑井样板总成

<BlockImage id="molecularmanipulator:matter_fabrication_pattern_assembly" scale="8" />

样板总成负责把 AE 自动合成任务交给[物质构筑井](matter_fabrication_well.md)执行。
完成[一阶研究](matter_fabrication_research.md)首次解锁后，可在构筑井中制作它。

## 安装与样板

1. 手持样板总成查看允许安装的位置，将它放入构筑井的合法接口位置。
2. 控制器结构完整并接入 AE 网络后，总成自动连接到控制器网络；仍需满足 AE 的供电与频道条件。
3. 总成有 36 个样板槽，放入与构筑井加工配方一致的 AE2 处理样板，包含准确的物品、流体输入和输出。
4. 对应研究解锁后，从 ME 终端请求产物。编码的合成、锻造和切石样板不用于此总成。

总成可命名，在 AE 样板终端中用于区分不同总成。新增或移除样板后会更新可用合成项目。

## 输入与输出缓存

界面分为“样板”“输入缓存”“输出缓存”三页。物品与流体缓存没有固定类型槽位限制，每种 AE Key 的数量上限为
9,223,372,036,854,775,807（约 9.22E）。不同数据的物品或流体按不同键计数；实际类型数量仍受可用内存约束。

AE 投入的材料由总成保存并排队，加工完成后产物自动推送到 AE 网络。网络暂不接收时保留在输出缓存中并继续重试。
可以点击“退回待加工原料”，退还尚未开始加工的批次材料；正在加工的批次不会因此被当作完成或丢弃。

任务、样板、输入、输出和待退款会随存档保存，正常拆下总成时也随掉落方块保留。
拆装后恢复结构与网络连接即可继续；单独拆下控制器并不会把其他总成的内容搬进控制器。

## 并行与研究

总成使用控制器的研究权限与同分支加工加成。容量上限不等于一次必定处理这么多份，实际批次仍受研究并行、输入数量、
单种输出上限和供电限制。深度研究只提高其开放配方在构筑井中的生产能力。

## 配方

<RecipeFor id="molecularmanipulator:matter_fabrication_pattern_assembly" fallbackText="当前整合包未提供此配方，请查看 JEI 或研究配置。" />
