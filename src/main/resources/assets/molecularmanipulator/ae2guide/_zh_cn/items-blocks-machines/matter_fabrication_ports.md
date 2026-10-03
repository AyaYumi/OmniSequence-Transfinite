---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: "构筑井输入输出口"
  icon: molecularmanipulator:matter_fabrication_item_input
  position: 1
item_ids:
- molecularmanipulator:matter_fabrication_item_input
- molecularmanipulator:matter_fabrication_item_output
- molecularmanipulator:matter_fabrication_fluid_input
- molecularmanipulator:matter_fabrication_fluid_output
---

# 构筑井输入输出口

把材料送进构筑井，再把产物交给管道或容器。四种接口均用基础 AE2 材料制作，无需研究。

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_item_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_item_output" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_output" scale="4" />
</Row>

## 选对接口

| 接口 | 管道用途 | 缓存 |
| --- | --- | --- |
| 物品输入口 | 放入材料 | 16 槽 |
| 物品输出口 | 取走产物 | 16 槽 |
| 流体输入口 | 注入原料 | 4 罐 |
| 流体输出口 | 抽取产物 | 4 罐 |

每个流体罐可存 **2,147,483,647 mB**。手持接口查看合法位置；成型后由[控制器](matter_fabrication_well.md)统一处理配方。

## 投料与输出

1. 将配方要求的物品、流体送入对应输入口。
2. 给每种产物准备对应输出口与剩余空间。
3. 用管道抽取输出；或在输出口开启**自动输出**并选择相邻容器方向。

| 自动输出设置 | 行为 |
| --- | --- |
| 初始状态 | 开关关闭，六个方向均未选 |
| 启用条件 | 开启开关，并至少选择一个方向 |
| 方向选择 | 上、下、北、南、西、东，可同时选多个 |

方向按世界方位计算。悬停方向按钮可查看相邻方块名称。

## 手动搬运流体

<ItemGrid>
<ItemIcon id="minecraft:bucket" />
<ItemIcon id="minecraft:water_bucket" />
</ItemGrid>

鼠标拿着装液容器，**右键流体槽**注入；拿着空容器，右键取出。每次处理一个容器，数量不足、流体不匹配或空间不够时停止转移。

## 退回与拆装

输入口的**退回 AE**把缓存送回控制器网络，放不下的部分留在接口中。正常拆下接口后，缓存随方块保留。

## 配方

<Row>
<RecipeFor id="molecularmanipulator:matter_fabrication_item_input" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

<RecipeFor id="molecularmanipulator:matter_fabrication_item_output" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
</Row>

<Row>
<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_input" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_output" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
</Row>
