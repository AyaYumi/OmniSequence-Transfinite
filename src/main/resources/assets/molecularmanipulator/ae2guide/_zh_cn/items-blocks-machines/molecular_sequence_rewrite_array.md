---
navigation:
  parent: omnisequence-index.md
  title: 分子构序重写阵列
  icon: molecularmanipulator:molecular_manipulator
  position: 1000
item_ids:
- molecularmanipulator:molecular_manipulator
---

# 分子构序重写阵列

<BlockImage id="molecularmanipulator:molecular_manipulator" scale="8" />

分子构序重写阵列是一台高吞吐量[自动合成](ae2:ae2-mechanics/autocrafting.md)设备。它像
<ItemLink id="ae2:pattern_provider" />一样保存已编码样板，但会在方块内部执行所有分子装配室兼容配方，
无需把原料发送给相邻机器。

## 使用方法

先在[物质构筑井](matter_fabrication_well.md)完成“二阶：构序阵列”的首次研究，再用构筑井制作本机器。
研究提升的是本机器制作配方在构筑井中的加工能力，不改变放置后的阵列性能。

1. 将阵列接入有可用频道且已经供电的 [ME 网络](ae2:ae2-mechanics/me-network-connections.md)。
2. 右键打开界面，放入已编码的合成、锻造或切石样板。
3. 使用翻页按钮管理固定样板库存：共 10 页，每页 36 槽，总计 360 个样板槽。
4. 像平常一样在 ME 终端中发起对应产物的自动合成。

阵列不能在内部执行处理样板。此类配方仍需使用普通样板供应器和外部机器。

## 配方处理与输出

阵列使用虚拟高并行处理，受支持的配方最快可在 1 Tick 内完成。实际吞吐量仍取决于原料、ME 能量，
以及网络是否能接收产物。

配方产物、中间产物和容器返还会按 AE Key 聚合并送回 ME 网络。网络暂时无法接收时，阵列会把它们保存在
持久化输出缓冲中并继续重试；世界重新加载后缓冲也不会丢失。

正常拆下阵列时，样板、库存和待返还内容会随掉落方块保留。重新放置并恢复 ME 连接后继续处理。

## 配方

<RecipeFor id="molecularmanipulator:molecular_manipulator" fallbackText="当前整合包未提供此配方，请查看 JEI 或研究配置。" />
