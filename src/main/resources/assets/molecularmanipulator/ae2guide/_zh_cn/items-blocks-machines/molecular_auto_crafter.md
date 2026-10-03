---
navigation:
  parent: omnisequence-index.md
  title: "分子自动合成器"
  icon: molecularmanipulator:molecular_auto_crafter
  position: 1040
item_ids:
- molecularmanipulator:molecular_auto_crafter
---

# 分子自动合成器

按库存目标持续补货的单方块设备。九个样板位置分别设置、分别运行。

<Row>
<BlockImage id="molecularmanipulator:molecular_auto_crafter" scale="4" />

<ItemImage id="ae2:crafting_pattern" scale="4" />

<ItemImage id="ae2:smithing_table_pattern" scale="4" />

<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

## 让库存保持充足

1. 接入有电、有频道的 ME 网络，放入最多 **9 张**合成、锻造或切石样板。
2. 点击样板上方的齿轮，或右键样板，打开该位置的设置。
3. 填写**成品库存上限**和各项**原料保留量**，按 Enter 或失去焦点提交。
4. 启用该位置，机器开始从同一 ME 网络取料并返还产物。

| 设置 | 用途 |
| --- | --- |
| 成品库存上限 | 达到目标后暂停补货 |
| 上限设为 0 | 持续合成，直到原料保留量或其他条件限制 |
| 原料保留量 | 为网络留下指定数量，避免全部用完 |
| 独立启停 | 单独启用或停止每个样板位置 |

## 输出到相邻容器

将输出模式切换为**附近容器**，再打开**输出方向**。
面板的左右格子对应从机器正面看到的左右邻接方块。
点击需要输出的方向，可同时启用多个方向。

## 缺料与缓存

原料不足、缺电、网络断开或输出无法接收时自动等待，条件恢复后继续。原料与产物条目较多时，可用设置页箭头翻页。

此处执行合成、锻造和切石配方。需要外部机器的处理配方，请使用样板供应器。

构序阵列的**自动合成**页使用相同的九槽补货方式，设置方法一致。

## 配方

<RecipeFor id="molecularmanipulator:molecular_auto_crafter" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
