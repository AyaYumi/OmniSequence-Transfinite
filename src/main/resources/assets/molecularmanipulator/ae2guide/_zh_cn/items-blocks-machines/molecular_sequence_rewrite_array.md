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

分子构序重写阵列将样板存储与配方执行集中在一个方块中，可为 ME 自动合成执行合成、锻造和切石配方。

<Row>
<BlockImage id="molecularmanipulator:molecular_manipulator" scale="4" />

<ItemImage id="ae2:crafting_pattern" scale="4" />

<ItemImage id="ae2:smithing_table_pattern" scale="4" />

<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

## 设置

1. 在构筑井完成**二阶：构序阵列**研究，制作阵列。
2. 放置后接入有电、有空闲频道的 ME 网络。
3. 插入已编码的合成、锻造或切石样板。
4. 从 ME 终端请求产物，阵列会接收材料并在内部完成加工。

样板库共 **360 槽**，分为 10 页，每页 36 槽。需要外部机器的处理配方，请将处理样板放入样板供应器，并连接相应机器。

## 批量加工

符合条件的配方可在 **1 tick** 内完成一批。批量数量取决于原料、能源与产物接收空间，合成仍按配方消耗材料并返还容器和工具。

构筑井的深度研究加成用于构筑井配方，不会提高本设备的加工速度。

## 输出与搬迁

ME 存储不足时，产物和返还物保存在设备中，等待网络接收。取消任务会退回未用材料，已完成的产物仍保留。

正常拆下阵列时，样板与未完成内容随方块携带。重新放置并恢复网络和供电后可继续。

## 配方

<RecipeFor id="molecularmanipulator:molecular_manipulator" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
