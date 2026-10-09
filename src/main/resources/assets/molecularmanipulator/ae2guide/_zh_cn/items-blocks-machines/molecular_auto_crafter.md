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

分子自动合成器从 ME 库存取料，在内部执行配方并返还产物，适合持续制作常用材料。九个样板位置各有独立的库存目标、原料保留量和启停设置。

<Row>
<BlockImage id="molecularmanipulator:molecular_auto_crafter" scale="4" />

<ItemImage id="ae2:crafting_pattern" scale="4" />

<ItemImage id="ae2:smithing_table_pattern" scale="4" />

<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

## 设置自动补货

1. 接入有电、有频道的 ME 网络。
2. 放入最多 **9 张**合成、锻造或切石样板。
3. 点击样板上方的齿轮，或右键样板，打开该位置的设置。
4. 填写**成品库存上限**和各项**原料保留量**，按 Enter 或离开输入框保存。
5. 启用该位置，设备开始自动补货。

| 设置 | 效果 |
| --- | --- |
| 成品库存上限 | 库存达到目标时暂停，低于目标时补货 |
| 成品上限为 0 | 持续制作，直到原料或其他运行条件不足 |
| 原料保留量 | 在网络中保留指定数量，避免补货用尽原料 |
| 独立启停 | 单独控制每个样板位置 |

例如，将木板目标设为 4096、原木保留量设为 256，设备会补充木板，同时为网络留下原木。

## 等待与停止

原料不足、缺电、断网或产物无法写入 ME 时自动等待，条件恢复后继续。停用位置或取出样板会停止新合成，已制作的产物仍会返回网络。

原料与产物较多时，可用设置页的箭头翻页。本设备执行合成、锻造与切石；外部机器加工需要样板供应器和处理样板。

[构序阵列](sequence_array_controller.md)的**自动合成**页提供相同的九槽补货功能，设置方法一致。

## 配方

<RecipeFor id="molecularmanipulator:molecular_auto_crafter" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
