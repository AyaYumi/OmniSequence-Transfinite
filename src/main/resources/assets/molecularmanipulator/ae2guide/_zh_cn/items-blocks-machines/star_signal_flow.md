---
navigation:
  parent: omnisequence-index.md
  title: "星讯存储"
  icon: molecularmanipulator:star_signal_storage_cell
  position: 1220
item_ids:
- molecularmanipulator:star_signal_storage_component
- molecularmanipulator:star_signal_storage_cell
- molecularmanipulator:creative_star_signal_disk
---

# 星讯存储

**星讯流**是星算系列使用的一种 AE 资源，不是普通物品或流体。它按 **bit** 计量，可以像其他 AE 资源一样存进 ME 网络、写进样板和自动合成。

<Row>
<ItemImage id="molecularmanipulator:star_signal_storage_component" scale="6" />
<ItemImage id="molecularmanipulator:star_signal_storage_cell" scale="6" />
<ItemImage id="molecularmanipulator:creative_star_signal_disk" scale="6" />
</Row>

## 星讯存储组件

**星讯存储组件**是合成存储元件的材料。工作台配方使用 **4 颗星髓晶体、4 份红石粉和 1 个星图处理器**；中央处理器负责寻址，四颗水晶构成数据芯。

<RecipeFor id="molecularmanipulator:star_signal_storage_component" />

## 星讯存储元件

将 **星讯存储组件**与 AE2 的 **物品单元外壳**无序合成，即可得到星讯存储元件。它可插入 ME 驱动或 ME 箱子，提供 **1 种类型、256 MiB（268,435,456 字节）**容量。

<RecipeFor id="molecularmanipulator:star_signal_storage_cell" />

## 创造星讯磁盘

**创造星讯磁盘**内含无限星讯流，仅创造模式获取。插入 ME 驱动后，终端会把它显示为约 **2.1G** 的库存，实际按无限供应处理。

## 终端看不到星讯流？

AE2 终端会记住“配置可见类型”。如果终端是在星讯流类型注册之前创建的，它可能默认隐藏星讯流。

1. 打开终端左侧的**配置可见类型**按钮（漏斗图标）。
2. 勾选 **星讯流**。
3. 库存中就会出现星讯流；普通存储元件和创造磁盘都会显示。
