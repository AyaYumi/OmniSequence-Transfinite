---
navigation:
  parent: omnisequence-index.md
  title: "微型黑洞与微型白洞"
  icon: molecularmanipulator:black_hole
  position: 1110
item_ids:
- molecularmanipulator:black_hole
- molecularmanipulator:white_hole
- molecularmanipulator:miniature_supernova
---

# 微型黑洞与微型白洞

微型黑洞将附近掉落物和非玩家实体送到同维度、同队最近的微型白洞。两者可用于物品或生物传送，也参与天枢部件的制作。

<Row>
<ItemImage id="molecularmanipulator:black_hole" scale="6" />

<ItemImage id="molecularmanipulator:white_hole" scale="6" />

<ItemImage id="molecularmanipulator:miniature_supernova" scale="6" />
</Row>

## 制作

完成**三阶：视界奇点**研究后，用构筑井[样板总成](matter_fabrication_pattern_assembly.md)加工。首次解锁时，以下配方均耗时 **30 秒**，功率 **4096 AE/t**。

| 产物 | 材料 |
| --- | --- |
| 微型黑洞 ×1 | AE2 奇点 ×10,000 |
| 微型白洞 ×1 | 微型黑洞 ×10 |
| 微型超新星 ×1 | 微型黑洞 ×100、微型白洞 ×100 |

天枢主控与白洞资源核心各需要一枚微型超新星。

## 建立传送通道

1. 先放置**微型白洞**，在其上方留出出口空间。
2. 同一 FTB 队伍的成员在同维度放置**微型黑洞**。
3. 附近掉落物和非玩家实体会被拉向黑洞，接近中心后传送到最近白洞上方。

玩家不会被吸取或传送。没有匹配白洞时，黑洞不开始吸取。多个黑洞会各自选择最近出口，白洞数量没有放置上限。

## 队伍与区块

放置时绑定放置者的 FTB 队伍；未安装 FTB Teams 时绑定个人。换队后，重新放置黑白洞来更新归属。

白洞会保持所在区块加载，拆除后释放。黑洞需要所在区块已加载才会工作。传送保留实体和物品本身。

## 在天枢中使用

微型黑洞也可作为物品放入天枢的专用槽，最多 **64 个**，用于增加奇点序质产量。详见[物质复制](singularity_matter_duplication.md)。

## 配方

<RecipeFor id="molecularmanipulator:black_hole" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

<RecipeFor id="molecularmanipulator:white_hole" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

<RecipeFor id="molecularmanipulator:miniature_supernova" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
