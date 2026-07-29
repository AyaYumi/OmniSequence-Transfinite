---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: 装配矩阵构序重写核心
  icon: molecularmanipulator:assembler_matrix_molecular_core
  position: 1010
categories:
- machines
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

## 配方

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" />
