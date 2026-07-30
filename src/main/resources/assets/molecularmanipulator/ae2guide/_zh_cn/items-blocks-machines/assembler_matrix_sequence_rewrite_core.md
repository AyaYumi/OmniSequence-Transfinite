---
navigation:
  parent: omnisequence-index.md
  title: 装配矩阵构序重写核心
  icon: molecularmanipulator:assembler_matrix_molecular_core
  position: 1010
item_ids:
- molecularmanipulator:assembler_matrix_molecular_core
---

# 装配矩阵构序重写核心

<BlockImage id="molecularmanipulator:assembler_matrix_molecular_core" scale="8" />

装配矩阵构序重写核心是 ExtendedAE 装配矩阵的升级组件。它以高吞吐量内部配方执行器的形式，
取代普通合成核心与速度核心。

## 使用方法

搭建有效的 ExtendedAE 装配矩阵，并将本方块作为功能核心装入结构。它不能脱离装配矩阵单独工作：
样板、网络连接和结构校验均由完整装配矩阵提供。

矩阵成型并联机后，矩阵中的分子装配室兼容样板会自动分配给本核心执行。

## 配方处理与兼容性

核心会调用真实配方装配逻辑，而不是简单倍增产物堆叠，因此能够正确保留：

- 空容器等配方返还；
- 工具耐久及其他带耐久原料；
- 配方需要但不会消耗的输入；
- 产生多种 AE Key 的配方结果。

产物会按 AE Key 聚合后批量返回 ME 网络。输出受阻时，持久化缓冲会安全保存产物，直到网络能够接收。

## 可复用输入与取消

核心可将同键返还物品作为一个可复用批次执行，其中也包括物品数据判定为不可损坏的物品。
有限耐久工具仅在每次合成都确定增加恰好 1 点损伤时才会批量执行；带耐久附魔、随机变化或依赖上下文
的工具会回退 AE2 原生逐份路径。水桶变为空桶等换键返还也继续逐份执行。

核心接受可复用批次后会持有并持久化完整执行状态，保存、区块卸载或服务器重启不会丢失或重复剩余工作。
取消 AE2 合成任务会写入持久取消标记、停止所有尚未执行的合成，并精确退回未使用材料以及可复用物品
的当前状态；取消前已经完成的产物仍然有效。

批量执行会对实际合并后的输入调用 AE2 原生样板能耗计算，保持 AE2 原版合成能耗行为。

## 配方

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" />
