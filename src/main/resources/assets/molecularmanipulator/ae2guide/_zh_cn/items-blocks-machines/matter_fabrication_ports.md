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

输入输出口用于向[物质构筑井](matter_fabrication_well.md)投料和取出产物。四种接口均无需研究，安装时需要使用构筑井的服务位置。

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_item_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_item_output" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_output" scale="4" />
</Row>

## 接口类型

| 接口 | 用途 | 容量 |
| --- | --- | --- |
| 物品输入口 | 接收物品原料 | 16 槽 |
| 物品输出口 | 存放物品产物 | 16 槽 |
| 流体输入口 | 接收流体原料 | 4 个流体罐 |
| 流体输出口 | 存放流体产物 | 4 个流体罐 |

每个流体罐容量为 **2,147,483,647 mB**。手持接口可查看安装位置；完整结构中的控制器会使用各输入口的材料执行已解锁配方。

## 管道与自动输出

将物品管道接到物品输入口，将流体管道接到流体输入口。为配方的每种产物安装对应输出口并预留空间，再使用管道抽取产物。

输出口也可以主动向相邻容器传送：开启**自动输出**，并选择至少一个方向。六个方向按世界方位计算，可以同时选择多个；悬停方向按钮可查看相邻方块。自动输出默认关闭。

## 手动转移流体

用鼠标拿起装液容器，**右键流体槽**即可注入；拿起空容器再右键即可取出。每次处理一个容器，槽中必须有足够的流体或接收空间。

## 退回材料

输入口的**退回 AE**按钮会把未用材料送回控制器连接的 ME 网络。网络放不下的部分留在接口中。正常拆下接口时，其缓存随方块保存。

加工不启动时，先检查配方是否已解锁、输入是否齐全，以及输出口是否能接收全部产物。研究材料需要放入 ME 库存，输入口不提供研究材料。

## 配方

<RecipeFor id="molecularmanipulator:matter_fabrication_item_input" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

<RecipeFor id="molecularmanipulator:matter_fabrication_item_output" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_input" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_output" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
