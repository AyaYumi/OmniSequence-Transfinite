---
navigation:
  parent: omnisequence-index.md
  title: "装配矩阵构序重写核心"
  icon: molecularmanipulator:assembler_matrix_molecular_core
  position: 1010
item_ids:
- molecularmanipulator:assembler_matrix_molecular_core
---

# 装配矩阵构序重写核心

安装在 ExtendedAE 装配矩阵内的加工核心，为矩阵提供大批量配方执行能力。

<Row>
<BlockImage id="molecularmanipulator:assembler_matrix_molecular_core" scale="4" />

<BlockImage id="molecularmanipulator:molecular_manipulator" scale="4" />
</Row>

## 装进矩阵

1. 在构筑井完成**二阶：构序阵列**研究并制作核心。
2. 按 ExtendedAE 的结构规则搭建有效装配矩阵。
3. 把核心安装到矩阵功能核心位置，成型并接通 ME 网络。
4. 向矩阵提供兼容分子装配室的样板，从 ME 终端发起合成。

| 准备条件 | 说明 |
| --- | --- |
| 完整装配矩阵 | 提供结构、样板与网络连接 |
| 独立放置 | 不能单独工作 |
| 批量能力 | 逻辑上限约 9.22E 次，受实际材料与输出限制 |

## 工具与容器返还

核心按真实配方执行，保留容器、可复用物品与工具状态。适合批量的配方合并执行；需要随机耐久或特殊上下文的配方按 AE2 常规路径逐份处理。

<ItemGrid>
<ItemIcon id="minecraft:bucket" />
<ItemIcon id="minecraft:iron_pickaxe" />
<ItemIcon id="ae2:crafting_pattern" />
</ItemGrid>

| 情况 | 处理方式 |
| --- | --- |
| 产物暂时无法写入 ME | 保存在输出缓存，等待接收 |
| 取消合成 | 停止未执行部分，退回未用材料及当前工具 |
| 保存或重载 | 保留任务、材料和执行进度 |
| 正常拆下核心 | 内容随核心携带，装回有效矩阵后继续 |

取消前已完成的产物仍然保留。合成能耗按 AE2 样板规则计算。

## 配方

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
