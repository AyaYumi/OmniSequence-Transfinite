---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: 构筑井输入输出口
  icon: molecularmanipulator:matter_fabrication_item_input
  position: 1
item_ids:
- molecularmanipulator:matter_fabrication_item_input
- molecularmanipulator:matter_fabrication_item_output
- molecularmanipulator:matter_fabrication_fluid_input
- molecularmanipulator:matter_fabrication_fluid_output
---

# 构筑井输入输出口

<BlockImage id="molecularmanipulator:matter_fabrication_item_input" scale="5" />

四种接口分别负责物品输入、物品输出、流体输入和流体输出。它们使用 AE2 原版材料制作，不需要研究。
手持接口时会高亮附近[物质构筑井](matter_fabrication_well.md)允许安装的位置；安装后由控制器识别。

## 缓存与管道

物品口具有 16 个普通物品槽；流体口具有 4 个独立流体罐，每罐容量为 2,147,483,647 mB。
输入口向外部管道开放放入能力，输出口开放取出能力。普通接口缓存与[样板总成](matter_fabrication_pattern_assembly.md)的不限类型缓存不同。

物品可通过界面、管道等送入对应接口。普通加工消耗输入口材料，产物交给对应输出口。
请同时为配方的物品和流体产物准备空间。

## 退回 AE 与自动输出

控制器网络在线时，输入口的“退回 AE”按钮可把缓存材料送回控制器网络。网络放不下的部分仍留在缓存中。

输出口提供“自动输出”开关，默认关闭，六个输出方向也默认未选。
开启后至少选择一个方向；可以同时选择多个方向，把物品或流体送入相邻的兼容容器。
方向按钮显示相邻方块图标，悬停可看方块名称。上、下、北、南、西、东使用世界方向，不随控制器朝向改变。

## 用桶手动操作流体

用鼠标拿起装有流体的桶或兼容容器，在目标流体缓存槽上右键，即可把容器中的流体放进去。
拿着空桶或空容器右键缓存槽，可把流体装出来。一次处理一个容器；流体不匹配、数量不足或没有空间时不会强行转移。
输入流体口和输出流体口都支持这种手动操作，堆叠容器的结果需要背包有接收空间。

正常拆下接口后，缓存中的物品或流体随掉落方块保存。重新放回合法接口位置后继续使用。

## 配方

<RecipeFor id="molecularmanipulator:matter_fabrication_item_input" fallbackText="当前整合包未提供此配方。" />

<RecipeFor id="molecularmanipulator:matter_fabrication_item_output" fallbackText="当前整合包未提供此配方。" />

<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_input" fallbackText="当前整合包未提供此配方。" />

<RecipeFor id="molecularmanipulator:matter_fabrication_fluid_output" fallbackText="当前整合包未提供此配方。" />
