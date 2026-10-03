---
navigation:
  parent: omnisequence-index.md
  title: "分子构序重写阵列"
  icon: molecularmanipulator:molecular_manipulator
  position: 1000
item_ids:
- molecularmanipulator:molecular_manipulator
---

# 分子构序重写阵列

在方块内部执行合成、锻造和切石样板，为 ME 自动合成提供高吞吐量加工。

<Row>
<BlockImage id="molecularmanipulator:molecular_manipulator" scale="4" />

<ItemImage id="ae2:crafting_pattern" scale="4" />

<ItemImage id="ae2:smithing_table_pattern" scale="4" />

<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

## 接入合成网络

1. 在构筑井完成**二阶：构序阵列**研究并制作本方块。
2. 放置阵列，接入有电、有空闲频道的 ME 网络。
3. 放入已编码的合成、锻造或切石样板，用翻页按钮管理样板库。
4. 在 ME 终端请求产物，阵列自动收料并执行配方。

| 能力 | 说明 |
| --- | --- |
| 样板库 | 10 页，每页 36 槽，共 360 槽 |
| 内部执行 | 合成、锻造、切石 |
| 外部加工 | 使用处理样板、样板供应器与对应机器 |
| 解锁来源 | 构筑井的构序阵列研究分支 |

## 大批量合成

逻辑批量上限约 **9.22E 次**。符合条件的配方可在 1 tick 内完成一批，实际份数由材料、能源和 ME 接收空间决定。

高并行不会省略原料、工具返还或合成能耗。构筑井深度研究的速度加成只影响构筑井配方。

## 网络满了怎么办

产物与返还物先保存，再尝试送回 ME。输出空间不足时保留并重试；正常拆下方块时，样板和未完成内容随方块携带。

## 配方

<RecipeFor id="molecularmanipulator:molecular_manipulator" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
