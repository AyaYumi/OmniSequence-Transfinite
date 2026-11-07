---
navigation:
  parent: omnisequence-index.md
  title: "超限算枢"
  icon: molecularmanipulator:transfinite_compute_nexus
  position: 1025
item_ids:
- molecularmanipulator:transfinite_compute_nexus
---

# 超限算枢

超限算枢将万物演算核心的合成 CPU 功能集中在一个方块中，高阶星算引擎增益可开启多个独立 ME 合成请求。

<Row>
<BlockImage id="molecularmanipulator:transfinite_compute_nexus" scale="4" />

<ItemImage id="ae2:fluix_glass_cable" scale="4" />

<ItemImage id="ae2:crafting_terminal" scale="4" />
</Row>

## 连接网络

1. 在构筑井完成**二阶：万物演算**研究并制作算枢。
2. 放置算枢，六个面均可连接 ME 线缆。
3. 提供 **1 个频道**和足够的 ME 电力。默认基础耗电为 **16,384 AE/t**。
4. 从 ME 终端发起合成，在 AE2 合成状态界面查看任务。

算枢需要连接线缆或其他外部 ME 设备。仅将多个算枢彼此相邻放置，无法使其工作。

## 使用

算枢遵循[万物演算核心](omni_computation_core.md)的存储与并行上限：没有星算引擎增益时为 **1 MiB、并行 16**；L7 开启虚拟 CPU 通道，只有持续付费的 L9 超频提供无限存储与并行。自适应投料、支持的工具续用与单份尾单处理也使用相同规则。配方仍由网络中的样板供应器和加工机器执行，实际速度取决于材料、供电与产物接收能力。

## 搬迁

正常拆下算枢会保留可恢复的任务和内容。重新放置并接回在线 ME 网络后可继续。

算枢不会强加载区块。需要持续合成时，请保持所在区块加载。

## 配方

<RecipeFor id="molecularmanipulator:transfinite_compute_nexus" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
